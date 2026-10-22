package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.qgyun.hltgq.hltgqsite.h5.vo.H5PatrolScheduleVO;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * H5 巡检计划查询与开始巡检写入（t_auto_hltgq_water_patrol_schedule + t_auto_hltgq_water_inspection_record）。
 * <p>基础口径：仅<b>当前登录人被指派</b>（主表 "user"）且「已发布 #2# / 已完成 #3#」的计划
 * （草稿 #1#、已取消 #4# 不入列，他人计划不可见）；任务名称模糊 LIKE；
 * 状态参数按展示态筛选（未开始 #5# / 进行中 #2# / 已完成 #3#），基础白名单恒生效。
 * <p>展示态按<b>巡检记录</b>推导：已发布且该计划尚无巡检记录 = 未开始 #5#；已有巡检记录 = 进行中 #2#；
 * 计划置已完成 #3# 即已完成（人工终态优先）。
 * <p>列表分页 LIMIT/OFFSET（总数与列表同条件，Service 统一裁剪页码与条数）。
 * <p>开始巡检：校验计划状态归属（主表 "user"）后写入一条草稿 #1# 占位记录（仅标记已开始，
 * 站点/设备/结果/内容留空），巡检人强制为当前登录人；同一计划已有记录时不写入（WHERE NOT EXISTS）。
 * 该占位记录即「空白草稿」，后续提交优先填充它（见 PatrolRecordMapper.selectBlankDraftId）。
 * <p>列别名与 VO 属性同名（map-underscore-to-camel-case=false 下依赖同名自动映射）。
 */
public interface H5PatrolScheduleMapper {

    /**
     * 符合条件的计划总数（与列表筛选同条件），用于分页 total/pages。
     * <p>展示态筛选：未开始 #5#（已发布且该计划无巡检记录）、进行中 #2#（已发布且已有巡检记录）、
     * 已完成 #3#（计划已完成）；均叠加在「当前登录人 + 基础白名单」之上。
     */
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" p " +
            "WHERE p.corp_code = 'hltgq' " +
            "AND p.\"user\" = #{userId} " +
            "AND p.status IN ('#2#', '#3#') " +
            "<if test='name != null and name != \"\"'>AND p.title LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='displayStatus == \"#5#\"'>AND p.status = '#2#' AND NOT EXISTS (" +
            "SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = p.id) </if>" +
            "<if test='displayStatus == \"#2#\"'>AND p.status = '#2#' AND EXISTS (" +
            "SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = p.id) </if>" +
            "<if test='displayStatus == \"#3#\"'>AND p.status = '#3#' </if>" +
            "</script>")
    long countScheduleList(@Param("name") String name,
                           @Param("displayStatus") String displayStatus,
                           @Param("userId") String userId);

    /**
     * 巡检计划分页列表：按计划开始时间倒序，id 兜底排序稳定（分页前提）。
     * <p>扩展字段：creatorName 关联用户表 t_apaas_uc_user（p.created_by = u.id，无创建人为 null）；
     * rangeStationIds 取巡查范围关系表 t_auto_hltgq_knc3g_nlbdju_tkwovk_rel 按 biz_id 聚合
     * （string_agg 逗号分隔，按 nature_order 顺序），无范围关系为 null；
     * started 为该计划是否已开始巡检（存在巡检记录）；
     * draftRecordId 为该计划当前空白占位草稿 id（草稿 #1# 且站点/设备/结果/内容全空，取最早一条），
     * 前端提交巡检记录时优先用它更新+提交（幂等），无则 null。
     */
    @Select("<script>" +
            "SELECT p.id AS id, p.code AS code, p.title AS title, p.content AS content, " +
            "p.start_time AS startTime, p.end_time AS endTime, " +
            "p.xhonqv AS planType, p.status AS statusCode, " +
            "CASE WHEN EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = p.id) " +
            "THEN true ELSE false END AS started, " +
            "(SELECT dr.id FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" dr " +
            "WHERE dr.corp_code = 'hltgq' AND dr.patrol_schedule = p.id AND dr.status = '#1#' " +
            "AND dr.site IS NULL AND dr.\"_d_10497_device\" IS NULL " +
            "AND dr.result IS NULL AND dr.content IS NULL " +
            "ORDER BY dr.created_at ASC NULLS LAST, dr.id ASC LIMIT 1) AS draftRecordId, " +
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
            "AND p.\"user\" = #{userId} " +
            "AND p.status IN ('#2#', '#3#') " +
            "<if test='name != null and name != \"\"'>AND p.title LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='displayStatus == \"#5#\"'>AND p.status = '#2#' AND NOT EXISTS (" +
            "SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = p.id) </if>" +
            "<if test='displayStatus == \"#2#\"'>AND p.status = '#2#' AND EXISTS (" +
            "SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" r " +
            "WHERE r.corp_code = 'hltgq' AND r.patrol_schedule = p.id) </if>" +
            "<if test='displayStatus == \"#3#\"'>AND p.status = '#3#' </if>" +
            "ORDER BY p.start_time DESC, p.id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<H5PatrolScheduleVO> selectScheduleList(@Param("name") String name,
                                                @Param("displayStatus") String displayStatus,
                                                @Param("userId") String userId,
                                                @Param("limit") long limit,
                                                @Param("offset") long offset);

    /** 计划是否存在且巡检人员为当前登录人（H5 点位接口归属校验；不存在或他人计划返回 0）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE id = #{scheduleId} AND corp_code = 'hltgq' AND \"user\" = #{userId}")
    long countScheduleOwnedBy(@Param("scheduleId") String scheduleId, @Param("userId") String userId);

    /**
     * 计划存储状态（开始巡检校验）：仅当计划存在且巡检人员为当前登录人时返回 status；
     * 不存在或非本人计划返回 null（Service 统一按“不存在或未指派”处理）。
     */
    @Select("SELECT status FROM \"qixiao-apaas\".\"t_auto_hltgq_water_patrol_schedule\" " +
            "WHERE id = #{scheduleId} AND corp_code = 'hltgq' AND \"user\" = #{userId}")
    String selectOwnedScheduleStatus(@Param("scheduleId") String scheduleId, @Param("userId") String userId);

    /** 计划下最早一条巡检记录 id（开始巡检幂等：已开始则复用原记录，避免重复写入；无记录返回 null）。 */
    @Select("SELECT id FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE corp_code = 'hltgq' AND patrol_schedule = #{scheduleId} " +
            "ORDER BY created_at ASC NULLS LAST, id ASC LIMIT 1")
    String selectFirstRecordId(@Param("scheduleId") String scheduleId);

    /**
     * 开始巡检写入占位记录：状态草稿 #1#（不进入已提交统计），站点/设备/结果/内容留空，
     * 巡检记录时间与审计列取当前时间/当前登录人；同一计划已有记录时不写入（WHERE NOT EXISTS）。
     *
     * @return 实际写入行数（0 = 已有记录，未写入）
     */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "(\"id\", \"corp_code\", \"created_at\", \"created_by\", \"updated_at\", \"updated_by\", " +
            "\"time\", \"patrol_schedule\", \"user\", \"status\") " +
            "SELECT #{recordId}, 'hltgq', #{now}, #{userId}, #{now}, #{userId}, " +
            "#{now}, #{scheduleId}, #{userId}, '#1#' " +
            "WHERE NOT EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_water_inspection_record\" " +
            "WHERE corp_code = 'hltgq' AND patrol_schedule = #{scheduleId})")
    int insertStartRecord(@Param("recordId") String recordId,
                          @Param("scheduleId") String scheduleId,
                          @Param("userId") String userId,
                          @Param("now") LocalDateTime now);

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
