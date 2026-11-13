package com.malls.common.security;

import com.malls.common.config.MallProperties;
import com.malls.user.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与校验（HS256，jjwt 0.12）。
 *
 * <p>为什么用 JWT 而不是 Session：服务端不存会话，天然适配"单体 → 微服务"的演进，
 * Day08 拆成六个服务后，任何一个服务拿同一把密钥都能验签。
 *
 * <p>安全要点（答辩会问）：
 * <ul>
 *   <li>载荷是 Base64 而非加密，<b>绝不能放密码、手机号、余额</b>，只放 uid/role/版本号</li>
 *   <li>密钥不足 32 字节时 jjwt 抛 {@code WeakKeyException}，启动即失败，不会带到线上</li>
 *   <li>必须校验 {@code exp} 与 {@code iss}，前者防永不过期，后者防"别的系统用同密钥签的令牌"混入</li>
 * </ul>
 *
 * <p>两种令牌共用一把密钥、靠 {@code typ} 区分：刷新令牌不能拿去访问业务接口。
 */
@Slf4j
@Component
public class JwtUtil {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";
    /** tokenVersion：登出 / 改密时自增，旧令牌立即失效 */
    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_TYPE = "typ";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final MallProperties.Jwt properties;
    private final SecretKey key;

    public JwtUtil(MallProperties properties) {
        this.properties = properties.jwt();
        // HS256 要求密钥至少 256 位（32 字节），不足会在启动时直接失败
        this.key = Keys.hmacShaKeyFor(this.properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** 访问令牌：带进 Authorization 头，2 小时有效 */
    public String createAccessToken(
            Long userId, String username, Role role, int tokenVersion) {
        return createToken(userId, username, role, tokenVersion,
                TYPE_ACCESS, properties.accessTtlSeconds());
    }

    /** 刷新令牌：只用来换新令牌，7 天有效 */
    public String createRefreshToken(Long userId, String username, Role role, int tokenVersion) {
        return createToken(userId, username, role, tokenVersion,
                TYPE_REFRESH, properties.refreshTtlSeconds());
    }

    private String createToken(Long userId, String username, Role role, int tokenVersion,
                               String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))          // 载荷只放非敏感标识
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析令牌。签名不对、格式不对、过期、issuer 不符都返回空。
     *
     * <p>返回 {@code Optional} 而不是抛异常：过滤器里"令牌非法"和"没带令牌"
     * 应该走同一条静默放行分支，由 {@code @RequireLogin} 决定是否拦。
     */
    public Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("令牌解析失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return properties.accessTtlSeconds();
    }

    public long refreshTtlSeconds() {
        return properties.refreshTtlSeconds();
    }
}

