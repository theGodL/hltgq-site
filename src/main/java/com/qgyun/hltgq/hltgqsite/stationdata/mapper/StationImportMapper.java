package com.qgyun.hltgq.hltgqsite.stationdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.entity.StationArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 站点信息导入 Mapper（t_auto_hltgq_5nw74_vnqqef）。
 * <p>主表新增走 MyBatis-Plus 内建（insert，主键 ASSIGN_UUID 短 ID）；
 * 其余为手写 SQL：编号查重。
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
}
