package com.qgyun.hltgq.hltgqsite.stationdetail.mapper;

import com.qgyun.hltgq.hltgqsite.stationdetail.vo.DeviceVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.IssueRecordVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.PatrolDetailVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.PatrolRecordVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.StationBasicVO;
import com.qgyun.hltgq.hltgqsite.stationdetail.vo.WorkOrderVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 站点详情聚合查询（/station-detail）。
 * <p>表均带 schema `qixiao-apaas`，列均加双引号（KingbaseES 大写列名必须引号）；
 * 动态 SQL 走 &lt;script&gt;，字符串拼接用 CONCAT（Kingbase 的 || 按逻辑 OR 语义处理）；
 * 列别名与 VO 属性同名（map-underscore-to-camel-case=false 下 MyBatis 依赖同名忽略大小写映射）；
 * user/desc/level 等关键字列名均需双引号转义。
 * <p>监测表（vol_info/msg_info/gate 等）的 site 列 = 站点档案主键 id（UUID），
 * 故各查询入参 site 一律使用 resolveStationId 解析后的档案 id。
 */
public interface StationDetailMapper {

    // ==================== 站点解析与档案 ====================

    /**
     * 站点键解析：iofhpi（站点编号）与 id（档案主键）双键命中，优先编码命中行。
     * 无命中返回 null（Service 层抛 400）。键全小写（id/iofhpi）。
     */
    @Select("SELECT id, iofhpi " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE iofhpi = #{key} OR id = #{key} " +
            "ORDER BY CASE WHEN iofhpi = #{key} THEN 0 ELSE 1 END LIMIT 1")
    Map<String, Object> selectStationKey(@Param("key") String key);

