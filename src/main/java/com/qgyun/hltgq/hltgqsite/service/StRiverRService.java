package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;

import java.util.List;

public interface StRiverRService extends IService<StRiverR> {

    boolean saveOrUpdateByKey(StRiverR entity);

    List<StRiverR> latestPerStation();
}
