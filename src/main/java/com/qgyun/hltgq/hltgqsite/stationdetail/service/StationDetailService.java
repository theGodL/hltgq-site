package com.qgyun.hltgq.hltgqsite.stationdetail.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsite.stationdetail.client.FileClient;
import com.qgyun.hltgq.hltgqsite.stationdetail.mapper.StationDetailMapper;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.DeviceVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.IssueRecordVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.PatrolDetailVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.PatrolRecordVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.StationBasicVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.StationOptionsVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.WorkOrderVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * 站点详情服务：站点档案聚合 + 巡检/问题/工单/设备分页 + 筛选下拉选项。
 * <p>编码 → 权威名称翻译集中在本类（口径与 H5/外部接口一致）；
 * 无数据源的档案字段恒 null，不补造数据（展示口径归前端）；
 * 巡检记录 device 多选格式联调确认项：解析结果打印日志，从服务器日志核对后调整。
 */
@Service
public class StationDetailService {

    private static final Logger log = LoggerFactory.getLogger(StationDetailService.class);

    /** 巡检结果编码 → 权威名称（6 档，与 H5 统计口径一致） */
    private static final Map<String, String> RESULT_LABELS = new LinkedHashMap<>();
    /** 巡检记录状态：#1# 草稿、#2# 已提交 */
    private static final Map<String, String> PATROL_STATUS = new LinkedHashMap<>();
    /** 问题处理方式：#1# 待确认、#2# 转工单、#3# 直接处理 */
    private static final Map<String, String> HANDLE_LABELS = new LinkedHashMap<>();
    /** 问题状态：#1# 待处理、#2# 处理中、#3# 已转工单、#4# 已关闭、#5# 已作废 */
    private static final Map<String, String> ISSUE_STATUS = new LinkedHashMap<>();
    /** 站点/设备监测类型编码 → 名称（epjutj / device.type 共用） */
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    /** 在线状态：#1# 在线、#2# 离线（站点 zebpsu / 设备 status 共用） */
    private static final Map<String, String> ON_OFF_LABELS = new LinkedHashMap<>();

    static {
        RESULT_LABELS.put("#1#", "待填写");
        RESULT_LABELS.put("#2#", "正常");
        RESULT_LABELS.put("#3#", "异常");
        RESULT_LABELS.put("#4#", "隐患");
        RESULT_LABELS.put("#5#", "缺陷");
        RESULT_LABELS.put("#6#", "故障");

        PATROL_STATUS.put("#1#", "草稿");
        PATROL_STATUS.put("#2#", "已提交");

        HANDLE_LABELS.put("#1#", "待确认");
        HANDLE_LABELS.put("#2#", "转工单");
        HANDLE_LABELS.put("#3#", "直接处理");

        ISSUE_STATUS.put("#1#", "待处理");
        ISSUE_STATUS.put("#2#", "处理中");
        ISSUE_STATUS.put("#3#", "已转工单");
        ISSUE_STATUS.put("#4#", "已关闭");
        ISSUE_STATUS.put("#5#", "已作废");

        TYPE_LABELS.put("#1#", "水位");
        TYPE_LABELS.put("#2#", "雨量");
        TYPE_LABELS.put("#3#", "流量");
        TYPE_LABELS.put("#4#", "闸门");
        TYPE_LABELS.put("#5#", "视频");
        TYPE_LABELS.put("#7#", "墒情");
        TYPE_LABELS.put("#8#", "水质");

        ON_OFF_LABELS.put("#1#", "在线");
        ON_OFF_LABELS.put("#2#", "离线");
    }

    @Autowired
    private StationDetailMapper mapper;

    @Autowired
    private FileClient fileClient;

    @Autowired
    private SessionContextService sessionContextService;

    // ==================== 基础信息 ====================

