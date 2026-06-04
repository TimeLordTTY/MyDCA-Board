package com.timelordtty.dca.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token生成和验证
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret:wealth-hub-secret-key-change-in-production-environment-minimum-256-bits}")
    /**
     * 认证相关字段，仅用于当次请求或安全校验，不应在日志中明文输出。
     */
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24小时
    /**
     * JWT 过期时长配置，单位为毫秒，用于生成访问令牌的失效时间。
     */
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * 根据登录用户名生成 JWT，并写入签发时间和过期时间供后续请求认证。
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 返回展示或唯一标识字段，用于人工识别和业务查找。
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * 校验 JWT 签名和过期时间，失败时返回 false 而不是让异常穿透到业务接口。
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

