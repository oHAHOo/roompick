package com.roompick.global.security;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영 환경에서 로컬 개발용 JWT 시크릿이 그대로 쓰이는 것을 막기 위한 기동 시점 검증입니다.
 */
@Component
@Profile("prod")
public class JwtSecretValidator implements InitializingBean {

    private static final String DEFAULT_SECRET = "roompick-local-dev-only-jwt-secret-please-change-1234567890";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;

    public JwtSecretValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = jwtProperties.secret();

        if (DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "운영 환경에서는 로컬 개발용 JWT_SECRET 기본값을 사용할 수 없습니다. JWT_SECRET 환경변수를 설정하세요.");
        }

        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET은 HS256 서명에 필요한 최소 " + MINIMUM_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
    }
}
