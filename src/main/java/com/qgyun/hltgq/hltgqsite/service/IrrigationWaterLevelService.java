package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelChartVO;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelHistoryVO;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelVO;

import java.time.LocalDateTime;

/**
 * 水位监测-灌区服务
 */
public interface IrrigationWaterLevelService {

    /**
     * 分页查询灌区水位数据
     *
     * @param page      分页参数
     * @param stcd      站点编号（可选）
     * @param startTime 监测开始时间（可选）
     * @param endTime   监测结束时间（可选）
     * @return 分页结果
     */
    Page<IrrigationWaterLevelVO> page(Page<IrrigationWaterLevelVO> page,
                                       String stcd,
                                       LocalDateTime startTime,
                                       LocalDateTime endTime);

    /**
     * 水位变化图表：单站点小时级水位值 + 水位变化（用于水位统计曲线图）
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @return 水位变化图表数据
     */
    IrrigationWaterLevelChartVO waterLevelChart(String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 水位历史数据（分页）：单站点小时级水位值 + 1h涨幅
     *
     * @param stcd      站点编号（必填）
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param page      页码
     * @param size      每页条数
     * @return 分页结果
     */
    Page<IrrigationWaterLevelHistoryVO> waterLevelHistory(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size);
}
