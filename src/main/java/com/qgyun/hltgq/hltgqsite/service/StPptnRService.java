package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallChartVO;
import com.qgyun.hltgq.hltgqsite.vo.GqRainfallVO;

import java.time.LocalDateTime;
import java.util.List;

public interface StPptnRService extends IService<StPptnR> {

    boolean saveOrUpdateByKey(StPptnR entity);

    List<StPptnR> latestPerStation();

    IPage<StPptnR> dailyPage(IPage<StPptnR> page, QueryWrapper<StPptnR> wrapper);

    List<StPptnR> todaySumPerStation(LocalDateTime start, LocalDateTime end);

    /**
     * 灌区雨量分页查询：每站点最新一条，含1h/3h/6h时段增量
     */
    IPage<GqRainfallVO> gqRainfallPage(long page, long size, String stcd, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 灌区雨量变化图表：单站点小时级增量+累计雨量
     */
    GqRainfallChartVO gqRainfallChart(String stcd, LocalDateTime startTime, LocalDateTime endTime);
}
