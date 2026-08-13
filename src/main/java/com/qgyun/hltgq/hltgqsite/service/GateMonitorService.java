package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 闸门监测服务
 */
public interface GateMonitorService {

    /**
     * 查询各闸站最新闸孔数据（每个闸站一条记录）
     *
     * @param site      站点 UUID（可选，不传=全部站点）
     * @param startTime 起始时间（含），null 表示不限制
     * @param endTime   截止时间（含），null 表示不限制
     * @return 各闸站监测数据列表（按站点名称排序）
     */
    List<GateMonitoringVO> monitoring(String site, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 闸门历史数据（分页，按监测时间倒序）
     *
     * @param siteId    站点 UUID（可选）
     * @param type      数据类型："opening"（开度）或 "waterLevel"（水位）
     * @param startTime 起始时间（含），null 表示不限制
     * @param endTime   截止时间（含），null 表示不限制
     * @param page      页码
     * @param size      每页条数
     */
    Page<Map<String, Object>> history(String siteId, String type, LocalDateTime startTime, LocalDateTime endTime,
                                       long page, long size);
}
