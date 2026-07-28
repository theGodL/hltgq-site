package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;

import java.time.LocalDateTime;
import java.util.List;

public interface StPptnRService extends IService<StPptnR> {

    boolean saveOrUpdateByKey(StPptnR entity);

    List<StPptnR> latestPerStation();

    IPage<StPptnR> dailyPage(IPage<StPptnR> page, QueryWrapper<StPptnR> wrapper);

    List<StPptnR> todaySumPerStation(LocalDateTime start, LocalDateTime end);
}
