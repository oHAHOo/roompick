package com.roompick.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import com.roompick.domain.member.entity.MemberRole;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-provider-unit-test-1234567890";

    private final JwtProperties jwtProperties = new JwtProperties(SECRET, 1800L, 1209600L);
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);

    @Test
    void 정상_발급된_토큰은_유효하다() {
        // given: 정상적으로 발급한 access token
        String token = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);

        // when & then: 유효성 검증을 통과한다.
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void 만료된_토큰은_유효하지_않다() {
        // given: 서명은 같은 시크릿으로 맞지만 만료 시각이 과거인 토큰
        String expiredToken = createExpiredToken();

        // when & then: 유효성 검증에 실패한다.
        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void 다른_시크릿으로_서명된_토큰은_유효하지_않다() {
        // given: 애플리케이션이 쓰는 시크릿과 다른 키로 서명된(위조된) 토큰
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "another-secret-key-that-does-not-match-the-app-key-1234567890".getBytes());
        String tamperedToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKey)
                .compact();

        // when & then: 유효성 검증에 실패한다.
        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
    }

    private String createExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Date past = new Date(System.currentTimeMillis() - 60_000);

        return Jwts.builder()
                .subject("1")
                .issuedAt(new Date(past.getTime() - 1_000))
                .expiration(past)
                .signWith(key)
                .compact();
    }
}
