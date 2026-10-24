package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 站点 / 设备 / 人员 / 部门档案存在性校验（唯一实现点）。
 * <p>巡检计划、巡检记录、问题记录、工单四个模块的 Mapper 原先各抄一份同 SQL 的
 * {@code countSite} / {@code countDeviceInSite} / {@code countUser}（工单另有 {@code countOrg}），
 * 共 10 个重复方法；此处收敛，业务计数（记录数、问题数、编号查重等）仍留在各业务 Mapper。
 */
public interface ArchiveLookupMapper {

    /** 站点是否存在（限定 corp_code=hltgq）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    long countSite(@Param("id") String id);

    /** 设备是否存在且属于该站点。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_water_device\" " +
            "WHERE id = #{deviceId} AND site = #{siteId}")
    long countDeviceInSite(@Param("siteId") String siteId, @Param("deviceId") String deviceId);

    /** 人员是否存在（限定 corp_code=hltgq）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    long countUser(@Param("id") String id);

    /** 部门是否存在（存量工单 org 可能落部门短码，id / code 双兼容）。 */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_apaas_uc_org\" " +
            "WHERE (id = #{id} OR code = #{id}) AND corp_code = 'hltgq'")
    long countOrg(@Param("id") String id);
}
