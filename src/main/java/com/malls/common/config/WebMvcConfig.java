package com.malls.common.config;

import com.malls.common.security.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配：把鉴权拦截器挂到请求链上。
 *
 * <p>为什么必须显式注册：{@code @Component} 只是把拦截器放进容器，
 * Spring MVC <b>不会</b>自动使用它；不注册的话 {@code @RequireLogin}/{@code @RequireRole}
 * 就成了纯注释，登录校验形同虚设。
 *
 * <p>只拦 {@code /api/**}：Swagger、错误页、静态资源不需要鉴权。
 * 这里不做路径排除 —— 未加注解的接口（注册/登录/刷新）在拦截器里本来就放行，
 * 少一份白名单就少一处"忘了同步"的隐患。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
