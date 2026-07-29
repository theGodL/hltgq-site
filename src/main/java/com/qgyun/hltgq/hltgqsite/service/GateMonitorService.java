package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.GateHistoryVO;
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

    /**
     * 闸门历史数据（分页，按监测时间倒序）
     *
     * @param siteId    站点 UUID（可选）
     * @param gateNo    闸孔编号（可选，如 "1"、"2"），null/空=全部
     * @param startTime 起始时间（含），null 表示不限制
     * @param endTime   截止时间（含），null 表示不限制
     * @param page      页码
     * @param size      每页条数
     */
    Page<GateHistoryVO> history(String siteId, String gateNo, LocalDateTime startTime, LocalDateTime endTime, long page, long size);
}
