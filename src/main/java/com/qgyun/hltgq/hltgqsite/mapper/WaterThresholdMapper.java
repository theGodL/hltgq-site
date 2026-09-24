package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsite.entity.WaterThreshold;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdSiteVO;
import com.qgyun.hltgq.hltgqsite.vo.ThresholdVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 阈值设置表 Mapper。
 * <p>阈值类型列口径：新配置写 {@code zvieyb}，历史列 {@code type} 仅在读取时兜底，
 * 故所有按类型的过滤与去重统一使用
 * {@code COALESCE(NULLIF("zvieyb", ''), "type", '') LIKE '%#N#%'}——与全库
 * 监测类型 LIKE 子串匹配规范一致（阈值类型为多值格式，如 #1#|#3# 也算已配置）。
 * <p>模糊匹配必须写 {@code LIKE CONCAT('%', #{x}, '%')}：实测 KingbaseES 下
 * {@code LIKE '%' || #{x} || '%'} 的绑定参数会静默失效（退化为匹配全部，2026-09-24 只读核对），
 * 全库既有 SQL 亦统一使用 CONCAT 写法。
 */
@Mapper
public interface WaterThresholdMapper extends BaseMapper<WaterThreshold> {

    /**
     * 阈值列表分页（站点名称/编号由站点档案一次补齐，档案缺失时站名回退站点主键）。
     *
     * @param siteId   站点档案主键过滤（可选）
     * @param typeCode 阈值类型编码过滤（可选，如 #1#）
     * @param keyword  站点名称/编号关键字过滤（可选）
     */
    @Select("<script>" +
            "SELECT t.id, t.site, " +
            "COALESCE(NULLIF(t.\"zvieyb\", ''), t.\"type\", '') AS threshold_type, " +
            "t.\"alarmdir\" AS alarm_dir, t.threshold, t.remark AS description, t.updated_at, " +
            "COALESCE(s.zzkaec, t.site) AS site_name, s.iofhpi AS site_code " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_threshold t " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = t.site " +
            "WHERE 1=1 " +
            "<if test='siteId != null'>AND t.site = #{siteId} </if>" +
            "<if test='typeCode != null'>" +
            "AND COALESCE(NULLIF(t.\"zvieyb\", ''), t.\"type\", '') LIKE CONCAT('%', #{typeCode}, '%') " +
            "</if>" +
            "<if test='keyword != null'>" +
            "AND (s.zzkaec LIKE CONCAT('%', #{keyword}, '%') " +
            "OR COALESCE(s.iofhpi, '') LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY t.updated_at DESC NULLS LAST, t.id " +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "site", property = "site"),
            @Result(column = "threshold_type", property = "thresholdType"),
            @Result(column = "alarm_dir", property = "alarmDir"),
            @Result(column = "threshold", property = "threshold"),
            @Result(column = "description", property = "description"),
            @Result(column = "updated_at", property = "updatedAt"),
            @Result(column = "site_name", property = "siteName"),
            @Result(column = "site_code", property = "siteCode")
    })
    IPage<ThresholdVO> selectPageWithSite(IPage<ThresholdVO> page,
                                          @Param("siteId") String siteId,
                                          @Param("typeCode") String typeCode,
                                          @Param("keyword") String keyword);

    /**
     * 阈值类型下的站点候选：站点档案中监测类型（epjutj）含该编码的站点，
     * 并标记该站点在该类型下是否已配置阈值（界面提示"已配置，请直接编辑"）。
     *
     * @param typeCode 阈值类型编码（必填，如 #1#）
     * @param keyword  站点名称/编号关键字过滤（可选）
     */
    @Select("<script>" +
            "SELECT s.id AS site_id, s.zzkaec AS name, s.iofhpi AS site_code, " +
            "(x.site IS NOT NULL) AS configured " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "LEFT JOIN ( " +
            "  SELECT DISTINCT \"site\" FROM \"qixiao-apaas\".t_auto_hltgq_water_threshold " +
            "  WHERE COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') LIKE CONCAT('%', #{typeCode}, '%') " +
            ") x ON x.\"site\" = s.id " +
            "WHERE s.epjutj LIKE CONCAT('%', #{typeCode}, '%') " +
            "<if test='keyword != null'>" +
            "AND (s.zzkaec LIKE CONCAT('%', #{keyword}, '%') " +
            "OR COALESCE(s.iofhpi, '') LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY s.zzkaec " +
            "</script>")
    @Results({
            @Result(column = "site_id", property = "siteId"),
            @Result(column = "name", property = "name"),
            @Result(column = "site_code", property = "siteCode"),
            @Result(column = "configured", property = "configured")
    })
    List<ThresholdSiteVO> selectCandidateSites(@Param("typeCode") String typeCode,
                                              @Param("keyword") String keyword);

    /**
     * 已配置阈值的站点清单（列表筛选下拉用）：阈值表中出现过的站点去重，
     * 站名/编号由站点档案补齐（档案缺失时站名回退站点主键），按站名排序。
     * <p>只列已有配置的站点：筛选项与本页数据同源，选中后必有结果；不区分类型，
     * 与「阈值类型」下拉为独立条件（同时选中即为交集）。
     */
    @Select("SELECT DISTINCT t.\"site\" AS site_id, " +
            "COALESCE(s.zzkaec, t.\"site\") AS name, s.iofhpi AS site_code " +
            "FROM \"qixiao-apaas\".t_auto_hltgq_water_threshold t " +
            "LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON s.id = t.\"site\" " +
            "WHERE t.\"site\" IS NOT NULL " +
            "ORDER BY name")
    @Results({
            @Result(column = "site_id", property = "siteId"),
            @Result(column = "name", property = "name"),
            @Result(column = "site_code", property = "siteCode")
    })
    List<ThresholdSiteVO> selectConfiguredSites();
}
