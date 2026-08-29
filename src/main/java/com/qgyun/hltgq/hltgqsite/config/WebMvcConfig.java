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
 * <p>静态页面资源（*.html、lib、templates）放行，页面内接口请求仍走拦截器鉴权。
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
                .excludePathPatterns("/*.html", "/lib/**", "/templates/**", "/error");
    }
}
