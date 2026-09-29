package com.bbtc.bluebird.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

/**
 * JWT 编解码（HS256，02 §1.7）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private SecretKey key;

    @PostConstruct
    void init() {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            key = Keys.hmacShaKeyFor(random);
            log.warn("JWT_SECRET 未配置，已生成临时密钥（重启后 token 失效）。生产请通过 ADR-010 自动生成或环境变量注入。");
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("jwt.secret 过短（需 ≥256bit/32B）");
            }
            key = Keys.hmacShaKeyFor(bytes);
        }
    }

    public String createAccess(Long userId, String username, String roleCode) {
        return create(userId, username, roleCode, TYPE_ACCESS, props.getAccessTtl());
    }

    public String createRefresh(Long userId, String username, String roleCode) {
        return create(userId, username, roleCode, TYPE_REFRESH, props.getRefreshTtl());
    }

    private String create(Long userId, String username, String roleCode, String type, long ttlSeconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlSeconds * 1000L);
        return Jwts.builder()
                .setId(java.util.UUID.randomUUID().toString())
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, roleCode)
                .claim(CLAIM_TYPE, type)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析并校验；无效抛 {@link JwtException}。 */
    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public long accessTtl() {
        return props.getAccessTtl();
    }
}
