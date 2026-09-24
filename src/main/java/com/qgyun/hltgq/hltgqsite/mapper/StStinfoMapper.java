package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.vo.StationSiteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StStinfoMapper extends BaseMapper<StStinfo> {

    /**
     * 站点档案：按站点标识批量取「站点编号 + 站点管理主键 + 站名 + 经纬度」。
     * <p>标识有两种形态：站点编号（档案主键 iofhpi）与站点管理主键（闸门/流量/墒情业务表的 site 列
     * 存的就是站点管理主键），两类列一起匹配，一次查全，避免逐站查询。
     *
     * @param keys 站点标识列表（非空；站点编号或站点管理主键）
     * @return 站点档案行：stcd=站点编号 iofhpi、siteId=站点管理主键 id、name=站名 zzkaec、lon/lat=经纬度
     */
    @Select("<script>" +
            "SELECT iofhpi AS stcd, id AS site_id, zzkaec AS name, bviiio_x AS lon, bviiio_y AS lat " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" WHERE " +
            "iofhpi IN <foreach collection='keys' item='k' open='(' separator=',' close=')'>#{k}</foreach> " +
            "OR id IN <foreach collection='keys' item='k' open='(' separator=',' close=')'>#{k}</foreach>" +
            "</script>")
    @Results({
            @Result(column = "stcd", property = "stcd"),
            @Result(column = "site_id", property = "siteId"),
            @Result(column = "name", property = "name"),
            @Result(column = "lon", property = "lon"),
            @Result(column = "lat", property = "lat")
    })
    List<StationSiteVO> selectArchiveSites(@Param("keys") Collection<String> keys);
}
