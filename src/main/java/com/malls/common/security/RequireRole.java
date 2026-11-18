package com.malls.common.security;

import com.malls.user.domain.Role;

import java.lang.annotation.*;

/**
 * 角色鉴权：标注在 Controller 类或方法上，要求当前用户具备其中任意一个角色。
 *
 * <p>用法：
 * <pre>
 * &#64;RequireRole({Role.OPERATOR, Role.ADMIN})   // 后台：运营或管理员
 * &#64;RequireRole(Role.ADMIN)                     // 仅管理员
 * </pre>
 *
 * <p>方法级优先、类级兜底，由 {@link AuthInterceptor} 裁决：
 * 未登录抛 40100，登录但角色不符抛 40300。
 *
 * <p>为什么是"任意一个"而不是"全部"：权限矩阵里后台接口通常是
 * "运营能做、管理员也能做"，要求同时具备两个角色反而写不出这种语义。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色集合，命中任意一个即放行 */
    Role[] value();
}
