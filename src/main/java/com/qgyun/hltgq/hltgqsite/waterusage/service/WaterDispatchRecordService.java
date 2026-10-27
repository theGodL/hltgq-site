package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.qgyun.hltgq.hltgqsite.model.util.ShortIdGenerator;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageDict;
import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDispatchMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterDispatchRecordMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.DispatchOptionVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordSaveVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchRecordVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterDispatchVO;
import com.qgyun.hltgq.hltgqsite.waterusage.vo.WaterUserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度记录服务（列表/详情/新增/编辑/删除）。
 * <p>闸口操作留痕，不参与水量与费用计算；记录为留痕数据宽进管理（编辑/删除不限状态），
 * 仅做存在性与关联一致性校验：关联指令（dispatchId）填写时用水户与操作闸口须与指令一致。
 * <p>记录内容：用水户（必填）+ 操作闸口 + 操作时间 + 操作后闸门高度（0~999 cm，选填）+ 操作人 + 备注。
 */
@Service
public class WaterDispatchRecordService {

    private final WaterDispatchRecordMapper recordMapper;

    private final WaterDispatchMapper dispatchMapper;

    private final WaterUserMapper userMapper;

    private final ShortIdGenerator shortIdGenerator;

    private final PlatformUserService platformUsers;

    /** 操作人兜底（会话缺失时使用，审计列 NOT NULL） */
    @Value("${hltgq.created-by}")
    private String fallbackOperator;

    public WaterDispatchRecordService(WaterDispatchRecordMapper recordMapper,
                                      WaterDispatchMapper dispatchMapper,
                                      WaterUserMapper userMapper,
                                      ShortIdGenerator shortIdGenerator,
                                      PlatformUserService platformUsers) {
        this.recordMapper = recordMapper;
        this.dispatchMapper = dispatchMapper;
        this.userMapper = userMapper;
        this.shortIdGenerator = shortIdGenerator;
        this.platformUsers = platformUsers;
    }

    // ==================== 元数据 / 列表 / 详情 ====================

