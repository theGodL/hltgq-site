package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.GateMonitoringVO;
import com.qgyun.hltgq.hltgqsite.vo.GateStationWaterLevelVO;

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

    /**
     * 闸站图表：固定七站在选中时间点的闸前/闸后水位
     * <p>命中规则：取各站距选中时间点最近（±30 分钟内）的闸门入库数据；
     * 半小时内无入库数据则该站水位为 null（表示该时间点无报文）。
     *
     * @param time 选中时间点（半小时粒度，如 2026-08-16 17:30）
     * @return 固定顺序七站水位列表（顺序：渠首进水闸、双庙湖节制闸、南山寺节制闸、
     * 毕岭节制闸、汪元节制闸、北干渠进水闸、南干渠进水闸）
     */
    List<GateStationWaterLevelVO> stationWaterLevel(LocalDateTime time);
}
