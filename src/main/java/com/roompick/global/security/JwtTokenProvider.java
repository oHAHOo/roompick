package com.roompick.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.roompick.domain.member.entity.MemberRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = jwtProperties.accessTokenValiditySeconds();
        this.refreshTokenValiditySeconds = jwtProperties.refreshTokenValiditySeconds();
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        return createToken(memberId, accessTokenValiditySeconds, role, TokenType.ACCESS);
    }

    public String createRefreshToken(Long memberId) {
        return createToken(memberId, refreshTokenValiditySeconds, null, TokenType.REFRESH);
    }

    private String createToken(Long memberId, long validitySeconds, MemberRole role, TokenType tokenType) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + validitySeconds * 1000);

        JwtBuilder builder = Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(String.valueOf(memberId))
            .issuedAt(now)
            .expiration(expiration)
            .claim(CLAIM_TOKEN_TYPE, tokenType.name())
            .signWith(key);

        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }

        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("유효하지 않은 JWT입니다. reason={}", exception.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        return getTokenType(token) == TokenType.ACCESS;
    }

    public boolean isRefreshToken(String token) {
        return getTokenType(token) == TokenType.REFRESH;
    }

    public TokenType getTokenType(String token) {
        String tokenType = parseClaims(token).get(CLAIM_TOKEN_TYPE, String.class);
        return tokenType != null ? TokenType.valueOf(tokenType) : null;
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public MemberRole getRole(String token) {
        String role = parseClaims(token).get(CLAIM_ROLE, String.class);
        return role != null ? MemberRole.valueOf(role) : null;
    }

    public String getJti(String token) {
        return parseClaims(token).getId();
    }

    public long getRemainingSeconds(String token) {
        long remainingMillis = parseClaims(token).getExpiration().getTime() - System.currentTimeMillis();
        return Math.max(remainingMillis / 1000, 0);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
