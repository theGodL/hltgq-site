package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.vo.GateCumulativeFlowVO;
import com.qgyun.hltgq.hltgqsite.vo.GateMonthCumulativeFlowVO;
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

    /**
     * 闸站累计流量（月累计 + 年累计）
     * <p>月累计 = 当月 1日 0点起至最新数据时间 = ttf(最新) − ttf(monthStart 前最近行)；
     * 年累计 = 当年 1月1日 0点起至最新数据时间（流量表 ytf）。
     *
     * @param siteId     站点 UUID（必填）
     * @param monthStart 月累计起点（可选，null 默认当月 1日 0点）
     * @return 月累计与年累计（改造前无 ytf/ttf 数据时为 null）
     */
    GateCumulativeFlowVO cumulativeFlow(String siteId, LocalDateTime monthStart);

    /**
     * 闸站月度累计取水量趋势（近 months 个月，含当月）
     * <p>月累计口径同 cumulativeFlow：当月 1日 0点 ≤ tm < 下月 1日 0点
     * = ttf(月内最新) − ttf(月初前最近)，当月累计截至最新数据时间。
     *
     * @param siteId 站点 UUID（必填）
     * @param months 月数（默认 12，上限 24），当前月为最后一个月
     * @return 每月一个数据点（时间升序，最早在前），月内无 ttf 数据时累计为 null
     */
    List<GateMonthCumulativeFlowVO> monthlyCumulativeFlow(String siteId, int months);

    /**
     * 闸站召测：对四站（北干渠进水闸、南干渠进水闸、毕岭节制闸、汪元节制闸）下发召测指令
     * <p>异步触发模式：接口立即返回，召测转发在后台线程执行（服务端 /api/recall 同步挂起
     * 等待 RTU 应答入库，窗口 5 分钟 + 1 分钟余量）；前端随后轮询 /recall-status 直至收敛
     * （判定时间在服务端：CONFIRMED = 数据已入库 / IDLE = 超时）。
     *
     * @param stcds 待召测站码列表（null/空 = 四站全部；重试仅传上次失败的站）
     * @return success=true + msg（指令已下发）
     */
    Map<String, Object> recallStations(List<String> stcds);

    /**
     * 闸站召测状态：聚合查询四站状态（页面加载/刷新后恢复按钮状态用）
     * <p>转发至召测服务 /api/recall/status（每站），聚合规则：任一 RECALLING → 召测中；
     * 全部 CONFIRMED → 数据确认；否则空闲（IDLE）。
     *
     * @return status（聚合态：RECALLING/CONFIRMED/IDLE）+ stations（每站 stcd/siteName/status/msg）
     */
    Map<String, Object> recallStatus();

    /**
     * 闸站召测确认复位：对四站全调 /api/recall/confirm（幂等），清除服务端确认状态
     * <p>用户点击绿色"数据确认"后调用（先调本接口，再刷新图表数据，按钮复位为召测样式）。
     *
     * @return success + results（每站 stcd/siteName/code/msg）
     */
    Map<String, Object> recallConfirm();
}
