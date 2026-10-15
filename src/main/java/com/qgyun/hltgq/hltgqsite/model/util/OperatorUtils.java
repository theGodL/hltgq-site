package com.qgyun.hltgq.hltgqsite.model.util;

import com.qgyun.hltgq.hltgqsite.auth.UserContext;
import com.qgyun.hltgq.hltgqsite.auth.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审计操作人取值（唯一实现点）：登录会话优先，会话缺失时用配置兜底（审计列 NOT NULL）。
 * <p>各模块 XxxSupport 保留门面并委托本类；需要「未登录即拒绝」语义的模块请自行校验
 * （如值班 {@code requireOperator()}），不要改用本兜底方法。
 */
public final class OperatorUtils {

    private static final Logger log = LoggerFactory.getLogger(OperatorUtils.class);

    private OperatorUtils() {
    }

    /** 当前操作人（会话缺失时用配置兜底并告警） */
    public static String currentOperatorId(String fallbackOperator) {
        UserContext user = UserContextHolder.currentUser();
        if (user != null && user.getUserId() != null && !user.getUserId().trim().isEmpty()) {
            return user.getUserId().trim();
        }
        log.warn("当前请求无用户上下文，审计字段使用兜底值：{}", fallbackOperator);
        return fallbackOperator;
    }
}
