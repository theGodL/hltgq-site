package com.qgyun.hltgq.hltgqsite.mapper;

import com.qgyun.hltgq.hltgqsite.vo.WaterIntakeRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WaterIntakeMapper {

    @Select("<script>" +
            "SELECT COALESCE(b.MP_NM, r.MP_CD) AS mpNm, r.MP_CD AS mpCd, r.TM AS tm, r.HOUR_W AS value " +
            "FROM \"qixiao-apaas\".\"WR_MP_HOURW_R\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"WR_MP_B\" b ON r.MP_CD = b.MP_CD " +
            "WHERE r.MP_CD IN (SELECT MP_CD FROM \"qixiao-apaas\".\"REL_WIUST_MP\" WHERE WIUST_CD = #{wiustCd}) " +
            "<if test='mpCd != null'>AND r.MP_CD = #{mpCd} </if>" +
            "AND r.TM &gt;= #{startTime}::timestamp " +
            "AND r.TM &lt;= #{endTime}::timestamp " +
            "ORDER BY r.TM" +
            "</script>")
    List<WaterIntakeRecordVO> selectHour(@Param("wiustCd") String wiustCd,
                                         @Param("mpCd") String mpCd,
                                         @Param("startTime") String startTime,
                                         @Param("endTime") String endTime);

    @Select("<script>" +
            "SELECT COALESCE(b.MP_NM, r.MP_CD) AS mpNm, r.MP_CD AS mpCd, r.TM AS tm, r.DAY_W AS value " +
            "FROM \"qixiao-apaas\".\"WR_DAY_W_R\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"WR_MP_B\" b ON r.MP_CD = b.MP_CD " +
            "WHERE r.MP_CD IN (SELECT MP_CD FROM \"qixiao-apaas\".\"REL_WIUST_MP\" WHERE WIUST_CD = #{wiustCd}) " +
            "<if test='mpCd != null'>AND r.MP_CD = #{mpCd} </if>" +
            "AND r.TM &gt;= #{startTime}::timestamp " +
            "AND r.TM &lt;= #{endTime}::timestamp " +
            "ORDER BY r.TM" +
            "</script>")
    List<WaterIntakeRecordVO> selectDay(@Param("wiustCd") String wiustCd,
                                        @Param("mpCd") String mpCd,
                                        @Param("startTime") String startTime,
                                        @Param("endTime") String endTime);

    @Select("<script>" +
            "SELECT COALESCE(b.MP_NM, r.MP_CD) AS mpNm, r.MP_CD AS mpCd, " +
            "DATE_TRUNC('month', r.TM) AS tm, SUM(r.DAY_W) AS value " +
            "FROM \"qixiao-apaas\".\"WR_DAY_W_R\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"WR_MP_B\" b ON r.MP_CD = b.MP_CD " +
            "WHERE r.MP_CD IN (SELECT MP_CD FROM \"qixiao-apaas\".\"REL_WIUST_MP\" WHERE WIUST_CD = #{wiustCd}) " +
            "<if test='mpCd != null'>AND r.MP_CD = #{mpCd} </if>" +
            "AND r.TM &gt;= #{startTime}::timestamp " +
            "AND r.TM &lt;= #{endTime}::timestamp " +
            "GROUP BY COALESCE(b.MP_NM, r.MP_CD), r.MP_CD, DATE_TRUNC('month', r.TM) " +
            "ORDER BY DATE_TRUNC('month', r.TM)" +
            "</script>")
    List<WaterIntakeRecordVO> selectMonth(@Param("wiustCd") String wiustCd,
                                          @Param("mpCd") String mpCd,
                                          @Param("startTime") String startTime,
                                          @Param("endTime") String endTime);

    @Select("<script>" +
            "SELECT COALESCE(b.MP_NM, r.MP_CD) AS mpNm, r.MP_CD AS mpCd, " +
            "DATE_TRUNC('year', r.TM) AS tm, SUM(r.DAY_W) AS value " +
            "FROM \"qixiao-apaas\".\"WR_DAY_W_R\" r " +
            "LEFT JOIN \"qixiao-apaas\".\"WR_MP_B\" b ON r.MP_CD = b.MP_CD " +
            "WHERE r.MP_CD IN (SELECT MP_CD FROM \"qixiao-apaas\".\"REL_WIUST_MP\" WHERE WIUST_CD = #{wiustCd}) " +
            "<if test='mpCd != null'>AND r.MP_CD = #{mpCd} </if>" +
            "AND r.TM &gt;= #{startTime}::timestamp " +
            "AND r.TM &lt;= #{endTime}::timestamp " +
            "GROUP BY COALESCE(b.MP_NM, r.MP_CD), r.MP_CD, DATE_TRUNC('year', r.TM) " +
            "ORDER BY DATE_TRUNC('year', r.TM)" +
            "</script>")
    List<WaterIntakeRecordVO> selectYear(@Param("wiustCd") String wiustCd,
                                         @Param("mpCd") String mpCd,
                                         @Param("startTime") String startTime,
                                         @Param("endTime") String endTime);

    @Select("SELECT COALESCE(b.MP_NM, rel.MP_CD) AS mpNm, rel.MP_CD AS mpCd " +
            "FROM \"qixiao-apaas\".\"REL_WIUST_MP\" rel " +
            "LEFT JOIN \"qixiao-apaas\".\"WR_MP_B\" b ON rel.MP_CD = b.MP_CD " +
            "WHERE rel.WIUST_CD = #{wiustCd} " +
            "ORDER BY rel.MP_CD")
    List<WaterIntakeRecordVO> selectPoints(@Param("wiustCd") String wiustCd);

    @Select("SELECT WIUST_NM FROM \"qixiao-apaas\".\"WR_WIUST_B\" WHERE WIUST_CD = #{wiustCd}")
    String selectWiustNm(@Param("wiustCd") String wiustCd);

    @Select("SELECT WIUST_CD AS mpCd, WIUST_NM AS mpNm FROM \"qixiao-apaas\".\"WR_WIUST_B\" ORDER BY WIUST_CD")
    List<WaterIntakeRecordVO> selectAllUnits();
}
