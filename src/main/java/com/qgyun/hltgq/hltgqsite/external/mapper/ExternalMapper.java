package com.qgyun.hltgq.hltgqsite.external.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 三维系统对接（/external）聚合查询。
 * <p>数据口径与页面接口一致：表均带 schema `qixiao-apaas`；
 * 巡检次数仅统计已提交记录（status=#2# 排除草稿）；问题按发现时间 time 全状态统计。
 */
@Mapper
public interface ExternalMapper {

    /**
     * 闸站最新一条闸门数据（闸前/闸后水位），site = 档案表 id。
     * 无效值清洗（-999 设备不存在、-9991 设备异常）由 Service 层处理。
     */
    @Select("SELECT tm, up_z, down_z " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" " +
            "WHERE site = #{site} " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestGateLevel(@Param("site") String site);

    /**
     * 闸站最新一条有效流量（排除 -999/-9991 无效值），site = 档案表 id。
     */
    @Select("SELECT tm, q " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" " +
            "WHERE site = #{site} AND q NOT IN (-999, -9991) " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestFlow(@Param("site") String site);

    /**
     * 站点档案行（id/iofhpi/zzkaec）：站点键兼容站点编码（iofhpi）与站点 ID（id，
     * 即监测表 site 值；MQTT 站无 stcd 时可用）；同时命中时优先编码命中行。
     */
    @Select("SELECT id, iofhpi, zzkaec " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE iofhpi = #{key} OR id = #{key} " +
            "ORDER BY CASE WHEN iofhpi = #{key} THEN 0 ELSE 1 END LIMIT 1")
    Map<String, Object> selectStationByKey(@Param("key") String key);

    /**
     * 站点档案列表（按监测类型 + 可选站名模糊）：监测类型 epjutj 含 #{typeCode}（编码 #N#，多类型以 | 分隔）
     * 的站点，name 非空时再按站名 zzkaec 模糊过滤，按站点编号 iofhpi 升序。
     * <p>供监测站点列表接口按档案口径输出（与原 qx-api 列表同口径：含当前无数据的站点）；
     * 关联名称列：管理单位 = ahieto 自关联本表 zzkaec、渠系 = ywvyds 关联渠系管理表 gfaegg、
     * 创建/更新人 = created_by/updated_by 关联平台用户表 name。
     */
    @Select("<script>" +
            "SELECT s.id AS id, s.iofhpi AS iofhpi, s.zzkaec AS zzkaec, " +
            "s.devicecode AS devicecode, s.cadcode AS cadcode, s.mivbcz AS mivbcz, s.epjutj AS epjutj, " +
            "s.nxtggq AS nxtggq, s.bviiio_x AS bviiio_x, s.bviiio_y AS bviiio_y, " +
            "s.bviiio_geohash AS bviiio_geohash, s.zebpsu AS zebpsu, " +
            "s.ahieto AS ahieto, u.zzkaec AS ahieto_title, " +
            "s.ywvyds AS ywvyds, c.gfaegg AS ywvyds_title, " +
            "s.waljdn AS waljdn, s.bhsqxd AS bhsqxd, s.lhwhuc AS lhwhuc, " +
            "s.cbitue AS cbitue, s.viwmmc AS viwmmc, s.discharge_type AS discharge_type, " +
            "s.badfhe AS badfhe, s.nwbzla AS nwbzla, s.ccnhtm AS ccnhtm, s.ijzsby AS ijzsby, " +
            "s.corp_code AS corp_code, s.created_at AS created_at, " +
            "s.created_by AS created_by, cu.name AS created_by_title, " +
            "s.updated_at AS updated_at, s.updated_by AS updated_by, uu.name AS updated_by_title " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" u ON s.ahieto = u.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\" c ON s.ywvyds = c.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" cu ON s.created_by = cu.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" uu ON s.updated_by = uu.id " +
            "WHERE s.epjutj LIKE CONCAT('%', #{typeCode}, '%') " +
            "<if test='name != null and name != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{name}, '%') </if>" +
            "ORDER BY s.iofhpi" +
            "</script>")
    List<Map<String, Object>> selectArchiveByType(@Param("typeCode") String typeCode,
                                                  @Param("name") String name);

    /**
     * 站点数量统计（大屏「站点数」卡片）：一次聚合，无逐类型多次查询。
     * <p>total = 档案全量行数；typed_total = epjutj 含 #1#/#2#/#3#/#4#/#5#/#7#/#8# 任一（7 类
     * 监测类型，按站去重；与 selectArchiveByType 的 LIKE 口径完全同源，#6# 模型类型不计入）；
     * type_1..type_8 = 各类型单计，多类型站重复计。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#1#%' OR epjutj LIKE '%#2#%' OR epjutj LIKE '%#3#%' " +
            "OR epjutj LIKE '%#4#%' OR epjutj LIKE '%#5#%' OR epjutj LIKE '%#7#%' OR epjutj LIKE '%#8#%') " +
            "AS typed_total, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#1#%') AS type_1, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#2#%') AS type_2, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#3#%') AS type_3, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#4#%') AS type_4, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#5#%') AS type_5, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#6#%') AS type_6, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#7#%') AS type_7, " +
            "COUNT(*) FILTER (WHERE epjutj LIKE '%#8#%') AS type_8 " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"")
    Map<String, Object> selectStationCount();

    /**
     * 站点下拉选项（轻量：仅 id/站名/编号）：站点集合与 selectStationCount 的 typed_total 同口径
     * （7 类监测类型任一，按站去重）；typeCode 非空时再限定该类型（如 '#4#' 仅闸门站）。
     * <p>供告警筛选等下拉框一次拉取，不返回档案全量字段；按站点编号 iofhpi 升序（无编号排在最后）。
     */
    @Select("<script>" +
            "SELECT s.id AS id, s.zzkaec AS name, s.iofhpi AS stcd " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "WHERE (s.epjutj LIKE '%#1#%' OR s.epjutj LIKE '%#2#%' OR s.epjutj LIKE '%#3#%' " +
            "OR s.epjutj LIKE '%#4#%' OR s.epjutj LIKE '%#5#%' OR s.epjutj LIKE '%#7#%' " +
            "OR s.epjutj LIKE '%#8#%') " +
            "<if test='typeCode != null'>AND s.epjutj LIKE CONCAT('%', #{typeCode}, '%') </if>" +
            "ORDER BY s.iofhpi NULLS LAST" +
            "</script>")
    List<Map<String, Object>> selectStationOptions(@Param("typeCode") String typeCode);

    /**
     * 巡检汇总：累计巡检次数（已提交）/ 巡检计划总数 / 完成巡检数（计划已完成）。
     * 三条计数合并为一条 SQL（三张表同 schema，逐条 COUNT 子查询）。
     */
    @Select("SELECT " +
            "(SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" WHERE status = '#2#') AS patrol_count, " +
            "(SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\") AS schedule_count, " +
            "(SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" WHERE status = '#3#') AS finished_count")
    Map<String, Object> selectPatrolSummary();

    /**
     * 逐日巡检次数：已提交记录按巡检时间 time 取日期聚合。
     */
    @Select("SELECT to_char(time, 'YYYY-MM-DD') AS day, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE status = '#2#' AND time >= #{startTime} AND time <= #{endTime} " +
            "GROUP BY to_char(time, 'YYYY-MM-DD')")
    List<Map<String, Object>> selectDailyPatrol(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 逐日突发事件数量：AI 智能分析告警（type=#3#）按发生时间 time 取日期聚合（全状态）。
     */
    @Select("SELECT to_char(time, 'YYYY-MM-DD') AS day, COUNT(*) AS cnt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" " +
            "WHERE type = '#3#' AND time >= #{startTime} AND time <= #{endTime} " +
            "GROUP BY to_char(time, 'YYYY-MM-DD')")
    List<Map<String, Object>> selectDailyEmergency(@Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 问题状态分布：name = status 编码（#1# 待处理 / #2# 处理中 / #3# 已转工单 / #4# 已关闭 / #5# 已作废）。
     */
    @Select("SELECT status AS name, COUNT(*) AS \"value\" " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" " +
            "GROUP BY status")
    List<Map<String, Object>> selectIssueStatus();

    /**
     * 突发事件（AI 智能分析告警）统计：total = type=#3# 告警总数，
     * closed = 其中已关闭（status=#4#，即已解除响应）数；未解除 = total - closed。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN status = '#4#' THEN 1 ELSE 0 END) AS closed " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" " +
            "WHERE type = '#3#'")
    Map<String, Object> selectEmergencyStats();

    /**
     * 视频设备按管理所聚合：安装位置 wlcvig 按「-」截取首段归组，
     * 空/无分隔符归 null（由 Service 层填充「未知」）；status #1#=在线、其他=离线。
     */
    @Select("SELECT split_part(wlcvig, '-', 1) AS org, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN status = '#1#' THEN 1 ELSE 0 END) AS online " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE type LIKE '%#5#%' " +
            "GROUP BY split_part(wlcvig, '-', 1) " +
            "ORDER BY org")
    List<Map<String, Object>> selectVideoSummary();

    /**
     * 视频通道清单（设备级，全量）：通道号 code、名称 name、设备状态 status、安装位置 wlcvig、
     * 管理所 org（安装位置按「-」截取首段，与视频聚合同口径）、所属站点档案关联信息
     * （主键 site_id / 站名 site_name / 站点编号 stcd / 站点状态 site_status / 站点经纬度 lon·lat /
     * 管理单位 unit_name）、设备自身经纬度 device_lon·device_lat（设备表坐标列，预留未登记时为 null）。
     * <p>site_name 非空时按站点名称 zzkaec 模糊过滤（不传 = 全量，向后兼容）。
     * 站点经纬度取所属站点档案 bviiio_x/y（档案未配置为 null）；站点状态取档案 zebpsu
     * （#1# 在线 / #2# 离线，人工维护值）；站点档案未配置单位时为 null。按安装位置、名称、通道号排序。
     */
    @Select("<script>" +
            "SELECT d.code AS code, d.name AS name, d.status AS status, " +
            "d.wlcvig AS wlcvig, split_part(d.wlcvig, '-', 1) AS org, " +
            "s.id AS site_id, s.zzkaec AS site_name, s.iofhpi AS stcd, " +
            "s.zebpsu AS site_status, " +
            "s.bviiio_x AS lon, s.bviiio_y AS lat, " +
            "d.hkkwwa_x AS device_lon, d.hkkwwa_y AS device_lat, " +
            "u.zzkaec AS unit_name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON d.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" u ON s.ahieto = u.id " +
            "WHERE d.type LIKE '%#5#%' " +
            "<if test='siteName != null and siteName != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{siteName}, '%') </if>" +
            "ORDER BY d.wlcvig, d.name, d.code" +
            "</script>")
    List<Map<String, Object>> selectVideoDevices(@Param("siteName") String siteName);
}
