package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

/**
 * 当前登录人返回体（仅业务必需字段）
 */
@Data
public class CurrentUserVO {

    /** 用户主键 */
    private String userId;

    /** 企业编码 */
    private String corpCode;

    /** 是否超级管理员 */
    private String superAdmin;

    /** 登录名（t_apaas_uc_user.login_name），三维 SSO 使用 */
    private String loginName;
}
