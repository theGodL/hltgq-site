package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.WaterAlertVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 设备状态概览（大屏）统计查询
 */
public interface DashboardMapper {

    /**
     * 设备总数与在线设备数：完全按站点状态字段 zebpsu（#1# 在线、#2# 离线，由报文入库项目维护）
     * <p>COUNT(d.id) 而非 COUNT(*)：LEFT JOIN 下关联不到站点的设备不误计。
     * <p>注意：本查询为 &lt;script&gt; 动态 SQL，SQL 内所有尖括号均需 XML 转义（如 &amp;lt;、&amp;gt;）。
     *
     * @param site 站点主键 ID（= 设备表 site 列值），null/空 = 全部站点
     */
    @Select("<script>" +
            "SELECT COUNT(d.id) AS total_cnt, " +
            "COUNT(d.id) FILTER (WHERE s.zebpsu = '#1#') AS online_cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON d.site = s.id " +
            "<if test='site != null and site != \"\"'>WHERE d.site = #{site} </if>" +
            "</script>")
    Map<String, Object> selectDeviceCount(@Param("site") String site);

    /**
     * 闸门总数与开启数：每个闸孔取最新一条（DISTINCT ON 模式）按开度判定，
     * 开启 = open_degree &gt; 0；排除站级行（gate_no='0'）与哨兵开度（-999 无信号/-9991 设备异常/null）；
     * 限定近 24h 有上报（长期无上报闸孔不计入当前开关率，且走 (device, tm) 索引）。
     * <p>注意：本查询为 &lt;script&gt; 动态 SQL，SQL 内所有尖括号均需 XML 转义（如 &amp;lt;、&amp;gt;）。
     *
     * @param site 站点主键 ID（= 闸门表 site 列值），null/空 = 全部站点
     */
    @Select("<script>" +
            "SELECT COUNT(*) AS total_cnt, " +
            "COUNT(*) FILTER (WHERE t.open_degree &gt; 0) AS open_cnt " +
            "FROM ( " +
            "  SELECT DISTINCT ON (t.device) t.device, t.open_degree " +
            "  FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" t " +
            "  WHERE t.gate_no &lt;&gt; '0' " +
            "    AND t.tm &gt;= now() - INTERVAL '24 hours' " +
            "    AND t.open_degree IS NOT NULL " +
            "    AND t.open_degree &gt;= 0 " +
            "<if test='site != null and site != \"\"'>    AND t.site = #{site} </if>" +
            "  ORDER BY t.device, t.tm DESC " +
            ") t" +
            "</script>")
    Map<String, Object> selectGateCount(@Param("site") String site);

    /**
     * 未处理告警数：未关闭即未处理（#1# 未确认、#2# 已确认、#3# 处理中；#4# 已关闭不计），
     * 用 IN 白名单而非排除法，防御 status 为 null/未知编码的脏行。
     *
     * @param site 站点主键 ID（= 告警表 site 列值），null/空 = 全部站点
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" " +
            "WHERE status IN ('#1#', '#2#', '#3#') " +
            "<if test='site != null and site != \"\"'>AND site = #{site} </if>" +
            "</script>")
    long selectUnhandledAlarmCount(@Param("site") String site);

    /**
     * 某站点未关闭的告警列表（未关闭即未处理：#1# 未确认、#2# 已确认、#3# 处理中；#4# 已关闭不计），
     * IN 白名单防御 status 为 null/未知编码的脏行；按发生时间倒序（最新在前）。
     * <p>列名与 WaterAlertVO 属性同名，MyBatis 自动映射。
     *
     * @param site 站点主键 ID（必填）
     */
    @Select("SELECT id, code, site, device, content, \"level\", status, time " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" " +
            "WHERE site = #{site} AND status IN ('#1#', '#2#', '#3#') " +
            "ORDER BY time DESC")
    List<WaterAlertVO> selectActiveAlertsBySite(@Param("site") String site);
}
