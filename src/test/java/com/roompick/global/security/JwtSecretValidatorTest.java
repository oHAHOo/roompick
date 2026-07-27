package com.roompick.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretValidatorTest {

    @Test
    void 기본_시크릿이면_기동에_실패한다() {
        // given: 로컬 개발용 기본 시크릿이 그대로 설정되어 있습니다.
        JwtProperties jwtProperties = new JwtProperties(
                "roompick-local-dev-only-jwt-secret-please-change-1234567890",
                1800L,
                1209600L
        );
        JwtSecretValidator validator = new JwtSecretValidator(jwtProperties);

        // when & then: 예외가 발생해 기동이 중단된다.
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 시크릿이_너무_짧으면_기동에_실패한다() {
        // given: HS256 최소 길이(32바이트)보다 짧은 시크릿입니다.
        JwtProperties jwtProperties = new JwtProperties("too-short-secret", 1800L, 1209600L);
        JwtSecretValidator validator = new JwtSecretValidator(jwtProperties);

        // when & then: 예외가 발생해 기동이 중단된다.
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 충분히_길고_기본값이_아닌_시크릿이면_기동에_성공한다() {
        // given: 운영용으로 별도 발급한 충분히 긴 시크릿입니다.
        JwtProperties jwtProperties = new JwtProperties(
                "operations-issued-production-secret-key-1234567890abcdef",
                1800L,
                1209600L
        );
        JwtSecretValidator validator = new JwtSecretValidator(jwtProperties);

        // when & then: 예외 없이 검증을 통과한다.
        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }
}
