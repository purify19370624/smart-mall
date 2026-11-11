package com.malls.audit.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.malls.common.persistence.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("sys_audit_log")
public class AuditLog extends BaseEntity {

    /** 操作人 ID，未登录或系统任务为空。 */
    private Long userId;

    /** 操作人登录名冗余存储，用户改名或删除后日志仍可读。 */
    private String username;

    /** 业务模块，如 order、marketing，用于按域检索。 */
    private String module;

    /** 操作码，如 CREATE_ORDER，由注解或方法名推导。 */
    private String action;

    /** HTTP 方法，GET / POST / PUT / DELETE。 */
    private String method;

    /** 请求路径。 */
    private String uri;

    /** 请求参数摘要，敏感字段（密码、token）已脱敏。 */
    private String params;

    /** 客户端 IP，取 X-Forwarded-For 首个非 unknown 地址。 */
    private String ip;

    /** 是否成功：true 成功，false 抛异常或返回业务失败码。 */
    private boolean success;

    /** 失败原因摘要，成功时为空。 */
    private String errorMsg;

    /** 请求耗时（毫秒），用于慢接口排查。 */
    private Long costMs;

    /** 是否为成功记录。 */
    public boolean succeeded() {
        return success;
    }

    /** 是否为失败记录。 */
    public boolean failed() {
        return !success;
    }

    /** 是否超过给定耗时阈值（毫秒），用于筛慢请求。 */
    public boolean slow(long thresholdMs) {
        return costMs != null && costMs > thresholdMs;
    }

}
