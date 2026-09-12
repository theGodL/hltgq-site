package com.qgyun.hltgq.hltgqsite.workorder.service;

import com.qgyun.hltgq.hltgqsite.workorder.mapper.WorkOrderMapper;
import com.qgyun.hltgq.hltgqsite.workorder.vo.WorkOrderListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工单管理查询服务：类型/状态编码 → 中文（口径见《数据表结构汇总.md》第 7 节）。
 * <p>处理时间过滤按 pyxcen 半开区间 [startDate 00:00, endDate+1 00:00)；
 * 状态过滤支持中文与编码双写法。
 */
@Service
public class WorkOrderService {

    /** 工单类型编码 → 名称（qjulvf，9 档） */
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    /** 工单状态编码 → 名称（status，5 档，含 #iizl# 已逾期） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();

    static {
        TYPE_LABELS.put("#1#", "日常巡检工单");
        TYPE_LABELS.put("#yjje#", "设备维护工单");
        TYPE_LABELS.put("#hxqm#", "设备故障抢修工单");
        TYPE_LABELS.put("#zopb#", "水工建筑物维护工单");
        TYPE_LABELS.put("#kjua#", "渠道及附属设施养护工单");
        TYPE_LABELS.put("#enak#", "计量与监测维护工单");
        TYPE_LABELS.put("#bgtg#", "信息化与通信维护工单");
        TYPE_LABELS.put("#pfqj#", "安全隐患整改工单");
        TYPE_LABELS.put("#uvxb#", "应急处置工单");

        STATUS_LABELS.put("#1#", "待处理");
        STATUS_LABELS.put("#2#", "处理中");
        STATUS_LABELS.put("#3#", "已关闭");
        STATUS_LABELS.put("#4#", "已取消");
        STATUS_LABELS.put("#iizl#", "已逾期");
    }

    @Autowired
    private WorkOrderMapper mapper;

    /**
     * 工单列表。
     *
     * @param site      所属站点：站点 id / 编号 / 名称，可选
     * @param startDate 处理时间起（yyyy-MM-dd，含当日），可选
     * @param endDate   处理时间止（yyyy-MM-dd，含当日），可选
     * @param status    状态：待处理/处理中/已关闭/已取消/已逾期 或编码，可选
     */
    public List<WorkOrderListVO> list(String site, LocalDate startDate, LocalDate endDate, String status) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<WorkOrderListVO> rows = mapper.selectWorkOrderList(trimToNull(site), startTime, endTime,
                resolveCode(STATUS_LABELS, status));
        for (WorkOrderListVO row : rows) {
            row.setType(TYPE_LABELS.getOrDefault(row.getTypeCode(), row.getTypeCode()));
            row.setStatus(STATUS_LABELS.getOrDefault(row.getStatusCode(), row.getStatusCode()));
        }
        return rows;
    }

    /** 过滤值归一：空 → null；编码（#...#）原样；中文 → 编码；未知原样下传 */
    private String resolveCode(Map<String, String> labels, String value) {
        String v = trimToNull(value);
        if (v == null || v.startsWith("#")) {
            return v;
        }
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (e.getValue().equals(v)) {
                return e.getKey();
            }
        }
        return v;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
