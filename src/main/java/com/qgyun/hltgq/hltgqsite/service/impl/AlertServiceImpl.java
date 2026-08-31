package com.qgyun.hltgq.hltgqsite.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.mapper.AlertMapper;
import com.qgyun.hltgq.hltgqsite.service.AlertService;
import com.qgyun.hltgq.hltgqsite.vo.AlertPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 告警查询服务实现
 */
@Service
public class AlertServiceImpl implements AlertService {

    @Autowired
    private AlertMapper alertMapper;

    @Override
    public Page<AlertPageVO> alertPage(String siteName, String deviceName,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       long page, long size) {
        // 查询总数
        long total = alertMapper.selectAlertCount(siteName, deviceName, startTime, endTime);

        Page<AlertPageVO> result = new Page<>(page, size);
        result.setTotal(total);

        if (total == 0) {
            result.setRecords(Collections.emptyList());
            return result;
        }

        // 分页查询
        int offset = (int) ((page - 1) * size);
        int limit = (int) size;
        List<AlertPageVO> records = alertMapper.selectAlertPage(
                siteName, deviceName, startTime, endTime, limit, offset);
        result.setRecords(records);

        return result;
    }
}
