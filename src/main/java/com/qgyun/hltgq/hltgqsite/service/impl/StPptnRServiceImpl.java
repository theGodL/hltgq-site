package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qgyun.hltgq.hltgqsite.entity.StPptnR;
import com.qgyun.hltgq.hltgqsite.mapper.StPptnRMapper;
import com.qgyun.hltgq.hltgqsite.service.StPptnRService;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class StPptnRServiceImpl extends ServiceImpl<StPptnRMapper, StPptnR> implements StPptnRService {

    @Override
    public List<StPptnR> latestPerStation() {
        return baseMapper.selectLatestPerStation();
    }

    @Override
    public IPage<StPptnR> dailyPage(IPage<StPptnR> page, QueryWrapper<StPptnR> wrapper) {
        List<StPptnR> all = baseMapper.selectDailyAll(wrapper);
        long total = all.size();
        int start = (int) ((page.getCurrent() - 1) * page.getSize());
        int end   = (int) Math.min(start + page.getSize(), total);
        page.setTotal(total);
        page.setRecords(start >= total ? Collections.emptyList() : all.subList(start, end));
        return page;
    }

    @Override
    public List<StPptnR> todaySumPerStation(LocalDateTime start, LocalDateTime end) {
        return baseMapper.selectTodaySumPerStation(Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    @Override
    public boolean saveOrUpdateByKey(StPptnR entity) {
        boolean exists = count(new QueryWrapper<StPptnR>()
                .eq("STCD", entity.getStcd())
                .eq("TM", Timestamp.valueOf(entity.getTm()))) > 0;
        if (exists) {
            return update(entity, new UpdateWrapper<StPptnR>()
                    .eq("STCD", entity.getStcd())
                    .eq("TM", Timestamp.valueOf(entity.getTm())));
        }
        return save(entity);
    }
}
