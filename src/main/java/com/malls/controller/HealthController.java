package com.malls.controller;

import com.malls.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 健康检查接口：不依赖数据库、缓存等外部资源，只证明服务已启动、请求链路可用。
 *
 * <p>线上探活（k8s liveness/readiness、负载均衡）也可以直接调 {@code GET /api/health}。
 */
@Tag(name = "健康检查", description = "验证服务是否正常启动、接口是否能正常返回")
@RestController
@RequestMapping("/actuator/health")
public class HealthController {

    private static final String STATUS_UP = "UP";
    private static final String VERSION = "v0.0.1";

    /** 类加载时间即服务启动时间，用于计算运行时长 */
    private static final Instant STARTED_AT = Instant.now();

    private final String applicationName;

    public HealthController(@Value("${spring.application.name:malls}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Operation(summary = "服务健康检查", description = "返回服务状态、应用名、版本、启动时间与已运行时长")
    @ApiResponse(responseCode = "200", description = "服务正常")
    @GetMapping
    public Result<HealthInfo> health() {
        long uptimeSeconds = (Instant.now().toEpochMilli() - STARTED_AT.toEpochMilli()) / 1000;
        return Result.ok(new HealthInfo(
                STATUS_UP,
                applicationName,
                VERSION,
                STARTED_AT,
                Instant.now(),
                uptimeSeconds));
    }

    @Operation(summary = "连通性探测", description = "最轻量的探活接口，正常时 data 为 pong")
    @ApiResponse(responseCode = "200", description = "服务正常")
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("pong");
    }

    /** 健康检查结果，字段说明会直接展示在 Swagger UI 的 Schema 里 */
    @Schema(description = "健康检查结果")
    public record HealthInfo(
            @Schema(description = "服务状态", example = "UP") String status,
            @Schema(description = "应用名", example = "malls") String application,
            @Schema(description = "应用版本", example = "v0.0.1") String version,
            @Schema(description = "启动时间（UTC）") Instant startedAt,
            @Schema(description = "当前时间（UTC）") Instant timestamp,
            @Schema(description = "已运行秒数", example = "42") long uptimeSeconds) {
    }
}
