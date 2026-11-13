package com.qgyun.hltgq.hltgqsite.sms.mapper;

import com.qgyun.hltgq.hltgqsite.sms.vo.SmsCompensationAlertVO;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsRecordPageVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 短信模块持久层：发送记录（自建表 t_auto_hltgq_water_sms_record）与取数源
 * （站点档案 / 雨量表 / 水位表 / 告警表 / 阈值表）。
 * <p>雨量表口径：DRP 为水文日累计值（见 StPptnRMapper 注释），日汇总取
 * "TM <= 08:00 最新一条"即上一水文日完整累计；水文日归属用
 * {@code (TM - 8h - 1s)::date + 32h} 判断（与全项目雨情口径一致）。
 * <p>水位表口径：Z 哨兵值 -999（设备不存在）/ -9991（设备异常）不参与取数。
 * <p>KingbaseES 注意：非 &lt;script&gt; 注解 SQL 直接写 &gt;= / &lt;=（写 &amp;gt; 会报列不存在）。
 */
public interface SmsRecordMapper {

    String T_RECORD = "\"qixiao-apaas\".t_auto_hltgq_water_sms_record";
    String T_STATION = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";
    String T_RAIN = "\"qixiao-apaas\".t_auto_hltgq_water_rain_info";
    String T_RIVER = "\"qixiao-apaas\".t_auto_hltgq_water_river_info";
    String T_GATE = "\"qixiao-apaas\".t_auto_hltgq_water_gate";
    String T_ALERT = "\"qixiao-apaas\".\"t_auto_hltgq_water_alert\"";
    String T_DEVICE = "\"qixiao-apaas\".\"t_auto_hltgq_water_device\"";
    String T_THRESHOLD = "\"qixiao-apaas\".t_auto_hltgq_water_threshold";

    /** 阈值指标表达式：#1#=水位 / #2#=雨量 / #3#=流量…（zvieyb 新列优先，type 历史列兜底，空串转 NULL） */
    String THRESHOLD_TYPE_EXPR = "COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '')";

    // ==================== 发送记录 ====================

    /**
     * 落一条发送记录（成功/失败/静默跳过都落）。
     * row key：id/biz_type/alert_id/silence_key/phone/template_code/template_params/
     * send_status/request_id/biz_id/response_code/response_message/sent_at/now。
     */
    @Insert("INSERT INTO " + T_RECORD + " " +
            "(\"id\", \"biz_type\", \"alert_id\", \"silence_key\", \"phone\", \"template_code\", " +
            "\"template_params\", \"send_status\", \"request_id\", \"biz_id\", \"response_code\", " +
            "\"response_message\", \"sent_at\", \"corp_code\", \"created_at\", \"created_by\", " +
            "\"updated_at\", \"updated_by\") " +
            "VALUES (#{row.id}, #{row.biz_type}, #{row.alert_id}, #{row.silence_key}, #{row.phone}, " +
            "#{row.template_code}, #{row.template_params}, #{row.send_status}, #{row.request_id}, " +
            "#{row.biz_id}, #{row.response_code}, #{row.response_message}, #{row.sent_at}, " +
            "'hltgq', #{row.now}, 'sms-service', #{row.now}, 'sms-service')")
    int insertRecord(@Param("row") Map<String, Object> row);

    /**
     * 同一告警是否已处理过（success=已发送 / skipped=静默跳过）。
     * <p>幂等与补偿均按"已处理"跳过：静默告警落 skipped 记录后不会被补偿在静默过期后延迟补发；
     * failed 记录仍可被补偿重发。
     */
    @Select("SELECT COUNT(*) FROM " + T_RECORD +
            " WHERE alert_id = #{alertId} AND send_status IN ('success', 'skipped')")
    long countHandledByAlertId(@Param("alertId") String alertId);

    /** 静默窗口内该站点该指标是否已成功发送（同站同指标 30 分钟不重发） */
    @Select("SELECT COUNT(*) FROM " + T_RECORD +
            " WHERE silence_key = #{silenceKey} AND send_status = 'success' AND sent_at >= #{since}")
    long countRecentSilence(@Param("silenceKey") String silenceKey, @Param("since") LocalDateTime since);

