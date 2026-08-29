package com.qgyun.hltgq.hltgqsite.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 全量查询用户：部门取「main='1' 优先、否则任一部门关系」（实际数据 main 多为 NULL，不能硬过滤），
     * 岗位取任一岗位名称（窗口函数去重，防多岗位笛卡尔积）。
     * <p>无部门用户不丢行（LEFT JOIN），Java 侧跳过并记日志。
     */
    @Select("SELECT u.id, u.name, u.login_name, u.mobile, u.email, u.sex, u.id_card, " +
            "to_char(u.birthday, 'YYYY-MM-DD') AS birthday, u.updated_at, " +
            "o.code AS dept_code, p.name AS position_name " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "LEFT JOIN ( " +
            "  SELECT biz_id, rel_id FROM ( " +
            "    SELECT biz_id, rel_id, ROW_NUMBER() OVER ( " +
            "      PARTITION BY biz_id ORDER BY CASE WHEN main = '1' THEN 0 ELSE 1 END, created_at ASC " +
            "    ) AS rn " +
            "    FROM \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" " +
            "    WHERE corp_code = 'hltgq' " +
            "  ) t WHERE rn = 1 " +
            ") rel ON u.id = rel.biz_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o " +
            "  ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "LEFT JOIN ( " +
            "  SELECT biz_id, rel_id FROM ( " +
            "    SELECT biz_id, rel_id, ROW_NUMBER() OVER (PARTITION BY biz_id ORDER BY created_at ASC) AS rn " +
            "    FROM \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" " +
            "    WHERE corp_code = 'hltgq' " +
            "  ) t WHERE rn = 1 " +
            ") prel ON u.id = prel.biz_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p " +
            "  ON prel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE u.corp_code = 'hltgq'")
    List<Map<String, Object>> selectUsersAll();

    /** 增量查询用户：updated_at >= since，部门/岗位关联同全量 */
    @Select("SELECT u.id, u.name, u.login_name, u.mobile, u.email, u.sex, u.id_card, " +
            "to_char(u.birthday, 'YYYY-MM-DD') AS birthday, u.updated_at, " +
            "o.code AS dept_code, p.name AS position_name " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "LEFT JOIN ( " +
            "  SELECT biz_id, rel_id FROM ( " +
            "    SELECT biz_id, rel_id, ROW_NUMBER() OVER ( " +
            "      PARTITION BY biz_id ORDER BY CASE WHEN main = '1' THEN 0 ELSE 1 END, created_at ASC " +
            "    ) AS rn " +
            "    FROM \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" " +
            "    WHERE corp_code = 'hltgq' " +
            "  ) t WHERE rn = 1 " +
            ") rel ON u.id = rel.biz_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o " +
            "  ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "LEFT JOIN ( " +
            "  SELECT biz_id, rel_id FROM ( " +
            "    SELECT biz_id, rel_id, ROW_NUMBER() OVER (PARTITION BY biz_id ORDER BY created_at ASC) AS rn " +
            "    FROM \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" " +
            "    WHERE corp_code = 'hltgq' " +
            "  ) t WHERE rn = 1 " +
            ") prel ON u.id = prel.biz_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p " +
            "  ON prel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE u.corp_code = 'hltgq' AND u.updated_at >= #{since}")
    List<Map<String, Object>> selectUsersSince(@Param("since") String since);

    /** 按登录名查询单个用户（部门/岗位关联同全量查询），单点登录兜底同步用 */
    @Select("SELECT u.id, u.name, u.login_name, u.mobile, u.email, u.sex, u.id_card, " +
            "to_char(u.birthday, 'YYYY-MM-DD') AS birthday, u.updated_at, " +
            "o.code AS dept_code, p.name AS position_name " +
            "FROM \"qixiao-apaas\".\"t_apaas_uc_user\" u " +
            "LEFT JOIN ( " +
            "  SELECT biz_id, rel_id FROM ( " +
            "    SELECT biz_id, rel_id, ROW_NUMBER() OVER ( " +
            "      PARTITION BY biz_id ORDER BY CASE WHEN main = '1' THEN 0 ELSE 1 END, created_at ASC " +
            "    ) AS rn " +
            "    FROM \"qixiao-apaas\".\"t_apaas_uc_user_org_rel\" " +
            "    WHERE corp_code = 'hltgq' " +
            "  ) t WHERE rn = 1 " +
            ") rel ON u.id = rel.biz_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_org\" o " +
            "  ON rel.rel_id = o.id AND o.corp_code = 'hltgq' " +
            "LEFT JOIN ( " +
            "  SELECT biz_id, rel_id FROM ( " +
            "    SELECT biz_id, rel_id, ROW_NUMBER() OVER (PARTITION BY biz_id ORDER BY created_at ASC) AS rn " +
            "    FROM \"qixiao-apaas\".\"t_apaas_uc_user_position_rel\" " +
            "    WHERE corp_code = 'hltgq' " +
            "  ) t WHERE rn = 1 " +
            ") prel ON u.id = prel.biz_id " +
            "LEFT JOIN \"qixiao-apaas\".\"t_apaas_uc_position\" p " +
            "  ON prel.rel_id = p.id AND p.corp_code = 'hltgq' " +
            "WHERE u.corp_code = 'hltgq' AND u.login_name = #{loginName} LIMIT 1")
    Map<String, Object> selectUserByLoginName(@Param("loginName") String loginName);

    /** 查询浩微 userId 回写字段（name_spell），单点登录优先读取 */
    @Select("SELECT name_spell FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE login_name = #{loginName} AND corp_code = 'hltgq'")
    String selectNameSpellByLoginName(@Param("loginName") String loginName);

    /** 回写浩微 userId 到 name_spell（人员同步成功后按 loginId 对应回写，单点登录用） */
    @Update("UPDATE \"qixiao-apaas\".\"t_apaas_uc_user\" SET name_spell = #{archiveUserId} " +
            "WHERE login_name = #{loginName} AND corp_code = 'hltgq'")
    int updateNameSpell(@Param("loginName") String loginName, @Param("archiveUserId") String archiveUserId);
}
