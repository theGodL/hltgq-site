package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

/**
 * 人员信息（按用户 ID 查询）：基本字段 + 部门/岗位列表（含 id/code/name）。
 * <p>数据源：企效 UC 表（t_apaas_uc_user / t_apaas_uc_org / t_apaas_uc_position 及两张关联表），
 * 全部限定 corp_code='hltgq'。
 */
@Data
public class UserProfileVO {

    /** 用户主键 */
    private String userId;

    /** 姓名 */
    private String name;

    /** 登录名 */
    private String loginName;

    /** 手机号 */
    private String mobile;

    /** 邮箱 */
    private String email;

    /** 性别（M/F；平台未维护时为 null） */
    private String sex;

    /** 所属部门列表（可能为空数组） */
    private List<DeptItem> departments;

    /** 岗位列表（可能为空数组） */
    private List<PositionItem> positions;

    /** 部门项 */
    @Data
    public static class DeptItem {

        /** 部门主键 */
        private String id;

        /** 部门编码 */
        private String code;

        /** 部门名称 */
        private String name;

        /** 主部门标记：'1'=主部门；null/其他=非主（平台实际数据多未维护 main，前端按需展示） */
        private String main;
    }

    /** 岗位项 */
    @Data
    public static class PositionItem {

        /** 岗位主键 */
        private String id;

        /** 岗位编码 */
        private String code;

        /** 岗位名称 */
        private String name;
    }
}
