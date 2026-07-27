package com.roompick.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.global.security.JwtProperties;
import com.roompick.global.security.JwtTokenProvider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void 공개_API는_인증_없이_접근할_수_있다() throws Exception {
        // given: 공개 경로로 지정된 GET 요청입니다.
        // when: 인증 없이 호출합니다. (컨트롤러가 아직 없어 정상 상태 코드는 아닙니다)
        MvcResult result = mockMvc.perform(get("/api/v1/rooms/1")).andReturn();

        // then: 인증 필터에서 401/403으로 막히지 않습니다.
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void 인증_토큰이_없으면_보호된_API_접근이_차단된다() throws Exception {
        // given & when: 토큰 없이 보호된 경로를 호출합니다.
        // then: 401 Unauthorized로 차단됩니다.
        mockMvc.perform(get("/api/v1/protected-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void USER_권한으로는_관리자_API에_접근할_수_없다() throws Exception {
        // given: USER 권한 access token
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);

        // when & then: 관리자 API 호출 시 403 Forbidden으로 차단됩니다.
        mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ADMIN_권한으로는_관리자_API에_접근할_수_있다() throws Exception {
        // given: ADMIN 권한 access token
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.ADMIN);

        // when: 관리자 API를 호출하면
        MvcResult result = mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andReturn();

        // then: 401/403으로 막히지 않습니다. (컨트롤러가 아직 없어 정상 상태 코드는 아닙니다)
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void Refresh_Token으로는_일반_API에_접근할_수_없다() throws Exception {
        // given: 로그인에서 발급되는 refresh token
        String refreshToken = jwtTokenProvider.createRefreshToken(1L);

        // when & then: 일반 보호 API 호출 시 401로 차단됩니다.
        mockMvc.perform(get("/api/v1/protected-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 만료된_액세스_토큰으로는_보호된_API에_접근할_수_없다() throws Exception {
        // given: 서명은 유효하지만 만료 시각이 지난 access token
        String expiredAccessToken = createExpiredAccessToken();

        // when & then: 401 Unauthorized로 차단됩니다.
        mockMvc.perform(get("/api/v1/protected-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken))
                .andExpect(status().isUnauthorized());
    }

    private String createExpiredAccessToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        Date past = new Date(System.currentTimeMillis() - 60_000);

        return Jwts.builder()
                .subject("1")
                .issuedAt(new Date(past.getTime() - 1_000))
                .expiration(past)
                .claim("tokenType", "ACCESS")
                .signWith(key)
                .compact();
    }
}
