package com.roompick.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.global.security.JwtTokenProvider;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 공개_API는_인증_없이_접근할_수_있다() throws Exception {
        // 컨트롤러가 아직 없어 정상 상태 코드는 아니지만, 인증 필터에서 401/403으로 막히지 않는지만 확인한다.
        MvcResult result = mockMvc.perform(get("/api/v1/rooms/1")).andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    void 인증_토큰이_없으면_보호된_API_접근이_차단된다() throws Exception {
        mockMvc.perform(get("/api/v1/protected-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void USER_권한으로는_관리자_API에_접근할_수_없다() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);

        mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void Refresh_Token으로는_일반_API에_접근할_수_없다() throws Exception {
        String refreshToken = jwtTokenProvider.createRefreshToken(1L);

        mockMvc.perform(get("/api/v1/protected-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }
}
