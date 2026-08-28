package com.qgyun.hltgq.hltgqsite.archive.service;

import com.qgyun.hltgq.hltgqsite.archive.client.ArchiveClient;
import com.qgyun.hltgq.hltgqsite.mapper.ArchiveSyncMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 档案系统同步服务：企效 UC 表 → 浩微档案系统。
 * <p>同步顺序固定：先组织后用户（用户依赖部门编码）；
 * 组织行按 dpLv 升序推送（父部门先于子部门，parentDeptId 引用依赖此顺序）。
 * <p>同步策略：全量（手动 full / 首次）与增量（定时，updated_at >= lastSyncTime）。
 * <p>无部门用户跳过并记录 ERROR 日志；无岗位用户 employeeName 兜底 "员工"。
 * <p>不落同步日志表，失败仅 ERROR 进程日志；下次触发由 Upsert 幂等自然重发。
 */
@Service
public class ArchiveSyncService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveSyncService.class);

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 根节点标记：parent_id='*' 时为顶级部门 */
    private static final String ROOT_PARENT = "*";

    /** 无岗位用户的默认岗位名称（需浩微「职位管理」预置） */
    private static final String DEFAULT_POSITION = "员工";

    private final ArchiveClient client;
    private final ArchiveSyncMapper mapper;

    /** 上次同步基线（内存态，重启后为空 → 下次按全量处理），组织/用户共用 */
    private volatile LocalDateTime lastSyncTime;

    public ArchiveSyncService(ArchiveClient client, ArchiveSyncMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    /** 定时增量同步：每天凌晨 2:00 */
    @Scheduled(cron = "${archive.sync-cron:0 0 2 * * ?}")
    public void scheduledSync() {
        try {
            sync(false);
        } catch (Exception e) {
            log.error("archive scheduled sync failed", e);
        }
    }

    /** 手动触发：full=全量（并重置基线），否则增量 */
    public void manualSync(boolean full) {
        if (full) {
            lastSyncTime = null;
        }
        sync(full);
    }

    /** 同步主流程：先组织后用户；组织失败即终止，用户失败仅记日志（Upsert 幂等，下次自然重发） */
    private synchronized void sync(boolean full) {
        LocalDateTime since = full ? null : lastSyncTime;
        String sinceStr = since == null ? null : since.format(TS_FMT);
        LocalDateTime syncStart = LocalDateTime.now();

        // 1. 组织同步
        List<Map<String, Object>> orgRows = buildDeptRows(full ? mapper.selectOrgsAll() : mapper.selectOrgsSince(sinceStr));
        if (!orgRows.isEmpty()) {
            client.syncDepts(client.getToken(), orgRows);
        } else {
            log.info("archive dept sync skipped: no rows to sync");
        }

        // 2. 用户同步（失败不中断，记 ERROR 日志，下次定时任务重发）
        try {
            List<Map<String, Object>> userRows = buildUserRows(full ? mapper.selectUsersAll() : mapper.selectUsersSince(sinceStr));
            if (!userRows.isEmpty()) {
                client.syncUsers(client.getToken(), userRows);
            } else {
                log.info("archive user sync skipped: no rows to sync");
            }
        } catch (Exception e) {
            log.error("archive user sync failed, will retry on next schedule", e);
        }

        // 3. 推进基线：取同步开始时刻，避免同步期间新变更被下个周期漏掉（边界重复由 Upsert 幂等兜底）
        lastSyncTime = syncStart;
        log.info("archive sync finished, mode={}, deptRows={}, baseline={}",
                full ? "full" : "incremental", orgRows.size(), syncStart.format(TS_FMT));
    }

    /** 组织行构建：计算 dpLv/fullName，增量模式下补父链，按 dpLv 升序 */
    private List<Map<String, Object>> buildDeptRows(List<Map<String, Object>> orgs) {
        if (orgs.isEmpty()) {
            return new ArrayList<>();
        }
        // 全量加载所有组织用于父链解析（增量模式下需补全父链，否则新子部门挂不上）
        Map<String, Map<String, Object>> orgMap = new HashMap<>();
        for (Map<String, Object> org : mapper.selectOrgsAll()) {
            orgMap.put(String.valueOf(org.get("id")), org);
        }
        // 收集需要推送的组织（含父链补齐）
        Map<String, Map<String, Object>> toSync = new LinkedHashMap<>();
        for (Map<String, Object> org : orgs) {
            collectWithAncestors(org, orgMap, toSync, new HashSet<>());
        }
        // 计算层级与完整路径（带环路防御）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> org : toSync.values()) {
            rows.add(buildDeptRow(org, orgMap));
        }
        rows.sort(Comparator.comparingInt(r -> (Integer) r.get("dpLv")));
        return rows;
    }

    /** 沿父链向上收集，环路防御（visited 命中即停） */
    private void collectWithAncestors(Map<String, Object> org,
                                      Map<String, Map<String, Object>> orgMap,
                                      Map<String, Map<String, Object>> toSync,
                                      Set<String> visited) {
        String id = String.valueOf(org.get("id"));
        if (!visited.add(id) || toSync.containsKey(id)) {
            return;
        }
        toSync.put(id, org);
        String parentId = str(org.get("parent_id"));
        if (parentId != null && !ROOT_PARENT.equals(parentId) && !parentId.equals(id)) {
            Map<String, Object> parent = orgMap.get(parentId);
            if (parent != null) {
                collectWithAncestors(parent, orgMap, toSync, visited);
            }
        }
    }

    /** 单条组织行：parentDeptId 引用父部门编码（根传 null），dpLv 从根递归累加 */
    private Map<String, Object> buildDeptRow(Map<String, Object> org, Map<String, Map<String, Object>> orgMap) {
        Map<String, Object> row = new LinkedHashMap<>();
        String code = str(org.get("code"));
        String name = str(org.get("name"));
        String parentId = str(org.get("parent_id"));

        String parentCode = null;
        if (parentId != null && !ROOT_PARENT.equals(parentId) && !parentId.equals(String.valueOf(org.get("id")))) {
            Map<String, Object> parent = orgMap.get(parentId);
            if (parent != null) {
                parentCode = str(parent.get("code"));
            }
        }

        row.put("parentDeptId", parentCode);
        row.put("dpCode", code);
        row.put("dpLv", calcLevel(org, orgMap, new HashSet<>()));
        row.put("deptName", name);
        row.put("fullName", calcFullName(org, orgMap, new HashSet<>()));
        row.put("describes", "");
        row.put("deptStatus", "0");
        row.put("dpType", "0");
        row.put("compositor", 1);
        row.put("departmentCode", code);
        row.put("personCharge", null);
        row.put("deputyPersonCharge", null);
        return row;
    }

    /** dpLv：根=1，逐层+1；父缺失/环路防御兜底 1 */
    private int calcLevel(Map<String, Object> org, Map<String, Map<String, Object>> orgMap, Set<String> visited) {
        String id = String.valueOf(org.get("id"));
        if (!visited.add(id)) {
            return 1;
        }
        String parentId = str(org.get("parent_id"));
        if (parentId == null || ROOT_PARENT.equals(parentId) || parentId.equals(id)) {
            return 1;
        }
        Map<String, Object> parent = orgMap.get(parentId);
        if (parent == null) {
            return 1;
        }
        return calcLevel(parent, orgMap, visited) + 1;
    }

    /** fullName：根=name，子=父路径_name；父缺失/环路防御兜底本名 */
    private String calcFullName(Map<String, Object> org, Map<String, Map<String, Object>> orgMap, Set<String> visited) {
        String id = String.valueOf(org.get("id"));
        String name = str(org.get("name"));
        if (name == null) {
            name = "";
        }
        if (!visited.add(id)) {
            return name;
        }
        String parentId = str(org.get("parent_id"));
        if (parentId == null || ROOT_PARENT.equals(parentId) || parentId.equals(id)) {
            return name;
        }
        Map<String, Object> parent = orgMap.get(parentId);
        if (parent == null) {
            return name;
        }
        return calcFullName(parent, orgMap, visited) + "_" + name;
    }

    /** 用户行构建：字段映射 + 无部门跳过 + 无岗位兜底 */
    private List<Map<String, Object>> buildUserRows(List<Map<String, Object>> users) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int skipped = 0;
        for (Map<String, Object> user : users) {
            String deptCode = str(user.get("dept_code"));
            if (deptCode == null || deptCode.isEmpty()) {
                skipped++;
                log.error("archive user skipped: no main dept, loginName={}, name={}",
                        str(user.get("login_name")), str(user.get("name")));
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("loginId", str(user.get("login_name")));
            row.put("userName", str(user.get("name")));
            row.put("telephone", null);
            row.put("mobile", str(user.get("mobile")));
            row.put("sex", mapSex(str(user.get("sex"))));
            row.put("cardId", str(user.get("id_card")));
            String position = str(user.get("position_name"));
            row.put("employeeName", position == null || position.isEmpty() ? DEFAULT_POSITION : position);
            row.put("email", str(user.get("email")));
            row.put("departmentCode", deptCode);
            row.put("birthDay", str(user.get("birthday")));
            rows.add(row);
        }
        if (skipped > 0) {
            log.error("archive user sync skipped {} users without main dept", skipped);
        }
        return rows;
    }

    /** 性别映射：M→M、F→F，其余（#SECRET#/null 等）→null */
    private String mapSex(String sex) {
        if ("M".equals(sex) || "F".equals(sex)) {
            return sex;
        }
        return null;
    }

    /** Map 取值：null 安全转 String */
    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
