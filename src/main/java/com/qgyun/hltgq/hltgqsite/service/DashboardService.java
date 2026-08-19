package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.DashboardOverviewVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterAlertVO;

import java.util.List;

/**
 * 设备状态概览服务（大屏统计）
 */
public interface DashboardService {

    /**
     * 设备状态概览：设备总数/在线数/在线百分比（按站点状态）、
     * 闸门总数/开启数/开启百分比（按最新开度）、未处理告警数（未关闭）
     */
    DashboardOverviewVO overview();

    /**
     * 某站点未关闭的告警列表（#4# 已关闭不计），按发生时间倒序
     *
     * @param site 站点主键 ID（必填）
     */
    List<WaterAlertVO> activeAlerts(String site);
}
