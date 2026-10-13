package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolScheduleVO;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * H5 巡检计划查询（t_auto_hltgq_water_patrol_schedule）。
 * <p>基础口径：仅「进行中 #2# / 已完成 #3#」（草稿 #1#、已取消 #4# 不入列）；
 * 任务名称模糊 LIKE；状态参数只做单值细分（Service 已转编码），基础白名单恒生效。
 * <p>列表分页 LIMIT/OFFSET（总数与列表同条件，Service 统一裁剪页码与条数）。
 * <p>列别名与 VO 属性同名（map-underscore-to-camel-case=false 下依赖同名自动映射）。
 */
public interface H5PatrolScheduleMapper {

    /**
     * 符合条件的计划总数（与列表筛选同条件），用于分页 total/pages。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "WHERE p.corp_code = 'hltgq' " +
            "AND p.status IN ('#2#', '#3#') " +
            "<if test='name != null and name != \"\"'>AND p.title LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND p.status = #{statusCode} </if>" +
            "</script>")
    long countScheduleList(@Param("name") String name,
                           @Param("statusCode") String statusCode);

    /**
     * 巡检计划分页列表：按计划开始时间倒序，id 兜底排序稳定（分页前提）。
     * <p>扩展字段：creatorName 关联用户表 t_apaas_uc_user（p.created_by = u.id，无创建人为 null）；
     * rangeStationIds 取巡查范围关系表 t_auto_hltgq_knc3g_nlbdju_tkwovk_rel 按 biz_id 聚合
     * （string_agg 逗号分隔，按 nature_order 顺序），无范围关系为 null。
     */
    @Select("<script>" +
            "SELECT p.id AS id, p.code AS code, p.title AS title, p.content AS content, " +
            "p.start_time AS startTime, p.end_time AS endTime, " +
            "p.xhonqv AS planType, p.status AS statusCode, " +
            "u.name AS creatorName, r.station_ids AS rangeStationIds " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "ON u.id = p.created_by AND u.corp_code = 'hltgq' " +
            "LEFT JOIN (SELECT rx.biz_id AS biz_id, " +
            "string_agg(rx.rel_id, ',' ORDER BY rx.nature_order) AS station_ids " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" rx " +
            "WHERE rx.corp_code = 'hltgq' GROUP BY rx.biz_id) r " +
            "ON r.biz_id = p.id " +
            "WHERE p.corp_code = 'hltgq' " +
            "AND p.status IN ('#2#', '#3#') " +
            "<if test='name != null and name != \"\"'>AND p.title LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND p.status = #{statusCode} </if>" +
            "ORDER BY p.start_time DESC, p.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<H5PatrolScheduleVO> selectScheduleList(@Param("name") String name,
                                                @Param("statusCode") String statusCode,
                                                @Param("limit") long limit,
                                                @Param("offset") long offset);

    /**
     * 计划关联点位批量查询（巡查范围）：当前页计划 id 一次 IN 取回全部关系行，Service 按 scheduleId 分组。
     * <p>点位信息（名称/经纬度）左联站点档案表实时取（关系存在但站点已删时仅 id 可用，名称/坐标为 null）；
     * 行序 biz_id + nature_order（与 rangeStationIds 聚合顺序一致）。
     */
    @Select("<script>" +
            "SELECT rx.biz_id AS scheduleId, rx.rel_id AS id, " +
            "s.zzkaec AS name, s.bviiio_x AS lon, s.bviiio_y AS lat " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_nlbdju_tkwovk_rel\" rx " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "ON s.id = rx.rel_id AND s.corp_code = 'hltgq' " +
            "WHERE rx.corp_code = 'hltgq' AND rx.biz_id IN " +
            "<foreach collection='scheduleIds' item='sid' open='(' separator=',' close=')'>#{sid}</foreach> " +
            "ORDER BY rx.biz_id, rx.nature_order NULLS LAST" +
            "</script>")
    List<ScheduleStationRow> selectScheduleStations(@Param("scheduleIds") List<String> scheduleIds);

    /** 计划-站点关系行（关联点位查询专用，Service 转换分组后即弃） */
    @Data
    class ScheduleStationRow {
        /** 计划 id（biz_id，Service 分组键） */
        private String scheduleId;
        /** 站点档案主键（rel_id） */
        private String id;
        /** 站点名称（站点档案 zzkaec；站点已删为 null） */
        private String name;
        /** 经度（站点档案 bviiio_x；未填坐标为 null） */
        private BigDecimal lon;
        /** 纬度（站点档案 bviiio_y；未填坐标为 null） */
        private BigDecimal lat;
    }
}
