package com.qgyun.hltgq.hltgqsite.watersaving.mapper;

import com.qgyun.hltgq.hltgqsite.watersaving.vo.WaterSavingIndicatorVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 节水体系查询：主表列表（指标名称/年度/状态过滤，按年度倒序）。
 */
public interface WaterSavingIndicatorMapper {

    /**
     * 节水体系列表：年度 ckzbjf 兼容时间戳与文本两种形态，
     * 过滤与排序统一按该列：CAST(x AS VARCHAR) 后 '2026-…' 形态前缀匹配 '2026'。
     */
    @Select("<script>" +
            "SELECT id AS id, iewyyy AS name, CAST(ckzbjf AS VARCHAR) AS yearRaw, sklcff AS target, " +
            "zkcllb AS statusCode, nschzs AS reviewOpinion, updated_at AS updatedAt " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_yn8cm_iiitnh\" " +
            "WHERE corp_code = 'hltgq' " +
            "<if test='name != null and name != \"\"'>AND iewyyy LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='year != null and year != \"\"'>AND CAST(ckzbjf AS VARCHAR) LIKE CONCAT(#{year}, '%') </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND zkcllb = #{statusCode} </if>" +
            "ORDER BY ckzbjf DESC, id DESC" +
            "</script>")
    List<WaterSavingIndicatorVO> selectIndicatorList(@Param("name") String name,
                                                     @Param("year") String year,
                                                     @Param("statusCode") String statusCode);
}
