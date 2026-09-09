package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityTrendVO;
import com.qgyun.hltgq.hltgqsite.vo.WaterQualityVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水质监测服务
 */
public interface WaterQualityService {

    /**
     * 首页：每站点最新一条水质数据（含该站水质阈值行）
     *
     * @param stcds     站点标识列表（编号或 site UUID，可选），null/空 → 全部
     * @param startDate 起始日期（可选，yyyy-MM-dd），仅返回该日期区间内的最新记录
     * @param endDate   截止日期（可选，yyyy-MM-dd，含当日），与 startDate 单独或成对使用
     */
    List<WaterQualityVO> monitoring(List<String> stcds, LocalDate startDate, LocalDate endDate);

    /**
     * 水质趋势：2 小时级六指标曲线（COD/BOD、氨氮/总氮/总磷、溶解氧三图共用），
     * 桶起点对齐偶数小时 00:00/02:00/...，跨天连续；附该站水质阈值行供预警线。
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（可选，默认 24 小时前，对齐偶数小时）
     * @param endTime   截止时间（可选，默认当前时间，对齐偶数小时）
     */
    WaterQualityTrendVO trend(String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 历史数据分页（按监测时间倒序）
     *
     * @param stcd      站点编号或 site UUID（必填）
     * @param startTime 起始时间（含，可选）
     * @param endTime   截止时间（含，可选）
     * @param page      页码，从 1 开始
     * @param size      每页条数
     */
    Page<WaterQualityVO> history(String stcd, LocalDateTime startTime, LocalDateTime endTime,
                                 long page, long size);

    /**
     * 水质监测全部站点（供下拉选择）
     */
    List<StationSiteVO> sites();
}
