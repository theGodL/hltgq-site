package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.AlertPageVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警分页查询（全局告警列表）
 */
public interface AlertMapper {

    /**
     * 未关闭告警总数（未关闭即未处理：#1# 未确认、#2# 已确认、#3# 处理中；#4# 已关闭不计），
     * IN 白名单防御 status 为 null/未知编码的脏行。
     * <p>type 筛选（逻辑分类，二选一或都不传）：typeOverlimit=true 仅阈值超限（a.type='#1#'）；
     * typeOther=true 仅非超限（type IS NULL 或 &lt;&gt;'#1#'，即异常告警）；都不传 = 全部。
     * <p>siteTypeFilter 为站点类型编码（如 '#4#' 闸门），LIKE 匹配支持多类型站点（如 '#1#|#4#'）；null = 不筛选。
     * <p>LEFT JOIN 站点表/设备表：站点或设备被删时告警行不丢（名称列返回 null），
     * LEFT JOIN 不改变 COUNT(*) 结果；名称筛选条件依赖关联表，故 COUNT 同样 JOIN。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON a.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON a.device = d.id " +
            "WHERE a.status IN ('#1#', '#2#', '#3#') " +
            "<if test='siteName != null and siteName != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{siteName}, '%') </if>" +
            "<if test='deviceName != null and deviceName != \"\"'>AND d.name LIKE CONCAT('%', #{deviceName}, '%') </if>" +
            "<if test='typeOverlimit != null and typeOverlimit'>AND a.type = '#1#' </if>" +
            "<if test='typeOther != null and typeOther'>AND (a.type IS NULL OR a.type &lt;&gt; '#1#') </if>" +
            "<if test='siteTypeFilter != null'>AND s.epjutj LIKE CONCAT('%', #{siteTypeFilter}, '%') </if>" +
            "<if test='startTime != null'>AND a.time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND a.time &lt;= #{endTime} </if>" +
            "</script>")
    long selectAlertCount(@Param("siteName") String siteName,
                          @Param("deviceName") String deviceName,
                          @Param("typeOverlimit") Boolean typeOverlimit,
                          @Param("typeOther") Boolean typeOther,
                          @Param("siteTypeFilter") String siteTypeFilter,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 未关闭告警分页：按发生时间倒序（最新在前，第一页第一条 = 全库最新未关闭告警），
     * id 降序兜底同一时间戳的分页稳定性。
     * <p>列别名与 AlertPageVO 属性同名（map-underscore-to-camel-case=false 下依赖同名自动映射）；
     * level 为 KingbaseES 层次查询伪列保留字，需双引号转义。
     * <p>type/siteType 筛选与 selectAlertCount 同口径。
     */
    @Select("<script>" +
            "SELECT a.id, a.code, a.site AS siteId, s.zzkaec AS siteName, " +
            "a.device AS deviceId, d.name AS deviceName, " +
            "a.content, a.\"level\", a.status, a.time, a.type, " +
            "s.epjutj AS siteType, s.bviiio_x AS lon, s.bviiio_y AS lat " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON a.site = s.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_device\" d ON a.device = d.id " +
            "WHERE a.status IN ('#1#', '#2#', '#3#') " +
            "<if test='siteName != null and siteName != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{siteName}, '%') </if>" +
            "<if test='deviceName != null and deviceName != \"\"'>AND d.name LIKE CONCAT('%', #{deviceName}, '%') </if>" +
            "<if test='typeOverlimit != null and typeOverlimit'>AND a.type = '#1#' </if>" +
            "<if test='typeOther != null and typeOther'>AND (a.type IS NULL OR a.type &lt;&gt; '#1#') </if>" +
            "<if test='siteTypeFilter != null'>AND s.epjutj LIKE CONCAT('%', #{siteTypeFilter}, '%') </if>" +
            "<if test='startTime != null'>AND a.time &gt;= #{startTime} </if>" +
            "<if test='endTime != null'>AND a.time &lt;= #{endTime} </if>" +
            "ORDER BY a.time DESC, a.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<AlertPageVO> selectAlertPage(@Param("siteName") String siteName,
                                      @Param("deviceName") String deviceName,
                                      @Param("typeOverlimit") Boolean typeOverlimit,
                                      @Param("typeOther") Boolean typeOther,
                                      @Param("siteTypeFilter") String siteTypeFilter,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);
}