    /** 记录分页（运维排查，时间倒序） */
    @Select("<script>" +
            "SELECT r.id AS id, r.biz_type AS bizType, r.alert_id AS alertId, r.phone AS phone, " +
            "r.template_code AS templateCode, r.template_params AS templateParams, " +
            "r.send_status AS sendStatus, r.response_code AS responseCode, r.response_message AS responseMessage, " +
            "to_char(r.sent_at, 'YYYY-MM-DD HH24:MI:SS') AS sentAt, " +
            "to_char(r.created_at, 'YYYY-MM-DD HH24:MI:SS') AS createdAt " +
            "FROM " + T_RECORD + " r WHERE 1=1 " +
            "<if test='bizType != null and bizType != \"\"'>AND r.biz_type = #{bizType} </if>" +
            "<if test='phone != null and phone != \"\"'>AND r.phone = #{phone} </if>" +
            "<if test='startTime != null'>AND r.created_at &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND r.created_at &lt;= #{endTime} </if>" +
            "ORDER BY r.created_at DESC, r.id DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<SmsRecordPageVO> selectRecordPage(@Param("bizType") String bizType,
                                           @Param("phone") String phone,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("size") int size,
                                           @Param("offset") long offset);

    /** 记录分页总数（条件与 selectRecordPage 一致） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM " + T_RECORD + " r WHERE 1=1 " +
            "<if test='bizType != null and bizType != \"\"'>AND r.biz_type = #{bizType} </if>" +
            "<if test='phone != null and phone != \"\"'>AND r.phone = #{phone} </if>" +
            "<if test='startTime != null'>AND r.created_at &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND r.created_at &lt;= #{endTime} </if>" +
            "</script>")
    long countRecordPage(@Param("bizType") String bizType,
                         @Param("phone") String phone,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);

    // ==================== 取数源 ====================

    /** 站点名称（按站号；查不到返回 null，调用方回退用站号） */
    @Select("SELECT s.zzkaec FROM " + T_STATION + " s WHERE s.iofhpi = #{stcd} LIMIT 1")
    String selectStationName(@Param("stcd") String stcd);

    /** 雨量站总数（档案监测类型含 #2# 的 320640 站，当前 12 站）：日汇总固定分母与"等报文齐"的期望值 */
    @Select("SELECT COUNT(*) FROM " + T_STATION + " s " +
            "WHERE s.epjutj LIKE '%#2#%' AND s.iofhpi LIKE '320640%'")
    int countRainStations();

    /**
     * 指定整点（TM = endTm）已有入库报文的雨量站数——日汇总发送前"等 08:00 整点报文齐"用。
     * <p>背景（2026-11-13 实测入库时间）：12 站 08:00 整点报文从 08:00:03 陆续入库到 08:06:29，
     * 老系统 08:02~08:05 结算时缺一条 0.5（4.63=55.5/12）；我们改为等报文齐再发，保证发完整值。
     * <p>返回 = {@link #countRainStations()} 即全部到齐。
     */
    @Select("SELECT COUNT(*) FROM " + T_STATION + " s " +
            "WHERE s.epjutj LIKE '%#2#%' AND s.iofhpi LIKE '320640%' " +
            "AND EXISTS (SELECT 1 FROM " + T_RAIN + " t " +
            "            WHERE t.STCD = s.iofhpi AND t.deleted IS NOT TRUE AND t.TM = #{endTm})")
    int countRainStationsWithReportAt(@Param("endTm") LocalDateTime endTm);

    /**
     * 雨量：站点的 TM &lt;= endTm 最新一条，且该行属于 endTm 标记的水文日
     * （endTm=今日 08:00 → 返回上一水文日完整累计 DRP；昨日无上报行返回 null → 按 0 计）。
     * <p>注意：该表 DRP 字段实测恒为 0（2026-11-13 全站核验），日汇总雨量改用
     * {@link #selectHydroDayRainAllStations} 的 DYP 差值口径；本方法仅留作兼容。
     */
    @Select("SELECT x.stcd AS stcd, x.tm AS tm, x.drp AS drp FROM (" +
            "  SELECT t.STCD AS stcd, t.TM AS tm, t.DRP AS drp, " +
            "         (t.TM - INTERVAL '8 hours' - INTERVAL '1 second')::date + INTERVAL '32 hours' AS hydroday " +
            "  FROM " + T_RAIN + " t " +
            "  WHERE t.STCD = #{stcd} AND t.deleted IS NOT TRUE AND t.TM <= #{endTm} " +
            "  ORDER BY t.TM DESC LIMIT 1" +
            ") x WHERE x.hydroday = CAST(#{endTm} AS timestamp)")
    Map<String, Object> selectRainLatestUpTo(@Param("stcd") String stcd,
                                             @Param("endTm") LocalDateTime endTm);