    /**
     * 站点档案行（按档案 id）：基础信息所需档案字段。
     * <p>列别名与 StationBasicVO 属性同名；bviiio_x/y 经纬度、zebpsu 运行状态、
     * waljdn 是否接通市电、bhsqxd 传输方法（ahieto 存的是管理单位 id 非名称，mivbcz 值同站名，均不取）。
     */
    @Select("SELECT iofhpi AS code, zzkaec AS name, epjutj AS typeCodes, " +
            "bviiio_x AS lon, bviiio_y AS lat, zebpsu AS runStatusCode, " +
            "waljdn AS mainsPowerCode, bhsqxd AS comm " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE id = #{id}")
    StationBasicVO selectStationBasic(@Param("id") String id);

    // ==================== 供电 / 网络 ====================

    /**
     * 电压表最新一行（供电情况）：vol 电压、tm 时间。
     * 电流（via/vib/vic）与信号强度（wc）列库中未确认存在，恒 null 不查；
     * 报文表库中不存在，通信延迟恒 null。键全小写，Java 侧按小写键取值。
     */
    @Select("SELECT vol, tm " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_vol_info " +
            "WHERE site = #{site} ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestVol(@Param("site") String site);

    // ==================== 闸口数量 / 视频通道 ====================

    /** 闸口数量：该站点下闸门类型（type 含 #4#）设备数 */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE site = #{site} AND type LIKE '%#4#%'")
    long countGateDevices(@Param("site") String site);

    /** 视频通道列表：该站点下视频类型（type 含 #5#）设备，按名称升序 */
    @Select("SELECT name AS channel, status AS statusCode " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE site = #{site} AND type LIKE '%#5#%' " +
            "ORDER BY name")
    List<StationBasicVO.VideoChannel> selectVideoChannels(@Param("site") String site);

    // ==================== 巡检记录 ====================

    /**
     * 巡检记录总数（按站点，全状态含草稿；与 selectPatrolPage 同条件）。
     * <p>person 按用户姓名模糊；abnormal=true 仅异常档（#3#~#6#）、false 仅正常（#2#）；
     * hasIssue=true 仅存在关联问题或异常档、false 仅无关联问题且非异常档（派生字段回查问题表）。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON r.\"user\" = u.id " +
            "WHERE r.site = #{site} " +
            "<if test='startTime != null'>AND r.\"time\" &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND r.\"time\" &lt; #{endTime} </if>" +
            "<if test='person != null and person != \"\"'>AND u.name LIKE CONCAT('%', #{person}, '%') </if>" +
            "<if test='abnormal != null and abnormal'>AND r.result IN ('#3#', '#4#', '#5#', '#6#') </if>" +
            "<if test='abnormal != null and !abnormal'>AND r.result = '#2#' </if>" +
            "<if test='hasIssue != null and hasIssue'>" +
            "AND (r.result IN ('#3#', '#4#', '#5#', '#6#') OR EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.abqezf = r.id)) </if>" +
            "<if test='hasIssue != null and !hasIssue'>" +
            "AND r.result NOT IN ('#3#', '#4#', '#5#', '#6#') AND NOT EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.abqezf = r.id) </if>" +
            "</script>")
    long countPatrol(@Param("site") String site,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime,
                     @Param("person") String person,
                     @Param("abnormal") Boolean abnormal,
                     @Param("hasIssue") Boolean hasIssue);

    /**
     * 巡检记录分页：按巡检时间倒序，id 降序兜底分页稳定。
     * <p>记录编号用主键 id（业务表无 code 字段）；hasIssue 为派生布尔列；
     * relatedIssue 为关联问题标题顿号拼接（无则 null）；object 由 Service 解析 device 回填。
     */
    @Select("<script>" +
            "SELECT r.id AS code, r.\"time\", u.name AS person, r.device AS deviceIds, r.content, " +
            "r.result AS resultCode, r.status AS statusCode, " +
            "CASE WHEN r.result IN ('#3#', '#4#', '#5#', '#6#') OR EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.abqezf = r.id) THEN TRUE ELSE FALSE END AS hasIssue, " +
            "(SELECT string_agg(i.title, '、') FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.abqezf = r.id) AS relatedIssue " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON r.\"user\" = u.id " +
            "WHERE r.site = #{site} " +
            "<if test='startTime != null'>AND r.\"time\" &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND r.\"time\" &lt; #{endTime} </if>" +
            "<if test='person != null and person != \"\"'>AND u.name LIKE CONCAT('%', #{person}, '%') </if>" +
            "<if test='abnormal != null and abnormal'>AND r.result IN ('#3#', '#4#', '#5#', '#6#') </if>" +
            "<if test='abnormal != null and !abnormal'>AND r.result = '#2#' </if>" +
            "<if test='hasIssue != null and hasIssue'>" +
            "AND (r.result IN ('#3#', '#4#', '#5#', '#6#') OR EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.abqezf = r.id)) </if>" +
            "<if test='hasIssue != null and !hasIssue'>" +
            "AND r.result NOT IN ('#3#', '#4#', '#5#', '#6#') AND NOT EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i WHERE i.abqezf = r.id) </if>" +
            "ORDER BY r.\"time\" DESC, r.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<PatrolRecordVO> selectPatrolPage(@Param("site") String site,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("person") String person,
                                          @Param("abnormal") Boolean abnormal,
                                          @Param("hasIssue") Boolean hasIssue,
                                          @Param("limit") int limit,
                                          @Param("offset") int offset);

    /**
     * 巡检记录详情（按记录 id）：JOIN 计划标题、用户姓名、站点名称；
     * deviceIds 多选原文由 Service 解析回填 object。
     */
    @Select("SELECT r.id AS code, r.\"time\", r.content AS remark, r.result AS resultCode, r.status AS statusCode, " +
            "r.device AS deviceIds, u.name AS person, p.title AS plan, s.zzkaec AS site " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON r.\"user\" = u.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p ON r.patrol_schedule = p.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON r.site = s.id " +
            "WHERE r.id = #{id}")
    PatrolDetailVO selectPatrolDetail(@Param("id") String id);

    /** 巡检记录关联问题（问题表 abqezf = 巡检记录 id），按发现时间升序 */
    @Select("SELECT i.code, i.title, d.name AS device " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON i.device = d.id " +
            "WHERE i.abqezf = #{recordId} " +
            "ORDER BY i.\"time\"")
    List<PatrolDetailVO.IssueItem> selectPatrolIssues(@Param("recordId") String recordId);

    /** 设备名批量查询（巡检对象 device 多选解析出的 id 列表），键全小写 */
    @Select("<script>" +
            "SELECT id, name FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach>" +
            "</script>")
    List<Map<String, Object>> selectDeviceNamesByIds(@Param("ids") List<String> ids);

    /**
     * 设备名批量回查（巡检对象 device 多选直接存名称时的兑底：按名称命中自身），键全小写。
     */
    @Select("<script>" +
            "SELECT name FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE name IN " +
            "<foreach collection='names' item='n' open='(' separator=',' close=')'>#{n}</foreach>" +
            "</script>")
    List<String> selectDeviceNamesByNames(@Param("names") List<String> names);

    // ==================== 问题记录 ====================

    /**
     * 问题记录总数（按站点，全状态；与 selectIssuePage 同条件）。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON i.\"user\" = u.id " +
            "WHERE i.site = #{site} " +
            "<if test='startTime != null'>AND i.\"time\" &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND i.\"time\" &lt; #{endTime} </if>" +
            "<if test='finder != null and finder != \"\"'>AND u.name LIKE CONCAT('%', #{finder}, '%') </if>" +
            "<if test='handleCode != null'>AND i.handle_status = #{handleCode} </if>" +
            "<if test='statusCode != null'>AND i.status = #{statusCode} </if>" +
            "</script>")
    long countIssue(@Param("site") String site,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime,
                    @Param("finder") String finder,
                    @Param("handleCode") String handleCode,
                    @Param("statusCode") String statusCode);

    /**
     * 问题记录分页：按发现时间倒序，id 降序兜底分页稳定。
     * <p>LEFT JOIN 站点/设备/工单/用户表（关联行被删时本行不丢，名称列 null）；
     * desc 为 PG 关键字，列别名需双引号。
     */
    @Select("<script>" +
            "SELECT i.code, i.title, i.content AS \"desc\", i.\"time\" AS foundAt, " +
            "i.handle_status AS handleCode, i.status AS statusCode, " +
            "u.name AS finder, s.zzkaec AS site, d.name AS device, w.code AS orderNo " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON i.\"user\" = u.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON i.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON i.device = d.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w ON i.work_order_id = w.id " +
            "WHERE i.site = #{site} " +
            "<if test='startTime != null'>AND i.\"time\" &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND i.\"time\" &lt; #{endTime} </if>" +
            "<if test='finder != null and finder != \"\"'>AND u.name LIKE CONCAT('%', #{finder}, '%') </if>" +
            "<if test='handleCode != null'>AND i.handle_status = #{handleCode} </if>" +
            "<if test='statusCode != null'>AND i.status = #{statusCode} </if>" +
            "ORDER BY i.\"time\" DESC, i.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<IssueRecordVO> selectIssuePage(@Param("site") String site,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("finder") String finder,
                                        @Param("handleCode") String handleCode,
                                        @Param("statusCode") String statusCode,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    // ==================== 维修工单 ====================

    /** 维修工单总数（按站点，全状态） */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "WHERE w.site = #{site}")
    long countOrder(@Param("site") String site);

    /**
     * 维修工单分页：按工单 time（要求完成时间）倒序，code 降序兜底。
     * <p>fault = 工单表 content（内容描述）、content = 工单表 result（处理结果）。
     */
    @Select("SELECT w.code, w.\"time\", w.content AS fault, w.result AS content, d.name AS device " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_work_order\" w " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON w.device = d.id " +
            "WHERE w.site = #{site} " +
            "ORDER BY w.\"time\" DESC, w.code DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<WorkOrderVO> selectOrderPage(@Param("site") String site,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    // ==================== 设备信息 ====================

    /**
     * 设备总数（按站点；与 selectDevicePage 同条件）。
     * <p>typeFilter 为类型编码片段（如 '#4#' 闸门），LIKE 匹配支持多类型设备；
     * statusFilter 为状态编码（#1# 在线、#2# 离线）。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d " +
            "WHERE d.site = #{site} " +
            "<if test='code != null and code != \"\"'>AND d.code LIKE CONCAT('%', #{code}, '%') </if>" +
            "<if test='name != null and name != \"\"'>AND d.name = #{name} </if>" +
            "<if test='typeFilter != null'>AND d.type LIKE CONCAT('%', #{typeFilter}, '%') </if>" +
            "<if test='statusFilter != null'>AND d.status = #{statusFilter} </if>" +
            "</script>")
    long countDevice(@Param("site") String site,
                     @Param("code") String code,
                     @Param("name") String name,
                     @Param("typeFilter") String typeFilter,
                     @Param("statusFilter") String statusFilter);

    /**
     * 设备分页：按名称升序（编码兜底）；实时数据与所属闸口由 Service 组装。
     */
    @Select("<script>" +
            "SELECT d.id, d.code, d.name, d.type AS typeCodes, d.status AS statusCode " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d " +
            "WHERE d.site = #{site} " +
            "<if test='code != null and code != \"\"'>AND d.code LIKE CONCAT('%', #{code}, '%') </if>" +
            "<if test='name != null and name != \"\"'>AND d.name = #{name} </if>" +
            "<if test='typeFilter != null'>AND d.type LIKE CONCAT('%', #{typeFilter}, '%') </if>" +
            "<if test='statusFilter != null'>AND d.status = #{statusFilter} </if>" +
            "ORDER BY d.name, d.code " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<DeviceVO> selectDevicePage(@Param("site") String site,
                                    @Param("code") String code,
                                    @Param("name") String name,
                                    @Param("typeFilter") String typeFilter,
                                    @Param("statusFilter") String statusFilter,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    // ----- 实时数据：站点级最新值（各监测表键口径不同，见各方法注释） -----

    /**
     * 闸门最新开度：闸门表按 site + gate_no 区分闸孔（无 device 列），
     * 每闸孔取最新一条（排除站级占位行 gate_no='0' 与负数哨兵值）；
     * 设备行按设备名解析出的闸孔号 gate 与 gate_no 匹配。键全小写。
     */
    @Select("SELECT DISTINCT ON (gate_no) gate_no, TRUNC(open_degree, 2) AS value " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_gate\" " +
            "WHERE site = #{site} AND gate_no <> '0' " +
            "AND open_degree IS NOT NULL AND open_degree >= 0 " +
            "ORDER BY gate_no, tm DESC")
    List<Map<String, Object>> selectLatestGateOpenings(@Param("site") String site);

    /** 水位最新值（2 位小数）：水位表按水文测站编码 STCD 关联（STCD = 档案表 iofhpi），列名大写需引号 */
    @Select("SELECT TRUNC(\"Z\", 2) AS value " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_river_info " +
            "WHERE \"STCD\" = #{stcd} AND \"Z\" IS NOT NULL AND \"Z\" >= 0 " +
            "ORDER BY \"TM\" DESC LIMIT 1")
    Map<String, Object> selectLatestLevel(@Param("stcd") String stcd);

    /** 雨量最新值（DRP 水文日累计，2 位小数）：雨量表按 STCD 关联，列名大写需引号 */
    @Select("SELECT TRUNC(\"DRP\", 2) AS value " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_rain_info " +
            "WHERE \"STCD\" = #{stcd} AND \"DRP\" IS NOT NULL AND \"DRP\" >= 0 " +
            "ORDER BY \"TM\" DESC LIMIT 1")
    Map<String, Object> selectLatestRain(@Param("stcd") String stcd);

    /** 流量最新瞬时流量（3 位小数，口径同闸门监测页）：流量表按 site 关联 */
    @Select("SELECT TRUNC(q, 3) AS value " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" " +
            "WHERE site = #{site} AND q IS NOT NULL AND q >= 0 " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestFlow(@Param("site") String site);

    /** 墒情最新 10cm 含水率（2 位小数）：墒情表 stcd（=iofhpi）或 site 双键命中 */
    @Select("SELECT TRUNC(mten, 2) AS value " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_soil_data " +
            "WHERE (stcd = #{stcd} OR site = #{site}) AND mten IS NOT NULL AND mten >= 0 " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestSoil(@Param("stcd") String stcd, @Param("site") String site);

    /** 水质最新氨氮（3 位小数，口径同水质监测页）：水质表 nmisp_info stcd（=iofhpi）或 site 双键命中 */
    @Select("SELECT TRUNC(nh3n, 3) AS value " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_nmisp_info " +
            "WHERE (stcd = #{stcd} OR site = #{site}) AND nh3n IS NOT NULL AND nh3n >= 0 " +
            "ORDER BY tm DESC LIMIT 1")
    Map<String, Object> selectLatestQuality(@Param("stcd") String stcd, @Param("site") String site);

    // ==================== 筛选下拉选项 ====================

    /** 巡检人员姓名去重（该站点巡检记录关联用户，非空升序） */
    @Select("SELECT DISTINCT u.name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON r.\"user\" = u.id " +
            "WHERE r.site = #{site} AND u.name IS NOT NULL AND u.name <> '' " +
            "ORDER BY u.name")
    List<String> selectPatrolPersons(@Param("site") String site);

    /** 问题发现人姓名去重（该站点问题记录关联用户，非空升序） */
    @Select("SELECT DISTINCT u.name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_bpzjoh\" i " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON i.\"user\" = u.id " +
            "WHERE i.site = #{site} AND u.name IS NOT NULL AND u.name <> '' " +
            "ORDER BY u.name")
    List<String> selectIssueFinders(@Param("site") String site);

    /** 设备名称去重（该站点设备表，非空升序） */
    @Select("SELECT DISTINCT name " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE site = #{site} AND name IS NOT NULL AND name <> '' " +
            "ORDER BY name")
    List<String> selectDeviceNames(@Param("site") String site);
}
