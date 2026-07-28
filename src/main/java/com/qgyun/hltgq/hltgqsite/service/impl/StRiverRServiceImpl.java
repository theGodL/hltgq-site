package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.mapper.StRiverRMapper;
import com.qgyun.hltgq.hltgqsite.service.StRiverRService;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class StRiverRServiceImpl extends ServiceImpl<StRiverRMapper, StRiverR> implements StRiverRService {

    @Override
    public List<StRiverR> latestPerStation() {
        return baseMapper.selectLatestPerStation();
    }

    @Override
    public boolean saveOrUpdateByKey(StRiverR entity) {
        boolean exists = count(new QueryWrapper<StRiverR>()
                .eq("STCD", entity.getStcd())
                .eq("TM", Timestamp.valueOf(entity.getTm()))) > 0;
        if (exists) {
            return update(entity, new UpdateWrapper<StRiverR>()
                    .eq("STCD", entity.getStcd())
                    .eq("TM", Timestamp.valueOf(entity.getTm())));
        }
        return save(entity);
    }
}