    /**
     * 日汇总雨量（完全复刻老系统公式）：320640 系列监测类型含 #2# 的全部站点<b>固定 12 站</b>，
     * 以站档案为主表 LEFT JOIN 雨量聚合，<b>保证每站恒定出一行</b>，无任何报文的站 rain=0、
     * rowsCnt=0（分母固定 12，缺报文按 0 参与，与老系统页面"空值按 0、分母不变"一致）。
     * <p>单站雨量 = Σ max(0, DYP_i − DYP_{i−1})，窗口 = 基线行（TM ≤ baseTm 最新一条）+ (baseTm, endTm] 全部报文；
     * baseTm=昨日 08:00（上一水文日起点）、endTm=今日 08:00。
     * <p>与首尾差法的区别：DYP 回退（换设备归零/修正）不计负值，与老系统 dailyDyp 逻辑一致；
     * 站点区间内只有基线行时 rain=0（无雨）。
     * <p>返回列：stcd/stnm/rain（Σ 正向增量）/rowsCnt（窗口含基线行数，=1 表示区间内无上报）。
     * <p>KingbaseES：DISTINCT ON 不能直接出现在 UNION ALL 分支，须套子查询。
     */
    @Select("SELECT s.iofhpi AS \"stcd\", s.zzkaec AS \"stnm\", " +
            " COALESCE(a.rain, 0) AS \"rain\", COALESCE(a.rowsCnt, 0) AS \"rowsCnt\" " +
            "FROM " + T_STATION + " s " +
            "LEFT JOIN ( " +
            "  SELECT x.STCD AS \"STCD\", " +
            "   COALESCE(SUM(CASE WHEN x.prev IS NULL THEN 0 WHEN x.DYP - x.prev < 0 THEN 0 ELSE x.DYP - x.prev END), 0) AS \"rain\", " +
            "   COUNT(x.TM) AS \"rowsCnt\" " +
            "  FROM ( " +
            "    SELECT w.STCD, w.TM, w.DYP, LAG(w.DYP) OVER (PARTITION BY w.STCD ORDER BY w.TM) AS prev " +
            "    FROM ( " +
            "      SELECT t.STCD, t.TM, t.DYP FROM " + T_RAIN + " t " +
            "      WHERE t.deleted IS NOT TRUE AND t.TM > #{baseTm} AND t.TM <= #{endTm} " +
            "      UNION ALL " +
            "      SELECT b.STCD, b.TM, b.DYP FROM ( " +
            "        SELECT DISTINCT ON (STCD) STCD, TM, DYP FROM " + T_RAIN + " " +
            "        WHERE deleted IS NOT TRUE AND TM <= #{baseTm} ORDER BY STCD, TM DESC " +
            "      ) b " +
            "    ) w " +
            "  ) x " +
            "  GROUP BY x.STCD " +
            ") a ON a.STCD = s.iofhpi " +
            "WHERE s.epjutj LIKE '%#2#%' AND s.iofhpi LIKE '320640%' " +
            "ORDER BY s.iofhpi")
    List<Map<String, Object>> selectHydroDayRainAllStations(@Param("baseTm") LocalDateTime baseTm,
                                                            @Param("endTm") LocalDateTime endTm);

    /** 水位：站点 TM &lt;= before 的最新有效水位（与全站最新口径一致：COALESCE(Z, Z1)，排除哨兵值） */
    @Select("SELECT r.STCD AS stcd, r.TM AS tm, COALESCE(r.Z, r.Z1) AS z " +
            "FROM " + T_RIVER + " r " +
            "WHERE r.STCD = #{stcd} AND r.TM <= #{before} " +
            "AND COALESCE(r.Z, r.Z1) NOT IN (-999, -9991) " +
            "ORDER BY r.TM DESC LIMIT 1")
    Map<String, Object> selectRiverLatestUpTo(@Param("stcd") String stcd,
                                              @Param("before") LocalDateTime before);

    /**
     * 闸站水位（河道站回退口径）：闸前水位 up_z，TM &lt;= before 最新一个时刻（同刻多孔取 MAX）。
     * <p>背景（2026-11-13 太宿告警实证）：闸站（90000000xx 等）在 river_info 无任何行，
     * 闸前/闸后水位落本表 up_z/down_z；告警引擎按阈值表 zb 字段取数判定，短信须同字段取数。
     * <p>必须按 site 关联：gate 表约 39% 行 STCD 为 NULL（393770 行中 154443 行），不能按 stcd 查。
     */
    @Select("SELECT g.TM AS tm, MAX(g.up_z) AS z " +
            "FROM " + T_GATE + " g " +
            "WHERE g.site = #{siteId} AND g.TM <= #{before} " +
            "AND g.up_z IS NOT NULL AND g.up_z NOT IN (-999, -9991) " +
            "GROUP BY g.TM ORDER BY g.TM DESC LIMIT 1")
    Map<String, Object> selectGateUpZLatestBySite(@Param("siteId") String siteId,
                                                  @Param("before") LocalDateTime before);