    /** 表单元数据（操作人候选（平台用户）+ 当天日期） */
    public Map<String, Object> meta() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("operators", platformUsers.options());
        out.put("today", LocalDate.now().toString());
        return out;
    }

    /** 列表分页（关键字匹配操作人/备注/指令编号） */
    public Map<String, Object> page(int page, int size, String keyword, String waterUserId, String gateId) {
        int p = WaterUsageSupport.clampPage(page);
        int s = WaterUsageSupport.clampSize(size);
        String kw = WaterUsageSupport.trim(keyword);
        String uid = WaterUsageSupport.trim(waterUserId);
        String gid = WaterUsageSupport.trim(gateId);
        long total = recordMapper.countRecords(kw, uid, gid);
        List<WaterDispatchRecordVO> records = recordMapper.selectRecordPage(kw, uid, gid, s, (long) (p - 1) * s);
        return WaterUsageSupport.pageResult(total, p, s, records);
    }

    /** 详情 */
    public WaterDispatchRecordVO detail(String id) {
        String key = WaterUsageSupport.requireText(id, "记录 id 不能为空");
        WaterDispatchRecordVO vo = recordMapper.selectRecordById(key);
        if (vo == null) {
            throw new IllegalArgumentException("调度记录不存在");
        }
        return vo;
    }

    /** 「关联调度指令」下拉（未取消全部，编号倒序） */
    public List<DispatchOptionVO> planOptions(String keyword) {
        List<DispatchOptionVO> rows = dispatchMapper.selectActivePlanOptions(WaterUsageSupport.trim(keyword));
        for (DispatchOptionVO row : rows) {
            row.setStatus(WaterUsageDict.dispatchStatusCodeOf(row.getStatus()));
        }
        return rows;
    }

    // ==================== 新增 / 编辑 / 删除 ====================

    /** 新增（关联指令选填；填写时用水户/闸口须与指令一致） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchRecordVO create(WaterDispatchRecordSaveVO req) {
        Draft d = validate(req);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        String id = shortIdGenerator.nextUUID(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("dispatch_id", d.dispatchId);
        row.put("water_user_id", d.userId);
        row.put("gate_id", d.gateId);
        row.put("op_time", d.opTime);
        row.put("gate_height_after", d.gateHeightAfter);
        row.put("operator", d.operator);
        row.put("remark", d.remark);
        row.put("now", now);
        row.put("auditor", operator);
        recordMapper.insertRecord(row);
        return detail(id);
    }

    /** 编辑（留痕数据宽进：业务字段可改，关联一致性同新增） */
    @Transactional(rollbackFor = Exception.class)
    public WaterDispatchRecordVO update(String id, WaterDispatchRecordSaveVO req) {
        String key = WaterUsageSupport.requireText(id, "记录 id 不能为空");
        WaterDispatchRecordVO current = recordMapper.selectRecordById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度记录不存在");
        }
        Draft d = validate(req);
        LocalDateTime now = LocalDateTime.now();
        String operator = WaterUsageSupport.currentOperatorId(fallbackOperator);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key);
        row.put("dispatch_id", d.dispatchId);
        row.put("water_user_id", d.userId);
        row.put("gate_id", d.gateId);
        row.put("op_time", d.opTime);
        row.put("gate_height_after", d.gateHeightAfter);
        row.put("operator", d.operator);
        row.put("remark", d.remark);
        row.put("now", now);
        row.put("auditor", operator);
        if (recordMapper.updateRecord(row) == 0) {
            throw new IllegalArgumentException("调度记录不存在，请刷新后重试");
        }
        return detail(key);
    }

    /** 删除（留痕数据宽进：不限状态） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        String key = WaterUsageSupport.requireText(id, "记录 id 不能为空");
        WaterDispatchRecordVO current = recordMapper.selectRecordById(key);
        if (current == null) {
            throw new IllegalArgumentException("调度记录不存在");
        }
        recordMapper.deleteRecord(key);
    }

    // ==================== 内部 ====================

    /** 校验产物：已归一化的入库值（LocalDateTime / trim 文本） */
    private static final class Draft {
        String dispatchId;
        String userId;
        String gateId;
        LocalDateTime opTime;
        Integer gateHeightAfter;
        String operator;
        String remark;
    }

    /** 新增/编辑共用校验：户/闸口存在、指令关联一致性、时间/闸门高度/文本 */
    private Draft validate(WaterDispatchRecordSaveVO req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Draft d = new Draft();
        d.userId = WaterUsageSupport.requireText(req.getWaterUserId(), "请选择用水户");
        WaterUserVO user = userMapper.selectUserById(d.userId);
        if (user == null) {
            throw new IllegalArgumentException("用水户不存在");
        }
        d.gateId = WaterUsageSupport.requireText(req.getGateId(), "请选择操作闸口");
        if (userMapper.selectGateNamesByIds(Collections.singletonList(d.gateId)).isEmpty()) {
            throw new IllegalArgumentException("操作闸口不存在或已停用");
        }
        d.dispatchId = WaterUsageSupport.trim(req.getDispatchId());
        if (d.dispatchId != null) {
            WaterDispatchVO dispatch = dispatchMapper.selectDispatchById(d.dispatchId);
            if (dispatch == null) {
                throw new IllegalArgumentException("关联的调度指令不存在");
            }
            if (!d.userId.equals(dispatch.getWaterUserId())) {
                throw new IllegalArgumentException("关联指令的用水户与所选用水户不一致");
            }
            if (!d.gateId.equals(dispatch.getGateId())) {
                throw new IllegalArgumentException("关联指令的操作闸口与所选闸口不一致");
            }
        }
        d.opTime = WaterUsageSupport.parseDateTime(req.getOpTime(), "操作时间");
        Integer gateHeightAfter = req.getGateHeightAfter();
        if (gateHeightAfter != null && (gateHeightAfter < 0 || gateHeightAfter > 999)) {
            throw new IllegalArgumentException("操作后闸门高度需在 0~999 cm 之间");
        }
        d.gateHeightAfter = gateHeightAfter;
        // 操作人必填：传 operatorUserId 时按平台用户回查固化姓名（不信任前端文本）；
        // 仅回传姓名（未换人/存量手填的闸管员）时按文本入库，不阻塞留痕
        d.operator = platformUsers.resolveName(
                req.getOperatorUserId(), req.getOperator(), "操作人 / 闸管员", true);
        d.remark = WaterUsageSupport.maxLength(WaterUsageSupport.trim(req.getRemark()), 200, "备注");
        return d;
    }
}
