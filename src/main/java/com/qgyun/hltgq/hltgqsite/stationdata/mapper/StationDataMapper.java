package com.qgyun.hltgq.hltgqsite.stationdata.mapper;

import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationCoordsVO;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationDataVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 站点数据查询：站点档案表列表（按名称/编号模糊 + 状态编码过滤）、批量经纬度。
 */
public interface StationDataMapper {

    /**
     * 站点列表：按编号升序（id 兜底）。
     * <p>列别名与 VO 属性同名自动映射（map-underscore-to-camel-case=false）。
     * <p>核心指标取 ccnhtm1 / ijzsby1（mq 实时写入的字符串列）；同表的 ccnhtm / ijzsby（double）
     * 是 2026-09-11 前旧程序的历史快照，已停止维护，不得再查。
     */
    @Select("<script>" +
            "SELECT s.id AS id, s.zzkaec AS name, s.iofhpi AS code, s.mivbcz AS location, " +
            "s.bviiio_x AS lon, s.bviiio_y AS lat, s.epjutj AS typeCodes, s.zebpsu AS statusCode, " +
            "s.ccnhtm1 AS waterIndicator, s.ijzsby1 AS rainIndicator, s.nxtggq AS methodCodes, " +
            "s.lhwhuc AS owner, s.cbitue AS phone, s.waljdn AS mainsPowerCode, s.bhsqxd AS comm, " +
            "s.viwmmc AS intro, s.badfhe AS operationNote " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "WHERE s.corp_code = 'hltgq' " +
            "<if test='name != null and name != \"\"'>AND s.zzkaec LIKE CONCAT('%', #{name}, '%') </if>" +
            "<if test='code != null and code != \"\"'>AND s.iofhpi LIKE CONCAT('%', #{code}, '%') </if>" +
            "<if test='statusCode != null and statusCode != \"\"'>AND s.zebpsu = #{statusCode} </if>" +
            "ORDER BY s.iofhpi, s.id" +
            "</script>")
    List<StationDataVO> selectStationList(@Param("name") String name,
                                          @Param("code") String code,
                                          @Param("statusCode") String statusCode);

    /**
     * 批量站点经纬度：按主键 IN 查询（ids 非空由 Service 保证），只返回本企业命中行。
     */
    @Select("<script>" +
            "SELECT s.id AS id, s.bviiio_x AS lon, s.bviiio_y AS lat " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" s " +
            "WHERE s.corp_code = 'hltgq' " +
            "AND s.id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "ORDER BY s.iofhpi, s.id" +
            "</script>")
    List<StationCoordsVO> selectCoordsByIds(@Param("ids") List<String> ids);
}
