package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 闸门动作记录（DZJL → t_auto_hltgq_water_gate_action）只读查询。
 *
 * <p>动作表由 hltgq-mq 的 MqttGateActionService 写入：闸门每次开启/关闭动作落一条，
 * action_type = open（开启）/ close（关闭），action_time 为设备时标（非入库时间 tm）。
 * 本服务只读，不写入。
 *
 * <p>展示口径（与《DZJL 闸门动作展示层对接说明》一致）：一次「开→关」为两条独立记录，
 * 开启时段 = 同站同孔 open 之后最近一条 close；未配到 close 表示仍在开启中。
 */
@Mapper
public interface GateActionMapper {

    /**
     * 各站点各闸孔最近一次开闸动作及其配对关闸。
     *
     * <p>按 site + gate_no 取最近一条 open（DISTINCT ON），LATERAL 取其后最近一条 close；
     * 一次批量返回全部站点的动作，供 monitoring 接口按「开度>0 的闸孔」挑选最近一次操作，
     * 避免逐站 N+1 查询。动作表每天新增个位数到几十条，查询成本可忽略。
     *
     * @param siteIds 站点 UUID 列表（非空）
     * @param endTime 查询截止时间（可选）：传值时只取该时刻之前的动作，与 monitoring 的时间范围口径一致；
     *                null = 不限（取当前最新一次开闸）
     * @return site / gate_no / open_time / close_time（close_time 为 null = 尚未关闸）
     */
    @Select("<script>" +
            "SELECT o.site, o.gate_no, o.action_time AS open_time, c.action_time AS close_time " +
            "FROM ( " +
            "  SELECT DISTINCT ON (a.site, a.gate_no) a.site, a.gate_no, a.action_time " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_gate_action a " +
            "  WHERE a.action_type = 'open' " +
            "    AND a.action_time IS NOT NULL " +
            "    AND a.gate_no IS NOT NULL " +
            "    <if test='endTime != null'>AND a.action_time &lt;= #{endTime} </if>" +
            "    AND a.site IN " +
            "    <foreach collection='siteIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> " +
            "  ORDER BY a.site, a.gate_no, a.action_time DESC " +
            ") o " +
            "LEFT JOIN LATERAL ( " +
            "  SELECT c.action_time " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_gate_action c " +
            "  WHERE c.site = o.site " +
            "    AND c.gate_no = o.gate_no " +
            "    AND c.action_type = 'close' " +
            "    AND c.action_time &gt; o.action_time " +
            "  ORDER BY c.action_time ASC " +
            "  LIMIT 1 " +
            ") c ON true " +
            "</script>")
    List<Map<String, Object>> selectLatestOpenBySiteHoles(@Param("siteIds") List<String> siteIds,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 指定站点截至 endTime 的全部开闸区间（同孔 open → 其后最近 close）。
     *
     * <p>供历史开度统计按行匹配：监测时刻落在 [open, close) 内时该行展示该次操作起止。
     * 不按 startTime 精确截断——覆盖查询区间起点的 open 动作可能发生在起点之前，
     * 故只设 minOpenTime 下界（调用方按查询起点回溯数个月），避免把该站全部历史 open
     * 连同逐行 LATERAL 配对拉回内存。动作表数据量小（四站每天个位数到几十条），
     * 单站近半年的配对成本可忽略。
     *
     * @param siteId     站点 UUID
     * @param minOpenTime 开闸时间下界（含）；null = 不限制
     * @param endTime    截止时间（含）；null = 不限制
     * @return gate_no / open_time / close_time（close_time 为 null = 仍在开启中）/ open_raw / close_raw
     *         （raw = MQTT 点位增量原始报文，如 {"names":["QSDZ_ZM1_KQ.PV"],"vals":["1"],...}），按开闸时间升序
     */
    @Select("<script>" +
            "SELECT o.gate_no, o.action_time AS open_time, o.raw AS open_raw, " +
            "       c.action_time AS close_time, c.raw AS close_raw " +
            "FROM ( " +
            "  SELECT a.gate_no, a.action_time, a.raw " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_gate_action a " +
            "  WHERE a.site = #{siteId} " +
            "    AND a.action_type = 'open' " +
            "    AND a.action_time IS NOT NULL " +
            "    AND a.gate_no IS NOT NULL " +
            "    <if test='minOpenTime != null'>AND a.action_time &gt;= #{minOpenTime} </if>" +
            "    <if test='endTime != null'>AND a.action_time &lt;= #{endTime} </if>" +
            ") o " +
            "LEFT JOIN LATERAL ( " +
            "  SELECT c.action_time, c.raw " +
            "  FROM \"qixiao-apaas\".t_auto_hltgq_water_gate_action c " +
            "  WHERE c.site = #{siteId} " +
            "    AND c.gate_no = o.gate_no " +
            "    AND c.action_type = 'close' " +
            "    AND c.action_time &gt; o.action_time " +
            "  ORDER BY c.action_time ASC " +
            "  LIMIT 1 " +
            ") c ON true " +
            "ORDER BY o.action_time " +
            "</script>")
    List<Map<String, Object>> selectOpenIntervalsBySite(@Param("siteId") String siteId,
                                                        @Param("minOpenTime") LocalDateTime minOpenTime,
                                                        @Param("endTime") LocalDateTime endTime);
}
