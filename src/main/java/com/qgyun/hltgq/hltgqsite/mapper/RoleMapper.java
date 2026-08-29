package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * APaaS 角色权限 Mapper：系统管理员角色判定（只读）。
 * <p>数据源：qixiao-apaas 库 t_apaas_auth_role_assign_rel（角色指派关系）+ t_apaas_auth_role（角色表）。
 * 判定规则：人员直接指派（field_id='USER'）的角色中含 code='hltgq_default_admin' 即系统管理员；
 * 黑名单关联（field_id='BLACK_LIST'）指向该角色时剔除。
 */
public interface RoleMapper {

    /**
     * 判定用户是否拥有系统管理员角色（>0 = 是）
     *
     * @param userId 用户主键（t_apaas_uc_user.id）
     * @return 命中行数（0/1）
     */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_apaas_auth_role_assign_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_auth_role\" r " +
            "  ON rel.biz_id = r.id AND r.corp_code = 'hltgq' " +
            "WHERE rel.rel_id = #{userId} " +
            "  AND rel.corp_code = 'hltgq' " +
            "  AND rel.field_id = 'USER' " +
            "  AND r.code = 'hltgq_default_admin' " +
            "  AND NOT EXISTS ( " +
            "    SELECT 1 FROM \"qixiao-apaas\".\"t_apaas_auth_role_assign_rel\" bl " +
            "    WHERE bl.rel_id = #{userId} AND bl.field_id = 'BLACK_LIST' AND bl.biz_id = r.id " +
            "  )")
    int existsAdminRole(@Param("userId") String userId);
}
