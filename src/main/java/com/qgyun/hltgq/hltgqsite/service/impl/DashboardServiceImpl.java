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
    public DashboardOverviewVO overview(String site) {
        // 站点过滤（可选）：站点主键 ID（= 设备/闸门/告警表 site 列值）；空串/空白归一为 null（全量）
        String siteKey = (site == null || site.trim().isEmpty()) ? null : site.trim();
        DashboardOverviewVO vo = new DashboardOverviewVO();

        // ① 设备总数/在线数：设备台账口径，与 /network-device/summary 同一判定
        //（设备 status 优先、空回退所属站点 zebpsu）；统计对象是设备，不是站点
        Map<String, Object> deviceRow = dashboardMapper.selectDeviceCount(siteKey);
        long totalDevices = toLong(deviceRow, "total_cnt");
        long onlineDevices = toLong(deviceRow, "online_cnt");
        vo.setTotalDeviceCount(totalDevices);
        vo.setOnlineDeviceCount(onlineDevices);
        vo.setOnlineDevicePercent(percent(onlineDevices, totalDevices));

        // ② 闸门总数/开启数：统计对象是闸门设备（闸孔）（设备台账 type 含 #4#，与 /network-device/summary
        // 闸门分类同一对象）；开启 = 该设备近 24h 最新开度 > 0（排除站级行与无信号/异常哨兵值）
        Map<String, Object> gateRow = dashboardMapper.selectGateCount(siteKey);
        long totalGates = toLong(gateRow, "total_cnt");
        long openGates = toLong(gateRow, "open_cnt");
        vo.setTotalGateCount(totalGates);
        vo.setOpenGateCount(openGates);
        vo.setOpenGatePercent(percent(openGates, totalGates));

        // ③ 未处理告警数：未关闭即未处理（#1# 未确认/#2# 已确认/#3# 处理中）
        vo.setUnhandledAlarmCount(dashboardMapper.selectUnhandledAlarmCount(siteKey));
        return vo;
    }

    @Override
    public List<WaterAlertVO> activeAlerts(String site) {
        List<WaterAlertVO> alerts = dashboardMapper.selectActiveAlertsBySite(site);
        // 无告警返回空列表（不返回 null）
        return alerts != null ? alerts : Collections.emptyList();
    }

    /** 百分比 = 分子 ÷ 分母 × 100，2 位小数 HALF_UP；分母 0 → null（防除零，不误导为 0%） */
    private BigDecimal percent(long part, long total) {
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
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
