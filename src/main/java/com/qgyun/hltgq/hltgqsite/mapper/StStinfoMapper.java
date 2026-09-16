package com.qgyun.hltgq.hltgqsite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface StStinfoMapper extends BaseMapper<StStinfo> {

    /**
     * 站点档案：按站点标识批量取「站点管理主键」（id）。
     * <p>标识有两种形态：测站编码（站点表主键 iofhpi）与站点管理主键本身
     * （闸门/流量/墒情业务表的 site 列存的就是站点管理主键），两类列一起匹配，一次查全。
     *
     * @param codes 站点标识列表（非空；测站编码或站点管理主键）
     * @return 每行 {iofhpi: 测站编码, id: 站点管理主键}
     */
    @Select("<script>" +
            "SELECT iofhpi, id FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" WHERE " +
            "iofhpi IN <foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach> " +
            "OR id IN <foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "</script>")
    List<Map<String, String>> selectIdByCodes(@Param("codes") Collection<String> codes);
}