    /**
     * 基础信息聚合：档案字段 + 闸口数量 + 供电（电压表最新）+ 视频通道。
     * <p>无数据源项（电流/信号/通信延迟/所属单位/位置等）恒 null，不补造数据。
     *
     * @param stationKey 站点键（档案 id 或站点编号 iofhpi 双键兼容）
     */
    public StationBasicVO basic(String stationKey) {
        StationKey key = resolveStation(stationKey);
        String siteId = key.getId();

        StationBasicVO vo = mapper.selectStationBasic(siteId);
        if (vo == null) {
            throw new IllegalArgumentException("站点档案不存在：id=" + siteId);
        }

        // 档案字段翻译
        vo.setType(translateTypes(vo.getTypeCodes()));
        vo.setRunStatus(ON_OFF_LABELS.getOrDefault(vo.getRunStatusCode(), vo.getRunStatusCode()));
        vo.setRunStatusOk("#1#".equals(vo.getRunStatusCode()));
        vo.setNetStatus(vo.getRunStatus());
        vo.setNetOk(vo.getRunStatusOk());

        // 供电：waljdn 是否接通市电（#1# 已接通 / #2# 未接通，口径同现有页面）；bhsqxd 传输方法为文本直接展示
        vo.setPowerStatus(translatePowerStatus(vo.getMainsPowerCode()));
        vo.setPowerOk("#1#".equals(vo.getMainsPowerCode()));

        // 经纬度文本
        if (vo.getLon() != null && vo.getLat() != null) {
            vo.setLnglat(vo.getLon().toPlainString() + ", " + vo.getLat().toPlainString());
        }

        // 闸口数量
        vo.setGates((int) mapper.countGateDevices(siteId));

        // 供电：电压表最新 vol 电压 + tm 最近通信时间（JDBC 返回 Timestamp，需转换；电流/信号强度列库中未确认，恒 null）
        Map<String, Object> vol = mapper.selectLatestVol(siteId);
        if (vol != null) {
            vo.setVolt(dec(vol.get("vol")));
            vo.setCommTime(toLocalDateTime(vol.get("tm")));
        }

        // 视频通道
        List<StationBasicVO.VideoChannel> videos = mapper.selectVideoChannels(siteId);
        for (StationBasicVO.VideoChannel v : videos) {
            v.setStatus(ON_OFF_LABELS.getOrDefault(v.getStatusCode(), v.getStatusCode()));
        }
        vo.setVideos(videos);

        return vo;
    }

    // ==================== 巡检记录 ====================

