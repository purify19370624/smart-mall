package com.malls.common.security;

import com.malls.user.domain.Role;
import com.malls.user.domain.UserAccount;
import com.malls.user.mapper.UserAccountMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 认证过滤器：解析 {@code Authorization: Bearer <accessToken>} 并把身份写进 {@link UserContext}。
 *
 * <p>与 {@link AuthInterceptor} 的分工：
 * <ul>
 *   <li>本过滤器答<b>你是谁</b>（认证）</li>
 *   <li>拦截器读 {@link RequireLogin}/{@link RequireRole} 答<b>你能不能</b>（鉴权）</li>
 * </ul>
 *
 * <p>令牌非法时<b>静默放行</b>而不是抛异常：过滤器跑在 DispatcherServlet 之前，
 * 这里抛异常会绕开 {@code @RestControllerAdvice}，前端拿到的是容器默认错误页而不是统一 JSON。
 * "没登录"与"令牌无效"走同一条匿名分支，是否拦由注解决定。
 *
 * <p>四道校验，任何一道不过都视为匿名：
 * <ol>
 *   <li>验签 + 过期 + issuer（{@link JwtUtil#parse}）</li>
 *   <li>{@code typ} 必须是 access —— 刷新令牌不能拿来访问业务接口</li>
 *   <li>账号存在且未禁用</li>
 *   <li>{@code tv} 与库里 {@code tokenVersion} 一致 —— 登出/改密后旧令牌立即失效</li>
 * </ol>
 *
 * <p>角色取<b>数据库</b>值而不是令牌里的 claim：令牌签发后被降权（如管理员转普通用户），
 * 旧令牌仍会带着 ADMIN，只信 claim 就等于给了 2 小时越权窗口。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // 排在 TraceIdFilter 之后，令牌日志才带得上 traceId
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserAccountMapper userAccountMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            resolve(request).ifPresent(UserContext::set);
            filterChain.doFilter(request, response);
        } finally {
            // 线程池会复用线程，不清理就会把上一个请求的身份带给下一个请求
            UserContext.clear();
        }
    }

    private Optional<CurrentUser> resolve(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        Optional<Claims> parsed = jwtUtil.parse(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }

        Claims claims = parsed.get();
        if (!JwtUtil.TYPE_ACCESS.equals(claims.get(JwtUtil.CLAIM_TYPE, String.class))) {
            log.debug("非访问令牌被用于访问业务接口 sub={}", claims.getSubject());
            return Optional.empty();
        }

        Long userId = subjectOf(claims);
        if (userId == null) {
            return Optional.empty();
        }
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null || !account.enabled()) {
            return Optional.empty();
        }
        if (tokenVersionOf(claims) != account.getTokenVersion()) {
            log.debug("令牌版本落后（已登出或改密） userId={}", userId);
            return Optional.empty();
        }

        Role role = account.getRole() == null ? Role.CUSTOMER : account.getRole();
        return Optional.of(new CurrentUser(account.getId(), account.getUsername(), role));
    }

    private Long subjectOf(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private int tokenVersionOf(Claims claims) {
        Object raw = claims.get(JwtUtil.CLAIM_TOKEN_VERSION);
        return raw instanceof Number number ? number.intValue() : -1;
    }
}
