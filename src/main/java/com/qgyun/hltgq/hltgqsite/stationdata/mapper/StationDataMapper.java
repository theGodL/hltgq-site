package com.qgyun.hltgq.hltgqsite.stationdata.mapper;

import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationDataVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 站点数据查询：站点档案表列表（按名称/编号模糊 + 状态编码过滤）。
 */
public interface StationDataMapper {

    /**
     * 站点列表：按编号升序（id 兜底）。
     * <p>列别名与 VO 属性同名自动映射（map-underscore-to-camel-case=false）。
     */
    @Select("<script>" +
            "SELECT s.id AS id, s.zzkaec AS name, s.iofhpi AS code, s.mivbcz AS location, " +
            "s.bviiio_x AS lon, s.bviiio_y AS lat, s.epjutj AS typeCodes, s.zebpsu AS statusCode, " +
            "s.ccnhtm AS waterIndicator, s.ijzsby AS rainIndicator, s.nxtggq AS methodCodes, " +
            "s.lhwhuc AS owner, s.cbitue AS phone " +
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
}
