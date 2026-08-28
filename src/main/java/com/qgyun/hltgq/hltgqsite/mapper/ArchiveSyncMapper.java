package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 企效平台 UC 表只读查询（t_apaas_uc_*），仅用于档案系统同步的数据源。
 * <p>列均以小写别名输出，Java 侧统一按小写键取值。
 */
public interface ArchiveSyncMapper {

    /** 全量查询组织（corp_code='hltgq'） */
    @Select("SELECT id, code, name, parent_id, updated_at " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_org\" " +
            "WHERE corp_code = 'hltgq'")
    List<Map<String, Object>> selectOrgsAll();

    /** 增量查询组织：updated_at >= since */
    @Select("SELECT id, code, name, parent_id, updated_at " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_org\" " +
            "WHERE corp_code = 'hltgq' AND updated_at >= #{since}")
    List<Map<String, Object>> selectOrgsSince(@Param("since") String since);

    /**
     * 全量查询用户：LEFT JOIN 主部门(main='1')取部门编码、任一岗位取岗位名称。
     * <p>条件全部放在 ON 子句中，保证无部门/无岗位用户不丢行（Java 侧跳过/兜底）。
     */
    @Select("SELECT u.id, u.name, u.login_name, u.mobile, u.email, u.sex, u.id_card, " +
            "to_char(u.birthday, 'YYYY-MM-DD') AS birthday, u.updated_at, " +
            "o.code AS dept_code, p.name AS position_name " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" rel " +
            "  ON u.id = rel.biz_id AND rel.corp_code = 'hltgq' AND rel.main = '1' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o " +
            "  ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" prel " +
            "  ON u.id = prel.biz_id AND prel.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p " +
            "  ON prel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE u.corp_code = 'hltgq'")
    List<Map<String, Object>> selectUsersAll();

    /** 增量查询用户：updated_at >= since */
    @Select("SELECT u.id, u.name, u.login_name, u.mobile, u.email, u.sex, u.id_card, " +
            "to_char(u.birthday, 'YYYY-MM-DD') AS birthday, u.updated_at, " +
            "o.code AS dept_code, p.name AS position_name " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" rel " +
            "  ON u.id = rel.biz_id AND rel.corp_code = 'hltgq' AND rel.main = '1' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o " +
            "  ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" prel " +
            "  ON u.id = prel.biz_id AND prel.corp_code = 'hltgq' " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p " +
            "  ON prel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE u.corp_code = 'hltgq' AND u.updated_at >= #{since}")
    List<Map<String, Object>> selectUsersSince(@Param("since") String since);
}
