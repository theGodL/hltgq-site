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
 * <p>监测指标列 {@code zb}：多指标类型（水质 #8# / 墒情 #7#）一个类型下含多个指标，
 * 列表如实输出该列，站点候选的「已配置」标记在传入指标时按「类型 + 指标」判定。
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
            "SELECT t.id, t.site, t.\"zb\" AS indicator, " +
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
            @Result(column = "indicator", property = "indicator"),
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
     * <p>水位指标特判：general（通用水位）只列闸门表无有效上下游水位的水位站
     * （渠道/河道/水库站，含 #5# 但无闸门水位的支渠、渡槽不误伤）；
     * up_z/down_z（闸前/闸后水位）不要求档案含 #1#（闸门站多为 #3#|#4#|#5#），
     * 按闸门表该列是否存在有效数据筛选。
     *
     * @param typeCode  阈值类型编码（必填，如 #1#）
     * @param keyword   站点名称/编号关键字过滤（可选）
     * @param indicator 监测指标编码（可选）：传值时「已配置」标记按「类型 + 指标」判定
     *                  （多指标类型如水质/墒情必须传，否则一个类型下的多个指标会互相误标；
     *                  水位通用水位传 general，与 zb 为空等价）；不传时按类型判定
     */
    @Select("<script>" +
            "SELECT s.id AS site_id, s.zzkaec AS name, s.iofhpi AS site_code, " +
            "(x.site IS NOT NULL) AS configured " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "LEFT JOIN ( " +
            "  SELECT DISTINCT \"site\" FROM \"qixiao-apaas\".t_auto_hltgq_water_threshold " +
            "  WHERE COALESCE(NULLIF(\"zvieyb\", ''), \"type\", '') LIKE CONCAT('%', #{typeCode}, '%') " +
            "  <choose>" +
            "    <when test='\"general\".equals(indicator)'>AND COALESCE(\"zb\", '') = '' </when>" +
            "    <when test='indicator != null'>AND \"zb\" = #{indicator} </when>" +
            "  </choose>" +
            ") x ON x.\"site\" = s.id " +
            "<choose>" +
            "  <when test='\"up_z\".equals(indicator)'>" +
            "  WHERE EXISTS (SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "    WHERE g.\"site\" = s.id AND g.\"up_z\" IS NOT NULL AND g.\"up_z\" NOT IN (-999, -9991)) " +
            "  </when>" +
            "  <when test='\"down_z\".equals(indicator)'>" +
            "  WHERE EXISTS (SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "    WHERE g.\"site\" = s.id AND g.\"down_z\" IS NOT NULL AND g.\"down_z\" NOT IN (-999, -9991)) " +
            "  </when>" +
            "  <when test='\"general\".equals(indicator)'>" +
            "  WHERE s.epjutj LIKE '%#1#%' " +
            "  AND NOT EXISTS (SELECT 1 FROM \"qixiao-apaas\".t_auto_hltgq_water_gate g " +
            "    WHERE g.\"site\" = s.id " +
            "    AND ((g.\"up_z\" IS NOT NULL AND g.\"up_z\" NOT IN (-999, -9991)) " +
            "      OR (g.\"down_z\" IS NOT NULL AND g.\"down_z\" NOT IN (-999, -9991)))) " +
            "  </when>" +
            "  <otherwise>" +
            "  WHERE s.epjutj LIKE CONCAT('%', #{typeCode}, '%') " +
            "  </otherwise>" +
            "</choose>" +
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
                                              @Param("keyword") String keyword,
                                              @Param("indicator") String indicator);

    /**
     * 闸门表该站是否存在有效闸前水位（用于水位·闸前水位阈值的站点校验）。
     * <p>有效值口径与闸门监测页一致：非空且非 -999（设备不存在）/ -9991（设备异常）。
     */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".t_auto_hltgq_water_gate " +
            "WHERE \"site\" = #{siteId} AND \"up_z\" IS NOT NULL AND \"up_z\" NOT IN (-999, -9991)")
    int countEffectiveUpZ(@Param("siteId") String siteId);

    /** 闸门表该站是否存在有效闸后水位（口径同 {@link #countEffectiveUpZ}） */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".t_auto_hltgq_water_gate " +
            "WHERE \"site\" = #{siteId} AND \"down_z\" IS NOT NULL AND \"down_z\" NOT IN (-999, -9991)")
    int countEffectiveDownZ(@Param("siteId") String siteId);

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
