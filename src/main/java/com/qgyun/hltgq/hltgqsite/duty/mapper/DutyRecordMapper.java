package com.qgyun.hltgq.hltgqsite.duty.mapper;

import com.qgyun.hltgq.hltgqsite.duty.vo.DutyRecordVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 值班记录查询：列表（日期/班次时间/单位/人员过滤 + 带班领导姓名联查）。
 */
public interface DutyRecordMapper {

    /**
     * 值班记录列表：按值班日期倒序（id 兜底）。
     * <p>值班日期过滤：yaaebo 兼容时间戳与文本两种形态，用 [当日, 次日) 半开区间字符串比较；
     * 人员过滤同时匹配值班人员多选列 _d_10233_ahygpx 与带班领导 atfzxl；
     * 多选列双路 LIKE（原文 + 解析出的用户 id），兼容 ID/姓名两种存储形态（personId 非空才拼，
     * 避免 PG CONCAT 忽略 NULL 导致 LIKE '%%' 恒真）。
     */
    @Select("<script>" +
            "SELECT d.id AS id, d.yaaebo AS dutyDateRaw, d.lzcjgq AS shift, d.sdcgli AS unit, " +
            "d.atfzxl AS leaderId, u.name AS leader, d.jlailj AS actualStart, d.yuyitx AS actualEnd, " +
            "d.rdammr AS summary, d.ylncfw AS statusCode " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_igahxz\" d " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON d.atfzxl = u.id " +
            "WHERE d.corp_code = 'hltgq' " +
            "<if test='dayStart != null'>AND d.yaaebo &gt;= #{dayStart} AND d.yaaebo &lt; #{dayEnd} </if>" +
            "<if test='shift != null and shift != \"\"'>AND d.lzcjgq LIKE CONCAT('%', #{shift}, '%') </if>" +
            "<if test='unit != null and unit != \"\"'>AND d.sdcgli LIKE CONCAT('%', #{unit}, '%') </if>" +
            "<if test='person != null and person != \"\"'>AND (d._d_10233_ahygpx LIKE CONCAT('%', #{person}, '%') " +
            "<if test='personId != null'>OR d._d_10233_ahygpx LIKE CONCAT('%', #{personId}, '%') </if>" +
            "OR d.atfzxl = #{person} OR d.atfzxl = #{personId}) </if>" +
            "ORDER BY d.yaaebo DESC, d.id DESC" +
            "</script>")
    List<DutyRecordVO> selectDutyList(@Param("dayStart") String dayStart,
                                      @Param("dayEnd") String dayEnd,
                                      @Param("shift") String shift,
                                      @Param("unit") String unit,
                                      @Param("person") String person,
                                      @Param("personId") String personId);

    /** 人员关键词解析为用户 id：命中用户 id 或姓名返回 id，未命中返回 null */
    @Select("SELECT id FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{value} OR name = #{value} LIMIT 1")
    String selectUserId(@Param("value") String value);
}
