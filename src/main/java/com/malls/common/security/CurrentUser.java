package com.malls.common.security;

import com.malls.user.domain.Role;

/**
 * 当前登录用户（从令牌里解析、并经数据库校验后得到的最小身份信息）。
 *
 * <p>只放 uid / username / role 三项：足以做鉴权与业务归属判断，
 * 又不必每次去查用户表拼装完整实体。
 *
 * @param id       用户 ID（令牌 sub）
 * @param username 登录名
 * @param role     角色，鉴权用
 */
public record CurrentUser(Long id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean hasAnyRole(Role... allowed) {
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        for (Role candidate : allowed) {
            if (candidate == role) {
                return true;
            }
        }
        return false;
    }
}
