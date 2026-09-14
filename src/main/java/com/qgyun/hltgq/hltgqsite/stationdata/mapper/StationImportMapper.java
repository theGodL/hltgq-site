package com.qgyun.hltgq.hltgqsite.stationdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StationArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 站点信息导入 Mapper（t_auto_hltgq_5nw74_vnqqef）。
 * <p>主表新增走 MyBatis-Plus 内建（insert，主键 ASSIGN_UUID 短 ID）；
 * 其余为手写 SQL：编号查重、渠系按名称匹配（渠系管理表）、管理单位按名称匹配（档案表自关联）。
 * <p>名称 → id 匹配均返回全部命中行：0 行 = 未匹配（行失败）、1 行 = 命中、
 * 多行 = 重名歧义（行失败，提示存在多个匹配）。
 */
@Mapper
public interface StationImportMapper extends BaseMapper<StationArchive> {

    /**
     * 站点编号是否已存在（corp_code 限定 hltgq）。
     * <p>导入时用于编号查重：iofhpi 为其他业务表 stcd 用值，须唯一。
     */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE \"iofhpi\" = #{code} AND \"corp_code\" = 'hltgq'")
    long countByCode(@Param("code") String code);

    /**
     * 渠系匹配（渠系类别列）：按渠系名称精确匹配渠系管理表，返回全部命中 id。
     * <p>渠系管理表 t_auto_hltgq_knc3g_egvnhw，名称列 gfaegg（口径与站点详情接口 canal 一致）。
     */
    @Select("SELECT \"id\" FROM \"qixiao-apaas\".\"t_auto_hltgq_knc3g_egvnhw\" " +
            "WHERE \"gfaegg\" = #{name}")
    List<String> selectCanalIds(@Param("name") String name);

    /**
     * 管理单位匹配（管理单位列）：按名称精确匹配站点档案表（ahieto 为档案表自关联），返回全部命中 id。
     * <p>注意：管理单位为档案表中已有记录（管理单位 / 站点同表），此处按 zzkaec 全表匹配。
     */
    @Select("SELECT \"id\" FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE \"corp_code\" = 'hltgq' AND \"zzkaec\" = #{name}")
    List<String> selectUnitIds(@Param("name") String name);
}
