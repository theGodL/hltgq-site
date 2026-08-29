package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 企效平台 UC 用户表只读查询（t_apaas_uc_user），用于登录鉴权取 login_name。
 */
public interface ApaasUcUserMapper {

    /**
     * 按用户主键查询登录名（corp_code 限定 hltgq）
     */
    @Select("SELECT login_name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectLoginNameById(@Param("id") String id);
}
