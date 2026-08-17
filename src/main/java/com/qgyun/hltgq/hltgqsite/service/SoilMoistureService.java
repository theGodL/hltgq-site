package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.SoilMoistureVO;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 墒情监测服务
 */
public interface SoilMoistureService {

    /**
     * 首页：每站点最新一条墒情数据
     *
     * @param stcds 站点标识列表（编号或 site UUID，可选），null/空 → 全部
     * @param date  监测日期（可选，yyyy-MM-dd），仅返回该日期内的最新记录
     */
    List<SoilMoistureVO> monitoring(List<String> stcds, LocalDate date);

    /**
     * 墒情趋势：小时级含水量曲线（8 个深度各一条线）
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（可选，默认 7 天前整点）
     * @param endTime   截止时间（可选，默认当前整点）
     */
    SoilMoistureTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 历史数据分页（按监测时间倒序）
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（含，可选）
     * @param page      页码，从 1 开始
     * @param size      每页条数
     */
    Page<SoilMoistureVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime,
                                 long page, long size);

    /**
     * 墒情监测全部站点
     */
    List<StationSiteVO> sites();
}
