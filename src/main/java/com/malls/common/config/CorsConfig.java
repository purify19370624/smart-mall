package com.malls.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：给前后端分离的本地开发留一条路。
 *
 * <p>为什么需要它：前台 Vue 开发服务器跑在 {@code http://localhost:5173}，
 * 后端在 {@code :8080}，浏览器会按同源策略拦截跨域请求。生产环境走 Nginx
 * 同源反代（{@code /api} → 8080）其实不需要 CORS，但只要有人想「前端直连后端」调试，
 * 少了这段就会全线 preflight 失败。
 *
 * <p>为什么用 {@code allowedOriginPatterns} 而不是 {@code allowedOrigins("*")}：
 * 携带凭据（Cookie / Authorization）时规范禁止通配符；pattern 可以写出
 * 「任意端口的本机地址」这种受限白名单，比放开通配更安全。
 *
 * <p>注意：本方法只影响 Spring MVC 的 CORS 处理，与 JWT 过滤器无关——
 * 预检请求 {@code OPTIONS} 不带 Authorization，所以必须在这里放行，
 * 否则浏览器根本拿不到业务响应。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
