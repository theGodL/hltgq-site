package com.qgyun.hltgq.hltgqsite.service;

import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 闸门监测服务
 */
public interface GateMonitorService {

    /**
     * 查询各闸站最新闸孔数据（每个闸站一条记录）
     *
     * @param startTime 起始时间（含），null 表示不限制
     * @param endTime   截止时间（含），null 表示不限制
     * @return 各闸站监测数据列表（按站点名称排序）
     */
    List<GateMonitoringVO> monitoring(LocalDateTime startTime, LocalDateTime endTime);
}
