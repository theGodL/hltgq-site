package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.IrrigationWaterLevelMapper;
import com.qgyun.hltgq.hltgqsite.service.IrrigationWaterLevelService;
import com.qgyun.hltgq.hltgqsite.vo.IrrigationWaterLevelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 水位监测-灌区服务实现
 */
@Service
public class IrrigationWaterLevelServiceImpl implements IrrigationWaterLevelService {

    @Autowired
    private IrrigationWaterLevelMapper irrigationWaterLevelMapper;

    @Override
    public Page<IrrigationWaterLevelVO> page(Page<IrrigationWaterLevelVO> page,
                                              String stcd,
                                              LocalDateTime startTime,
                                              LocalDateTime endTime) {
        // ew1: 日期过滤（作用于子查询中确定"最新"记录的范围）
        QueryWrapper<?> dateWrapper = new QueryWrapper<>();
        if (startTime != null) {
            dateWrapper.ge("TM", Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            dateWrapper.le("TM", Timestamp.valueOf(endTime));
        }

        // ew2: 站点编号过滤（作用于外层结果）
        QueryWrapper<?> stcdWrapper = new QueryWrapper<>();
        if (stcd != null && !stcd.trim().isEmpty()) {
            stcdWrapper.eq("r.STCD", stcd.trim());
        }

        // 查询总数
        long total = irrigationWaterLevelMapper.selectCount(dateWrapper, stcdWrapper);
        page.setTotal(total);

        if (total == 0) {
            return page;
        }

        // 计算分页偏移
        int offset = (int) ((page.getCurrent() - 1) * page.getSize());
        int limit = (int) page.getSize();

        // 分页查询
        List<IrrigationWaterLevelVO> records = irrigationWaterLevelMapper.selectPage(
                dateWrapper, stcdWrapper, limit, offset);
        page.setRecords(records);

        return page;
    }
}
