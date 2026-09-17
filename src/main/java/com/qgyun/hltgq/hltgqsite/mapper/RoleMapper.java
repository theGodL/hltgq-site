package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;

/**
 * APaaS 角色权限 Mapper：系统管理员角色判定（只读）。
 * <p>数据源：qixiao-apaas 库 t_apaas_auth_role_assign_rel（角色指派关系）+ t_apaas_auth_role（角色表）。
 * <p>判定规则：人员直接指派（field_id='USER'）的角色中命中管理员角色编码之一即系统管理员
 * （编码清单由 RolePermissionService.ADMIN_ROLE_CODES 单一维护，本 Mapper 只接收、不写死）；
 * 黑名单关联（field_id='BLACK_LIST'）指向该角色时剔除。
 * <p>角色侧不限定 corp_code：administra 为平台内置管理员角色，可能不挂在灌区企业名下，
 * 归属范围由指派关系的 rel.corp_code = 'hltgq' 限定。
 */
public interface RoleMapper {

    /**
     * 判定用户是否拥有管理员角色（>0 = 是）
     *
     * @param userId    用户主键（t_apaas_uc_user.id）
     * @param roleCodes 管理员角色编码集合（调用方 RolePermissionService 传入）
     * @return 命中行数（0/1）
     */
    @Select("<script>SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_apaas_auth_role_assign_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_auth_role\" r " +
            "  ON rel.biz_id = r.id " +
            "WHERE rel.rel_id = #{userId} " +
            "  AND rel.corp_code = 'hltgq' " +
            "  AND rel.field_id = 'USER' " +
            "  AND r.code IN " +
            "<foreach collection='roleCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach> " +
            "  AND NOT EXISTS ( " +
            "    SELECT 1 FROM \"qixiao-apaas\".\"t_apaas_auth_role_assign_rel\" bl " +
            "    WHERE bl.rel_id = #{userId} AND bl.field_id = 'BLACK_LIST' AND bl.biz_id = r.id " +
            "  )</script>")
    int existsAdminRole(@Param("userId") String userId, @Param("roleCodes") Collection<String> roleCodes);
}
