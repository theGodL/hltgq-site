package com.qgyun.hltgq.hltgqsite.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统管理员操作权限注解：标注在敏感写接口（方法或类）上，
 * AuthInterceptor 检测到后校验当前登录人是否拥有系统管理员角色
 * （角色 code = {corpCode}_default_admin，hltgq 场景为 hltgq_default_admin），
 * 非管理员返回 403。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
