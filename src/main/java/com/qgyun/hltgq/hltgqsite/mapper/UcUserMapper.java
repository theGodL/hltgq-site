package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 企效 UC 用户信息只读查询（t_apaas_uc_*），供人员信息接口使用。
 * <p>列均以小写别名输出，Java 侧统一按小写键取值；关联表口径与 {@code ArchiveSyncMapper} 一致：
 * t_apaas_uc_user_org_rel.biz_id / t_apaas_uc_user_position_rel.biz_id 即用户 id，
 * rel_id 分别指向部门表/岗位表主键；查询一律限定 corp_code='hltgq'。
 */
public interface UcUserMapper {

    /** 按用户主键查基本字段（不存在返回 null） */
    @Select("SELECT id, name, login_name, mobile, email, sex " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{userId} AND corp_code = 'hltgq' LIMIT 1")
    Map<String, Object> selectUserById(@Param("userId") String userId);

    /** 所属部门列表（id/code/name/main 主部门标记；无部门返回空列表） */
    @Select("SELECT o.id, o.code, o.name, rel.main " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND rel.biz_id = #{userId}")
    List<Map<String, Object>> selectDeptsByUserId(@Param("userId") String userId);

    /** 岗位列表（id/code/name；无岗位返回空列表） */
    @Select("SELECT p.id, p.code, p.name " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" rel " +
            "JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p ON rel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE rel.corp_code = 'hltgq' AND rel.biz_id = #{userId}")
    List<Map<String, Object>> selectPositionsByUserId(@Param("userId") String userId);
}
