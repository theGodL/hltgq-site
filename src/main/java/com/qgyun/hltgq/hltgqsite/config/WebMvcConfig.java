package com.qgyun.hltgq.hltgqsite.config;

import com.qgyun.hltgq.hltgqsite.auth.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册登录验证拦截器。
 * <p>总开关 auth.enabled（默认 false），验证通过后置 true 灰度；关闭时全量接口不受影响。
 * <p>所有页面（*.html）、静态资源（/lib、/templates）与接口均走拦截器 sessionId 验证：
 * 未登录访问页面 302 跳转平台登录页，登录后 Cookie 携带 sessionId 放行。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    /** 登录验证总开关 */
    @Value("${auth.enabled:false}")
    private boolean authEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!authEnabled) {
            return;
        }
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error");
    }
}
