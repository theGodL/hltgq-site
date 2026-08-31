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
    public Page<AlertPageVO> alertPage(String siteName, String deviceName, String type, String siteType,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       long page, long size) {
        // 防御非法分页参数：page/size 非正或偏移越界会触发数据库错误（如 OFFSET must not be negative），统一转 400
        if (page < 1 || size < 1 || size > 1000 || (page - 1) * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分页参数非法：page/size 必须为正整数且 size 不超过 1000");
        }

        // type 逻辑分类 → 过滤条件：overlimit=仅阈值超限（type='#1#'）、other=仅非超限（异常告警）；
        // 两者互斥，不传（或非法值）视为全部
        Boolean typeOverlimit = "overlimit".equals(type) ? Boolean.TRUE : null;
        Boolean typeOther = "other".equals(type) ? Boolean.TRUE : null;

        // siteType 站点类型筛选：数字编码翻译为 '#N#'，LIKE 匹配支持多类型站点（如 '#1#|#4#'）；
        // 非法值视为不筛选
        String siteTypeFilter = translateSiteType(siteType);

        // 查询总数
        long total = alertMapper.selectAlertCount(
                siteName, deviceName, typeOverlimit, typeOther, siteTypeFilter, startTime, endTime);

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
                siteName, deviceName, typeOverlimit, typeOther, siteTypeFilter, startTime, endTime, limit, offset);
        result.setRecords(records);

        return result;
    }

    /** 站点类型数字 → epjutj 编码（#1# 水位、#2# 雨量、#3# 流量、#4# 闸门、#5# 视频、#7# 墒情、#8# 水质）；非法值返回 null（不筛选） */
    private String translateSiteType(String siteType) {
        if (siteType == null) return null;
        String t = siteType.trim();
        if (t.length() == 1 && "1234578".indexOf(t.charAt(0)) >= 0) {
            return "#" + t + "#";
        }
        return null;
    }
}