    /** 闸站闸后水位 down_z（阈值表 zb=down_z 的站，如渠首进水闸 QSJSZ；口径同上） */
    @Select("SELECT g.TM AS tm, MAX(g.down_z) AS z " +
            "FROM " + T_GATE + " g " +
            "WHERE g.site = #{siteId} AND g.TM <= #{before} " +
            "AND g.down_z IS NOT NULL AND g.down_z NOT IN (-999, -9991) " +
            "GROUP BY g.TM ORDER BY g.TM DESC LIMIT 1")
    Map<String, Object> selectGateDownZLatestBySite(@Param("siteId") String siteId,
                                                    @Param("before") LocalDateTime before);

    /** 站点档案 id（=阈值表/gate 表的 site 关联键）；查不到返回 null */
    @Select("SELECT s.id FROM " + T_STATION + " s WHERE s.iofhpi = #{stcd} LIMIT 1")
    String selectStationIdByStcd(@Param("stcd") String stcd);

    /**
     * 该站水位阈值行配置的监测字段（zb）：up_z=闸前 / down_z=闸后 / 空或 Z=河道 Z。
     * <p>告警引擎（阈值表 site + zvieyb=#1# + zb + threshold）按该字段取实时值判定，
     * 短信"当前水位/1 小时涨幅"必须与告警引擎同字段，否则会出现"告警了但短信暂无/风马牛不相及"。
     * <p>同站多行时优先 up_z，其次最近创建；无水位阈值行返回 null（走河道口径）。
     */
    @Select("SELECT COALESCE(NULLIF(t.zb, ''), '') AS zb FROM " + T_THRESHOLD + " t " +
            "WHERE t.site = #{siteId} " +
            "AND COALESCE(NULLIF(t.\"zvieyb\", ''), t.\"type\", '') = '#1#' " +
            "ORDER BY CASE WHEN t.zb = 'up_z' THEN 0 ELSE 1 END, t.created_at DESC LIMIT 1")
    String selectLevelThresholdField(@Param("siteId") String siteId);

    /**
     * 补偿扫描候选：时间窗内未关闭的阈值告警（type='#1#'），带站点/设备信息。
     * <p>状态口径（2026-11-13 库内实证）：'#1#'=活动中；'#4#'=已解除（不补发）；
     * '#2#' 语义未确认（历史数据见长期挂起两个月的记录，疑似"已确认/已处理"），
     * 按宁可少发不误发排除，只补 '#1#'（'#3#' 留作未来其他未关闭态）。
     * <p>是否发过/是否可判定指标由 Service 逐条过滤（补发兜底，宁可少发不误发）。
     */
    @Select("SELECT a.id AS alertId, a.time AS alertTime, a.site AS siteId, " +
            "s.iofhpi AS stcd, s.zzkaec AS siteName, d.name AS deviceName " +
            "FROM " + T_ALERT + " a " +
            "JOIN " + T_STATION + " s ON s.id = a.site " +
            "LEFT JOIN " + T_DEVICE + " d ON d.id = a.device " +
            "WHERE a.type = '#1#' AND a.status IN ('#1#', '#3#') " +
            "AND a.time >= #{start} " +
            "ORDER BY a.time DESC LIMIT 50")
    List<SmsCompensationAlertVO> selectCompensationAlerts(@Param("start") LocalDateTime start);

    /** 站点雨量阈值（补偿时模板 ${threshold} 取值；无配置返回 null → 该条跳过） */
    @Select("SELECT th.threshold FROM " + T_THRESHOLD + " th " +
            "WHERE th.site = #{siteId} " +
            "AND " + THRESHOLD_TYPE_EXPR + " LIKE '%#2#%' " +
            "ORDER BY th.updated_at DESC LIMIT 1")
    java.math.BigDecimal selectRainThreshold(@Param("siteId") String siteId);

    /**
     * 站点阈值行统计（补偿指标推定）：total=全部行数，rain=含 #2# 行数，level=含 #1# 行数。
     * <p>仅当 total 与 rain/level 之一相等（全部行均为单一指标）才可推定指标，
     * 含流量/开度等其他指标或一行多值一律跳过（宁可少发不误发）。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN " + THRESHOLD_TYPE_EXPR + " LIKE '%#2#%' THEN 1 ELSE 0 END), 0) AS rain, " +
            "COALESCE(SUM(CASE WHEN " + THRESHOLD_TYPE_EXPR + " LIKE '%#1#%' THEN 1 ELSE 0 END), 0) AS level " +
            "FROM " + T_THRESHOLD + " th WHERE th.site = #{siteId}")
    Map<String, Object> countThresholdTypes(@Param("siteId") String siteId);
}
