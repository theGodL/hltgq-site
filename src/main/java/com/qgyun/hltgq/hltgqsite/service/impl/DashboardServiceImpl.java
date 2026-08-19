package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.mapper.DashboardMapper;
import com.qgyun.hltgq.hltgqsite.service.DashboardService;
import com.qgyun.hltgq.hltgqsite.vo.DashboardOverviewVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterAlertVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 设备状态概览服务实现
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private DashboardMapper dashboardMapper;

    @Override
    public DashboardOverviewVO overview() {
        DashboardOverviewVO vo = new DashboardOverviewVO();

        // ① 设备总数/在线数：完全按站点状态字段 zebpsu（#1# 在线、#2# 离线，由报文入库项目维护）
        Map<String, Object> deviceRow = dashboardMapper.selectDeviceCount();
        long totalDevices = toLong(deviceRow, "total_cnt");
        long onlineDevices = toLong(deviceRow, "online_cnt");
        vo.setTotalDeviceCount(totalDevices);
        vo.setOnlineDeviceCount(onlineDevices);
        vo.setOnlineDevicePercent(percent(onlineDevices, totalDevices));

        // ② 闸门总数/开启数：各闸孔最新开度 > 0 判定开启（排除站级行与无信号/异常闸孔，近 24h 窗口）
        Map<String, Object> gateRow = dashboardMapper.selectGateCount();
        long totalGates = toLong(gateRow, "total_cnt");
        long openGates = toLong(gateRow, "open_cnt");
        vo.setTotalGateCount(totalGates);
        vo.setOpenGateCount(openGates);
        vo.setOpenGatePercent(percent(openGates, totalGates));

        // ③ 未处理告警数：未关闭即未处理（#1# 未确认/#2# 已确认/#3# 处理中）
        vo.setUnhandledAlarmCount(dashboardMapper.selectUnhandledAlarmCount());
        return vo;
    }

    @Override
    public List<WaterAlertVO> activeAlerts(String site) {
        List<WaterAlertVO> alerts = dashboardMapper.selectActiveAlertsBySite(site);
        // 无告警返回空列表（不返回 null）
        return alerts != null ? alerts : Collections.emptyList();
    }

    /** 百分比 = 分子 ÷ 分母 × 100，1 位小数 HALF_UP；分母 0 → null（防除零，不误导为 0%） */
    private BigDecimal percent(long part, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    /** Map 值 → long（null 安全） */
    private long toLong(Map<String, Object> row, String key) {
        Object v = row != null ? row.get(key) : null;
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return v != null ? Long.parseLong(v.toString()) : 0L;
    }
}
