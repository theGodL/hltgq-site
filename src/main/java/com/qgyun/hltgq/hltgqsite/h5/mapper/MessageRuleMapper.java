package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsite.h5.entity.MessageRule;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * H5 消息中心：消息接收规则表 Mapper。
 * <p>同步侧（MessageService）查询走 MP 内置方法（规则量小全量载入内存匹配）；
 * 管理侧（消息发送设置页 /message-rule）提供分页、候选与名称解析查询。
 * <p>列别名与 VO 属性同名（全局 map-underscore-to-camel-case=false，自定义查询不做下划线自动映射）。
 * <p>四个维度候选口径与 H5 命中判定一致（MessageQueryMapper）：
 * 部门/岗位/角色取 code（corp_code=hltgq 限定），人员取 login_name（登录名，保存进 target_id 的匹配键）。
 * <p>注意：线上 Kingbase 的 || 按逻辑 OR 语义处理（会产生布尔值），拼接必须用 CONCAT。
 */
public interface MessageRuleMapper extends BaseMapper<MessageRule> {

    String T_RULE = "\"qixiao-apaas\".\"t_auto_hltgq_water_message_rule\"";
    String T_ORG = "\"qixiao-apaas\".\"t_apaas_uc_org\"";
    String T_POSITION = "\"qixiao-apaas\".\"t_apaas_uc_position\"";
    String T_ROLE = "\"qixiao-apaas\".\"t_apaas_auth_role\"";
    String T_USER = "\"qixiao-apaas\".\"t_apaas_uc_user\"";
    String T_SITE = "\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"";

    /**
     * 规则分页（消息发送设置列表）：messageType 可选过滤，按更新时间倒序。
     * <p>只取展示字段（id/类型/维度/目标/站点/更新时间），名称解析由 Service 层批量完成。
     */
    @Select("<script>" +
            "SELECT r.id AS id, r.message_type AS messageType, r.target_type AS targetType, " +
            "r.target_id AS targetId, r.site_id AS siteId, r.updated_at AS updatedAt " +
            "FROM " + T_RULE + " r " +
            "<if test='messageType != null'>WHERE r.message_type = #{messageType} </if>" +
            "ORDER BY r.updated_at DESC NULLS LAST, r.id DESC" +
            "</script>")
    IPage<MessageRuleVO> selectRulePage(IPage<MessageRuleVO> page, @Param("messageType") String messageType);

    /**
     * 同类型同维度同目标编码是否已存在（新增/编辑查重，编辑时经 excludeId 排除自身）。
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM " + T_RULE + " " +
            "WHERE message_type = #{messageType} AND target_type = #{targetType} AND target_id = #{targetId} " +
            "<if test='excludeId != null'>AND id != #{excludeId} </if>" +
            "</script>")
    long countSameRule(@Param("messageType") String messageType,
                       @Param("targetType") String targetType,
                       @Param("targetId") String targetId,
                       @Param("excludeId") String excludeId);

    /** 部门候选 / 名称解析：codes 为空 = 全量候选；非空 = 按编码批量取（名称解析与存在性校验共用） */
    @Select("<script>" +
            "SELECT g.code AS value, g.name AS label FROM " + T_ORG + " g " +
            "WHERE g.corp_code = 'hltgq' " +
            "<if test='codes != null and codes.size() > 0'>AND g.code IN " +
            "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach> </if>" +
            "ORDER BY g.name ASC, g.code ASC" +
            "</script>")
    List<PickerOptionVO> selectOrgOptions(@Param("codes") List<String> codes);

    /** 岗位候选 / 名称解析：口径同部门（code/name） */
    @Select("<script>" +
            "SELECT p.code AS value, p.name AS label FROM " + T_POSITION + " p " +
            "WHERE p.corp_code = 'hltgq' " +
            "<if test='codes != null and codes.size() > 0'>AND p.code IN " +
            "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach> </if>" +
            "ORDER BY p.name ASC, p.code ASC" +
            "</script>")
    List<PickerOptionVO> selectPositionOptions(@Param("codes") List<String> codes);

    /** 角色候选 / 名称解析：口径同部门（code/name） */
    @Select("<script>" +
            "SELECT r.code AS value, r.name AS label FROM " + T_ROLE + " r " +
            "WHERE r.corp_code = 'hltgq' " +
            "<if test='codes != null and codes.size() > 0'>AND r.code IN " +
            "<foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach> </if>" +
            "ORDER BY r.name ASC, r.code ASC" +
            "</script>")
    List<PickerOptionVO> selectRoleOptions(@Param("codes") List<String> codes);

    /**
     * 人员候选 / 名称解析：value = login_name（保存进 target_id 的匹配键，命中判定按登录名）；
     * sub = 登录名（辅助展示，与姓名区分重名）。
     */
    @Select("<script>" +
            "SELECT u.login_name AS value, u.name AS label, u.login_name AS sub FROM " + T_USER + " u " +
            "WHERE u.corp_code = 'hltgq' " +
            "<if test='loginNames != null and loginNames.size() > 0'>AND u.login_name IN " +
            "<foreach collection='loginNames' item='l' open='(' separator=',' close=')'>#{l}</foreach> </if>" +
            "ORDER BY u.name ASC, u.login_name ASC" +
            "</script>")
    List<PickerOptionVO> selectUserOptions(@Param("loginNames") List<String> loginNames);

    /**
     * 站点候选 / 名称解析（仅告警规则用）：value = 站点档案主键（site_id 与告警表 site 同键），
     * label = 站名（zzkaec），sub = 站点编号（iofhpi）。
     */
    @Select("<script>" +
            "SELECT s.id AS value, s.zzkaec AS label, s.iofhpi AS sub FROM " + T_SITE + " s " +
            "WHERE s.corp_code = 'hltgq' " +
            "<if test='ids != null and ids.size() > 0'>AND s.id IN " +
            "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach> </if>" +
            "ORDER BY s.zzkaec ASC, s.id ASC" +
            "</script>")
    List<PickerOptionVO> selectSiteOptions(@Param("ids") List<String> ids);
}
