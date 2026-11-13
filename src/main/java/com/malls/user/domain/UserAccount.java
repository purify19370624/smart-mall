package com.malls.user.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("user_account")      // 表名映射；唯一约束写在建表 SQL 里
public class UserAccount extends BaseEntity {

    private String username;

    private String nickname;
    /** BCrypt 加盐哈希，禁止明文 */
    private String password;

    private String avatar;

    private String phone;

    private Role role = Role.CUSTOMER;            // 枚举默认按 name 存为 "CUSTOMER"

    private AccountStatus status = AccountStatus.ENABLED;

    private int points = 0;

    private int level = 1;

    /** 累计消费：会员等级与 AI 推荐画像的输入 */
    private BigDecimal totalConsumption = BigDecimal.ZERO;

    /** 令牌版本号：登出/改密后自增，使旧令牌立即失效 */
    private int tokenVersion = 0;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    public boolean enabled() {
        return status == AccountStatus.ENABLED;
    }

    /** 是否平台管理员，后台接口鉴权时使用 */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** 记录一次成功登录，只更新登录痕迹，不触碰密码等敏感字段 */
    public void recordLogin(String ip) {
        this.lastLoginTime = LocalDateTime.now();
        this.lastLoginIp = ip;
    }
}