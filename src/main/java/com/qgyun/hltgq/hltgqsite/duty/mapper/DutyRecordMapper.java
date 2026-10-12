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
     * <p>读侧新列优先 + 旧列兜底：班次时间 egkivk_min/max 格式化为 HH:mm-HH:mm（跨天加「次日」前缀），
     * 空则回落 lzcjgq 旧文本；单位 cfszln 联单位表取名称，空则回落 sdcgli 旧文本。
     * 值班日期过滤用 to_char 定长文本 [当日, 次日) 半开区间比较（不依赖金仓跨类型隐式转换）。
     * 人员过滤三通道：值班人员关系表 rel_id 命中（新记录）、多选旧列 _d_10233_ahygpx 双路 LIKE
     * （原文 + 解析出的用户 id，兼容 ID/姓名两种存储形态；personId 非空才拼，避免 PG CONCAT
     * 忽略 NULL 导致 LIKE '%%' 恒真）、带班领导 atfzxl。
     */
    @Select("<script>" +
            "SELECT d.id AS id, d.yaaebo AS dutyDateRaw, " +
            "CASE WHEN d.egkivk_min IS NOT NULL AND d.egkivk_max IS NOT NULL " +
            "THEN to_char(d.egkivk_min, 'HH24:MI') || '-' || " +
            "CASE WHEN to_char(d.egkivk_min, 'YYYY-MM-DD') != to_char(d.egkivk_max, 'YYYY-MM-DD') " +
            "THEN '次日' ELSE '' END || to_char(d.egkivk_max, 'HH24:MI') " +
            "ELSE d.lzcjgq END AS shift, " +
            "COALESCE(o.name, d.sdcgli) AS unit, " +
            "d.atfzxl AS leaderId, u.name AS leader, d.jlailj AS actualStart, d.yuyitx AS actualEnd, " +
            "d.rdammr AS summary, d.ylncfw AS statusCode " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_igahxz\" d " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user\" u ON d.atfzxl = u.id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON o.id = d.cfszln " +
            "WHERE d.corp_code = 'hltgq' " +
            "<if test='dayStart != null'>AND to_char(d.yaaebo, 'YYYY-MM-DD') &gt;= #{dayStart} " +
            "AND to_char(d.yaaebo, 'YYYY-MM-DD') &lt; #{dayEnd} </if>" +
            "<if test='shift != null and shift != \"\"'>AND (CASE WHEN d.egkivk_min IS NOT NULL AND d.egkivk_max IS NOT NULL " +
            "THEN to_char(d.egkivk_min, 'HH24:MI') || '-' || " +
            "CASE WHEN to_char(d.egkivk_min, 'YYYY-MM-DD') != to_char(d.egkivk_max, 'YYYY-MM-DD') " +
            "THEN '次日' ELSE '' END || to_char(d.egkivk_max, 'HH24:MI') " +
            "ELSE d.lzcjgq END) LIKE CONCAT('%', #{shift}, '%') </if>" +
            "<if test='unit != null and unit != \"\"'>AND COALESCE(o.name, d.sdcgli) LIKE CONCAT('%', #{unit}, '%') </if>" +
            "<if test='person != null and person != \"\"'>AND (d._d_10233_ahygpx LIKE CONCAT('%', #{person}, '%') " +
            "<if test='personId != null'>OR d._d_10233_ahygpx LIKE CONCAT('%', #{personId}, '%') " +
            "OR EXISTS (SELECT 1 FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_igahxz_ahygpx_rel\" rel " +
            "WHERE rel.biz_id = d.id AND rel.rel_id = #{personId}) </if>" +
            "OR d.atfzxl = #{person} " +
            "<if test='personId != null'>OR d.atfzxl = #{personId} </if>" +
            ") </if>" +
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
