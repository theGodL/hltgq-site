package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
}
