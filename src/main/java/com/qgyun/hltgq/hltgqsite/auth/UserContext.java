package com.qgyun.hltgq.hltgqsite.auth;

import lombok.Data;

/**
 * 当前登录人上下文
 * <p>数据来源：平台 Redis 会话 Hash（HGETALL {sessionId}）。
 * 仅保留会话 Hash 实际存在的字段，字段名解析兼容常见命名变体（见 SessionContextService）。
 */
@Data
public class UserContext {

    /** 用户主键（t_apaas_uc_user.id，UUID） */
    private String userId;

    /** 企业编码 */
    private String corpCode;

    /** 是否超级管理员 */
    private String superAdmin;

    /** 三维 SSO 所需登录名（t_apaas_uc_user.login_name），由 resolveLoginName 查库兜底填充 */
    private String loginName;
}
