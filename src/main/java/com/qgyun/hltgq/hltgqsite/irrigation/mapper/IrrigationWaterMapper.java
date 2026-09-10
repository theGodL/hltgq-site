package com.qgyun.hltgq.hltgqsite.irrigation.mapper;

import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationIntervalVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 灌溉用水取数 Mapper（流量监测表 t_auto_hltgq_water_wt_nfo）。
 * <p>区间累计口径与流量监测/闸门监测一致：区间累计 = ttf(窗口内末行) − ttf(起点前最近一条 ttf 非空行)，
 * 起点前无基准行时按 0；本方法为灌溉用水专用变体：起点前子查询同样按候选站点过滤
 * （原 selectLatestPerStation 的起点前子查询不按站点过滤，全表扫描代价高）。
 */
@Mapper
public interface IrrigationWaterMapper {

    /**
     * 指定站点集合在时间窗口 [startTime, endTime] 内的末行总累计与起点前基准。
     * <p>站点标识 skey = COALESCE(stcd, site)：老站点用编号，MQTT 站点回退到 site（UUID），
     * 入参候选标识对 f.stcd / f.site 双列匹配（任一命中）。
     *
     * @param stcds     候选站点标识列表（stcd 或 site UUID，非空）
     * @param startTime 窗口起点（区间累计基准时间，必填）
     * @param endTime   窗口终点（必填）
     * @return 每个命中站点一行（窗口内末行），未命中站点不返回
     */
    @Select("<script>" +
            "SELECT DISTINCT ON (t.skey) t.skey AS site, t.stnm, t.tm, t.ttf, fq_prev.prev_ttf " +
            "FROM ( " +
            "  SELECT COALESCE(f.stcd, f.site) AS skey, COALESCE(s.zzkaec, f.stcd, f.site) AS stnm, f.tm, f.ttf " +
            "  FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "  LEFT JOIN \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s ON f.site = s.id " +
            "  WHERE (f.stcd IN " +
            "  <foreach collection='stcds' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "   OR f.site IN " +
            "  <foreach collection='stcds' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "  ) " +
            "  AND f.tm &gt;= #{startTime} AND f.tm &lt;= #{endTime} " +
            ") t " +
            "LEFT JOIN ( " +
            "  SELECT DISTINCT ON (t2.skey) t2.skey, t2.ttf AS prev_ttf " +
            "  FROM ( " +
            "    SELECT COALESCE(f.stcd, f.site) AS skey, f.tm, f.ttf " +
            "    FROM \"qixiao-apaas\".\"t_auto_hltgq_water_wt_nfo\" f " +
            "    WHERE f.ttf IS NOT NULL AND f.tm &lt; #{startTime} " +
            "    AND (f.stcd IN " +
            "    <foreach collection='stcds' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "     OR f.site IN " +
            "    <foreach collection='stcds' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "    ) " +
            "  ) t2 " +
            "  ORDER BY t2.skey, t2.tm DESC " +
            ") fq_prev ON fq_prev.skey = t.skey " +
            "ORDER BY t.skey, t.tm DESC" +
            "</script>")
    @Results({
            @Result(column = "site", property = "site"),
            @Result(column = "stnm", property = "stnm"),
            @Result(column = "tm", property = "tm"),
            @Result(column = "ttf", property = "ttf"),
            @Result(column = "prev_ttf", property = "prevTtf")
    })
    List<IrrigationIntervalVO> selectIntervalPerStation(
            @Param("stcds") List<String> stcds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