    /**
     * 巡检记录分页（按站点，全状态含草稿）。
     *
     * @param stationKey 站点键（档案 id 或站点编号 iofhpi 双键兼容）
     * @param date       巡检日期 yyyy-MM-dd，可选（命中当日 [00:00, 次日 00:00)）
     * @param person     巡检人员姓名模糊，可选
     * @param result     巡检结果：正常/异常，可选；异常含隐患/缺陷/故障档
     * @param hasIssue   是否发现问题：1/0，可选
     */
    public Page<PatrolRecordVO> patrolPage(String stationKey, LocalDate date, String person,
                                           String result, String hasIssue, long page, long size) {
        String siteId = resolveStation(stationKey).getId();
        checkPage(page, size);

        LocalDateTime startTime = date == null ? null : date.atStartOfDay();
        LocalDateTime endTime = date == null ? null : date.plusDays(1).atStartOfDay();
        Boolean abnormal = parseAbnormal(result);
        Boolean hasIssueFilter = parseHasIssue(hasIssue);

        long total = mapper.countPatrol(siteId, startTime, endTime, person, abnormal, hasIssueFilter);
        Page<PatrolRecordVO> resultPage = new Page<>(page, size);
        resultPage.setTotal(total);
        if (total == 0) {
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        int offset = (int) ((page - 1) * size);
        List<PatrolRecordVO> records = mapper.selectPatrolPage(
                siteId, startTime, endTime, person, abnormal, hasIssueFilter, (int) size, offset);

        // 编码翻译 + 巡检对象设备名解析回填
        for (PatrolRecordVO r : records) {
            r.setResult(RESULT_LABELS.getOrDefault(r.getResultCode(), r.getResultCode()));
            r.setStatus(PATROL_STATUS.getOrDefault(r.getStatusCode(), r.getStatusCode()));
        }
        fillPatrolObjects(records);

        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 巡检记录详情（「查看」弹窗）。
     *
     * @param recordId 巡检记录主键 id
     * @param request  当前请求（提取会话 id 透传文件服务换取照片签名地址）
     */
    public PatrolDetailVO patrolDetail(String recordId, HttpServletRequest request) {
        if (recordId == null || recordId.trim().isEmpty()) {
            throw new IllegalArgumentException("巡检记录 id 不能为空");
        }
        PatrolDetailVO vo = mapper.selectPatrolDetail(recordId.trim());
        if (vo == null) {
            throw new IllegalArgumentException("巡检记录不存在：id=" + recordId);
        }

        vo.setResult(RESULT_LABELS.getOrDefault(vo.getResultCode(), vo.getResultCode()));
        vo.setStatus(PATROL_STATUS.getOrDefault(vo.getStatusCode(), vo.getStatusCode()));
        vo.setObject(resolveDeviceNames(vo.getDeviceIds(), " / "));
        vo.setPhotos(loadPatrolPhotos(recordId.trim(), request));
        vo.setIssues(mapper.selectPatrolIssues(recordId.trim()));
        if (vo.getIssues() == null) {
            vo.setIssues(Collections.emptyList());
        }
        return vo;
    }

    /**
     * 巡检现场照片组装：图片关联表 biz_id = 记录 id 取文件 id 列表，逐文件经文件服务换签名地址
     * （preview 原图 + thumb 缩略图）。单文件失败仅 warn 跳过（不阻断主数据），全部失败返回空列表。
     */
    private List<PatrolDetailVO.PhotoItem> loadPatrolPhotos(String recordId, HttpServletRequest request) {
        List<String> fileIds = mapper.selectPatrolImageFileIds(recordId);
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sessionId = sessionContextService.extractSessionId(request);
        List<PatrolDetailVO.PhotoItem> photos = new ArrayList<>();
        for (String fileId : fileIds) {
            if (fileId == null || fileId.isEmpty()) {
                continue;
            }
            try {
                FileClient.FileInfo info = fileClient.getFile(fileId, sessionId);
                PatrolDetailVO.PhotoItem item = new PatrolDetailVO.PhotoItem();
                item.setFileId(info.fileId);
                item.setName(info.name);
                item.setUrl(info.url);
                item.setThumb(info.thumb);
                photos.add(item);
            } catch (Exception e) {
                log.warn("巡检照片获取失败 recordId={}, fileId={}: {}", recordId, fileId, e.getMessage());
            }
        }
        log.info("巡检照片组装完成 recordId={}, 文件数={}/{}", recordId, photos.size(), fileIds.size());
        return photos;
    }

    // ==================== 问题记录 ====================

    /**
     * 问题记录分页（按站点，全状态）。
     *
     * @param handle 处理方式：转工单/待确认/直接处理，可选
     * @param status 状态：已转工单/待处理/已关闭，可选
     */
    public Page<IssueRecordVO> issuePage(String stationKey, LocalDate date, String finder,
                                         String handle, String status, long page, long size) {
        String siteId = resolveStation(stationKey).getId();
        checkPage(page, size);

        LocalDateTime startTime = date == null ? null : date.atStartOfDay();
        LocalDateTime endTime = date == null ? null : date.plusDays(1).atStartOfDay();
        String handleCode = parseHandle(handle);
        String statusCode = parseIssueStatus(status);

        long total = mapper.countIssue(siteId, startTime, endTime, finder, handleCode, statusCode);
        Page<IssueRecordVO> resultPage = new Page<>(page, size);
        resultPage.setTotal(total);
        if (total == 0) {
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        int offset = (int) ((page - 1) * size);
        List<IssueRecordVO> records = mapper.selectIssuePage(
                siteId, startTime, endTime, finder, handleCode, statusCode, (int) size, offset);
        for (IssueRecordVO r : records) {
            r.setHandle(HANDLE_LABELS.getOrDefault(r.getHandleCode(), r.getHandleCode()));
            r.setStatus(ISSUE_STATUS.getOrDefault(r.getStatusCode(), r.getStatusCode()));
        }
        resultPage.setRecords(records);
        return resultPage;
    }

    // ==================== 维修工单 ====================

    /** 维修工单分页（按站点，全状态，页面无筛选） */
    public Page<WorkOrderVO> orderPage(String stationKey, long page, long size) {
        String siteId = resolveStation(stationKey).getId();
        checkPage(page, size);

        long total = mapper.countOrder(siteId);
        Page<WorkOrderVO> resultPage = new Page<>(page, size);
        resultPage.setTotal(total);
        if (total == 0) {
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        int offset = (int) ((page - 1) * size);
        resultPage.setRecords(mapper.selectOrderPage(siteId, (int) size, offset));
        return resultPage;
    }

    // ==================== 设备信息 ====================

    /**
     * 设备分页（按站点）+ 实时数据（按监测类型取对应监测表最新值）+ 所属闸口解析。
     *
     * @param code   设备编号模糊，可选
     * @param name   设备名称精确（下拉选择），可选
     * @param type   设备类型：中文名称 水位/雨量/流量/闸门/视频/墒情/水质 或数字 1/2/3/4/5/7/8（等价），可选
     * @param status 设备状态：在线/离线（兼容旧词正常/关闭），可选
     */
    public Page<DeviceVO> devicePage(String stationKey, String code, String name,
                                     String type, String status, long page, long size) {
        StationKey key = resolveStation(stationKey);
        String siteId = key.getId();
        checkPage(page, size);

        String typeFilter = parseDeviceType(type);
        String statusFilter = parseDeviceStatus(status);

        long total = mapper.countDevice(siteId, code, name, typeFilter, statusFilter);
        Page<DeviceVO> resultPage = new Page<>(page, size);
        resultPage.setTotal(total);
        if (total == 0) {
            resultPage.setRecords(Collections.emptyList());
            return resultPage;
        }

        int offset = (int) ((page - 1) * size);
        List<DeviceVO> records = mapper.selectDevicePage(
                siteId, code, name, typeFilter, statusFilter, (int) size, offset);

        StationBasicVO archive = mapper.selectStationBasic(siteId);
        String siteName = archive == null ? null : archive.getName();
        for (DeviceVO d : records) {
            d.setType(translateTypes(d.getTypeCodes()));
            d.setStatus(ON_OFF_LABELS.getOrDefault(d.getStatusCode(), d.getStatusCode()));
            d.setGate(parseGateNo(d.getName(), siteName));
        }
        fillDeviceRealtime(siteId, key.getIofhpi(), records);

        resultPage.setRecords(records);
        return resultPage;
    }

    // ==================== 筛选下拉选项 ====================

    /** 筛选下拉选项：巡检人员/发现人/设备名称（一次请求返回） */
    public StationOptionsVO options(String stationKey) {
        String siteId = resolveStation(stationKey).getId();
        StationOptionsVO vo = new StationOptionsVO();
        vo.setPatrolPersons(mapper.selectPatrolPersons(siteId));
        vo.setIssueFinders(mapper.selectIssueFinders(siteId));
        vo.setDeviceNames(mapper.selectDeviceNames(siteId));
        return vo;
    }

    // ==================== 私有工具 ====================

    /** 站点键解析：id 或 iofhpi 双键命中，无命中抛 400；返回档案主键 id + 站点编号 iofhpi */
    private StationKey resolveStation(String stationKey) {
        if (stationKey == null || stationKey.trim().isEmpty()) {
            throw new IllegalArgumentException("站点参数不能为空（stationId=档案 id 或站点编号）");
        }
        Map<String, Object> row = mapper.selectStationKey(stationKey.trim());
        if (row == null || row.get("id") == null) {
            throw new IllegalArgumentException("站点不存在：" + stationKey);
        }
        StationKey key = new StationKey();
        key.setId(str(row.get("id")));
        key.setIofhpi(str(row.get("iofhpi")));
        return key;
    }

    /** 站点键解析结果：档案主键 id（监测/业务表 site 值）+ 站点编号 iofhpi（水文表 STCD） */
    private static class StationKey {
        private String id;
        private String iofhpi;

        String getId() {
            return id;
        }

        void setId(String id) {
            this.id = id;
        }

        String getIofhpi() {
            return iofhpi;
        }

        void setIofhpi(String iofhpi) {
            this.iofhpi = iofhpi;
        }
    }

    /** 分页参数防御（与告警分页一致）：非法转 400，size 上限 1000 */
    private void checkPage(long page, long size) {
        if (page < 1 || size < 1 || size > 1000 || (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数非法：page/size 必须为正整数且 size 不超过 1000");
        }
    }

    /** 巡检结果筛选：正常 → false（仅 #2#），异常 → true（#3#~#6#）；其他/空 → null（不筛） */
    private Boolean parseAbnormal(String result) {
        if (result == null) return null;
        String t = result.trim();
        if ("正常".equals(t)) return Boolean.FALSE;
        if ("异常".equals(t)) return Boolean.TRUE;
        return null;
    }

    /** 是否发现问题筛选：1 → true、0 → false；其他/空 → null（不筛） */
    private Boolean parseHasIssue(String hasIssue) {
        if (hasIssue == null) return null;
        if ("1".equals(hasIssue.trim())) return Boolean.TRUE;
        if ("0".equals(hasIssue.trim())) return Boolean.FALSE;
        return null;
    }

    /** 处理方式筛选文本 → 编码（#2# 转工单、#1# 待确认、#3# 直接处理）；未知 → null（不筛） */
    private String parseHandle(String handle) {
        if (handle == null) return null;
        String t = handle.trim();
        if ("转工单".equals(t)) return "#2#";
        // 权威文案「待确认」（与响应翻译一致），兼容旧词「待确定」
        if ("待确认".equals(t) || "待确定".equals(t)) return "#1#";
        if ("直接处理".equals(t)) return "#3#";
        return null;
    }

    /** 问题状态筛选文本 → 编码（#3# 已转工单、#1# 待处理、#4# 已关闭）；未知 → null（不筛） */
    private String parseIssueStatus(String status) {
        if (status == null) return null;
        String t = status.trim();
        if ("已转工单".equals(t)) return "#3#";
        if ("待处理".equals(t)) return "#1#";
        if ("已关闭".equals(t)) return "#4#";
        return null;
    }

    /**
     * 设备类型筛选 → 编码片段（LIKE '%编码%'）：中文名称（水位/雨量/流量/闸门/视频/墒情/水质，
     * 与响应 type 翻译同一权威字典 TYPE_LABELS）或数字 1/2/3/4/5/7/8 → #N#；其他/空 → null（不筛）。
     */
    private String parseDeviceType(String type) {
        if (type == null) return null;
        String t = type.trim();
        if (t.isEmpty()) return null;
        for (Map.Entry<String, String> e : TYPE_LABELS.entrySet()) {
            if (e.getValue().equals(t)) return e.getKey();
        }
        if (t.length() == 1 && "1234578".indexOf(t.charAt(0)) >= 0) {
            return "#" + t + "#";
        }
        return null;
    }

    /** 设备状态筛选：权威文案「在线/离线」（与响应翻译一致，兼容旧词正常/关闭）；其他 → null（不筛） */
    private String parseDeviceStatus(String status) {
        if (status == null) return null;
        String t = status.trim();
        if ("在线".equals(t) || "正常".equals(t)) return "#1#";
        if ("离线".equals(t) || "关闭".equals(t)) return "#2#";
        return null;
    }

    /** 多类型编码翻译（epjutj / device.type，「|」分隔 → 「、」分隔名称；未知编码保留原文） */
    private String translateTypes(String typeCodes) {
        if (typeCodes == null || typeCodes.trim().isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String code : typeCodes.split("\\|")) {
            String c = code.trim();
            if (c.isEmpty()) continue;
            String label = TYPE_LABELS.get(c);
            if (sb.length() > 0) sb.append("、");
            sb.append(label == null ? c : label);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 巡检对象 device 多选解析：支持逗号/竖线/# 分隔（联调确认项，解析结果打印日志）。
     * 先按 id 批量查设备名，未命中的 token 按名称回查兑底，仍无则保留原文。
     */
    private String resolveDeviceNames(String raw, String joiner) {
        List<String> tokens = parseDeviceTokens(raw);
        if (tokens.isEmpty()) return null;

        Map<String, String> idToName = new HashMap<>();
        for (Map<String, Object> row : mapper.selectDeviceNamesByIds(tokens)) {
            idToName.put(str(row.get("id")), str(row.get("name")));
        }
        List<String> missed = new ArrayList<>();
        for (String t : tokens) {
            if (!idToName.containsKey(t)) missed.add(t);
        }
        Set<String> nameHit = new HashSet<>();
        if (!missed.isEmpty()) {
            nameHit.addAll(mapper.selectDeviceNamesByNames(missed));
        }

        List<String> names = new ArrayList<>();
        for (String t : tokens) {
            String name = idToName.get(t);
            if (name == null && nameHit.contains(t)) name = t;
            names.add(name == null ? t : name);
        }
        log.info("巡检对象 device 解析：raw={}, tokens={}, 命中id={}, 名称兑底={}",
                raw, tokens, idToName.size(), nameHit.size());
        return String.join(joiner, names);
    }

    /** 巡检记录列表回填巡检对象设备名（批量解析，顿号拼接） */
    private void fillPatrolObjects(List<PatrolRecordVO> records) {
        // 收集全部记录 device 原文 token 去重
        Set<String> allTokens = new LinkedHashSet<>();
        for (PatrolRecordVO r : records) {
            allTokens.addAll(parseDeviceTokens(r.getDeviceIds()));
        }
        if (allTokens.isEmpty()) return;

        List<String> tokenList = new ArrayList<>(allTokens);
        Map<String, String> idToName = new HashMap<>();
        for (Map<String, Object> row : mapper.selectDeviceNamesByIds(tokenList)) {
            idToName.put(str(row.get("id")), str(row.get("name")));
        }
        List<String> missed = new ArrayList<>();
        for (String t : tokenList) {
            if (!idToName.containsKey(t)) missed.add(t);
        }
        Set<String> nameHit = new HashSet<>();
        if (!missed.isEmpty()) {
            nameHit.addAll(mapper.selectDeviceNamesByNames(missed));
        }
        log.info("巡检对象批量解析：tokens={}, 命中id={}, 名称兑底={}", tokenList.size(), idToName.size(), nameHit.size());

        for (PatrolRecordVO r : records) {
            List<String> names = new ArrayList<>();
            for (String t : parseDeviceTokens(r.getDeviceIds())) {
                String name = idToName.get(t);
                if (name == null && nameHit.contains(t)) name = t;
                names.add(name == null ? t : name);
            }
            r.setObject(names.isEmpty() ? null : String.join("、", names));
        }
    }

    /** device 多选原文拆 token：按逗号/竖线/# 拆分，去空去重保持顺序 */
    private List<String> parseDeviceTokens(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String part : raw.split("[,|#]")) {
            String t = part.trim();
            if (t.isEmpty() || !seen.add(t)) continue;
            out.add(t);
        }
        return out;
    }

    /** 所属闸口解析：设备名形如「站点名+闸孔号#」（如 南山寺节制闸1#），取闸孔号；无则为 null */
    private String parseGateNo(String deviceName, String siteName) {
        if (deviceName == null || deviceName.trim().isEmpty()) return null;
        if (siteName == null || !deviceName.startsWith(siteName)) return null;
        String rest = deviceName.substring(siteName.length()).replace("#", "").trim();
        return rest.isEmpty() ? null : rest;
    }

    /**
     * 设备实时数据组装（站点级取数）：各监测表按站点键关联（闸门按 site+闸孔号、
     * 水位/雨量按 STCD=iofhpi、流量按 site、墒情/水质按 stcd/site 双键），无数据恒 null；
     * 仅当前页存在对应类型设备时才查询对应监测表；-999/-9991 哨兵值已在 SQL 过滤（>=0）。
     * <p>视频设备（#5#）巡检结果取 device 留痕表（同库直查）该通道最近一轮，
     * 翻译「正常/检出故障/检测异常」填 textValue（metric 固定「巡检结果」）；
     * 设备 code 为空（无法匹配通道）或无留痕时不带值。
     */
    private void fillDeviceRealtime(String siteId, String stcd, List<DeviceVO> records) {
        // 类型需求探测（仅查有对应设备的监测表）
        boolean needGate = false;
        boolean needLevel = false;
        boolean needRain = false;
        boolean needFlow = false;
        boolean needSoil = false;
        boolean needQuality = false;
        boolean needVideo = false;
        for (DeviceVO d : records) {
            String codes = d.getTypeCodes() == null ? "" : d.getTypeCodes();
            needGate |= codes.contains("#4#");
            needLevel |= codes.contains("#1#");
            needRain |= codes.contains("#2#");
            needFlow |= codes.contains("#3#");
            needSoil |= codes.contains("#7#");
            needQuality |= codes.contains("#8#");
            needVideo |= codes.contains("#5#");
        }

        // 闸门：每闸孔最新开度（闸孔号 → 开度）
        Map<String, BigDecimal> gateMap = new HashMap<>();
        if (needGate) {
            for (Map<String, Object> row : mapper.selectLatestGateOpenings(siteId)) {
                BigDecimal v = dec(row.get("value"));
                if (v != null) gateMap.put(str(row.get("gate_no")), v);
            }
        }
        // 站点级最新值（每类一条，无数据为 null）
        BigDecimal level = needLevel ? valueOf(mapper.selectLatestLevel(stcd)) : null;
        BigDecimal rain = needRain ? valueOf(mapper.selectLatestRain(stcd)) : null;
        BigDecimal flow = needFlow ? valueOf(mapper.selectLatestFlow(siteId)) : null;
        BigDecimal soil = needSoil ? valueOf(mapper.selectLatestSoil(stcd, siteId)) : null;
        BigDecimal quality = needQuality ? valueOf(mapper.selectLatestQuality(stcd, siteId)) : null;
        // 视频通道最新巡检结果（channel_code = 设备 code；code 为空/无留痕的设备不带值）
        Map<String, String> videoTextMap = new HashMap<>();
        if (needVideo) {
            List<String> videoCodes = new ArrayList<>();
            for (DeviceVO d : records) {
                String codes = d.getTypeCodes() == null ? "" : d.getTypeCodes();
                if (codes.contains("#5#") && d.getCode() != null && !d.getCode().trim().isEmpty()) {
                    videoCodes.add(d.getCode().trim());
                }
            }
            if (!videoCodes.isEmpty()) {
                for (Map<String, Object> row : mapper.selectLatestVideoPatrolResults(videoCodes)) {
                    String code = str(row.get("channel_code"));
                    String text = translateVideoResult(str(row.get("result")));
                    if (code != null && text != null) {
                        videoTextMap.put(code, text);
                    }
                }
            }
        }

        for (DeviceVO d : records) {
            String codes = d.getTypeCodes() == null ? "" : d.getTypeCodes();
            if (codes.contains("#4#")) {
                BigDecimal v = d.getGate() == null ? null : gateMap.get(d.getGate());
                if (v != null) setRealtime(d, "当前开度", v, "m");
            } else if (codes.contains("#1#") && level != null) {
                setRealtime(d, "当前水位", level, "m");
            } else if (codes.contains("#2#") && rain != null) {
                setRealtime(d, "日累计雨量", rain, "mm");
            } else if (codes.contains("#3#") && flow != null) {
                setRealtime(d, "瞬时流量", flow, "m³/s");
            } else if (codes.contains("#7#") && soil != null) {
                setRealtime(d, "10cm含水率", soil, "%");
            } else if (codes.contains("#8#") && quality != null) {
                setRealtime(d, "氨氮", quality, "mg/L");
            } else if (codes.contains("#5#")) {
                String text = d.getCode() == null ? null : videoTextMap.get(d.getCode().trim());
                if (text != null) {
                    d.setMetric("巡检结果");
                    d.setTextValue(text);
                }
            }
        }
    }

    private void setRealtime(DeviceVO d, String metric, BigDecimal value, String unit) {
        d.setMetric(metric);
        d.setValue(value);
        d.setUnit(unit);
    }

    /** 视频巡检结果翻译：ok → 正常、fail → 检出故障、error → 检测异常；未知值原样返回（不隐藏） */
    private String translateVideoResult(String result) {
        if (result == null) return null;
        if ("ok".equals(result)) return "正常";
        if ("fail".equals(result)) return "检出故障";
        if ("error".equals(result)) return "检测异常";
        return result;
    }

    /** 单行查询结果取 value 列数值（行不存在为 null，防 NPE） */
    private BigDecimal valueOf(Map<String, Object> row) {
        return row == null ? null : dec(row.get("value"));
    }

    /** 是否接通市电编码翻译：#1# 已接通 / #2# 未接通（档案表 waljdn） */
    private String translatePowerStatus(String code) {
        if (code == null) return null;
        if ("#1#".equals(code)) return "已接通市电";
        if ("#2#".equals(code)) return "未接通市电";
        return code;
    }

    /**
     * 数据库时间值转 LocalDateTime：JDBC Map 查询对 datetime 列返回 Timestamp（而非 LocalDateTime），
     * 强转会 ClassCastException；两种类型均兼容。
     */
    private static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime();
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().atStartOfDay();
        return null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal dec(Object v) {
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }
}
