package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolScheduleVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
