package com.roompick.domain.member.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roompick.domain.member.dto.LoginRequestDto;
import com.roompick.domain.member.dto.LogoutRequestDto;
import com.roompick.domain.member.dto.RefreshRequestDto;
import com.roompick.domain.member.dto.SignupRequestDto;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void 회원가입에_성공한다() throws Exception {
        // given: 유효한 회원가입 요청입니다.
        SignupRequestDto request = new SignupRequestDto("test@example.com", "Password123!", "길동");

        // when & then: 회원가입에 성공하고 회원 정보가 반환됩니다.
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberId").value(notNullValue()))
            .andExpect(jsonPath("$.data.email").value("test@example.com"))
            .andExpect(jsonPath("$.data.name").value("길동"));
    }

    @Test
    void 이메일이_중복되면_회원가입에_실패한다() throws Exception {
        // given: 이미 가입된 이메일이 있습니다.
        SignupRequestDto request = new SignupRequestDto("dup@example.com", "Password123!", "홍길동");

        mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        SignupRequestDto duplicated = new SignupRequestDto("dup@example.com", "Password123!", "김철수");

        // when & then: 동일 이메일로 다시 가입하면 DUPLICATED_EMAIL로 실패합니다.
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicated)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_001"));
    }

    @Test
    void 요청_값이_올바르지_않으면_회원가입에_실패한다() throws Exception {
        // given: 이메일 형식이 잘못되고 비밀번호·이름이 규칙을 어긴 요청입니다.
        SignupRequestDto request = new SignupRequestDto("not-an-email", "short", "");

        // when & then: 요청 값 검증에 실패하고 INVALID_INPUT_VALUE(COMMON_001)로 응답합니다.
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void 로그인에_성공하고_토큰을_발급받는다() throws Exception {
        // given: 가입된 회원이 있습니다.
        SignupRequestDto signupRequest = new SignupRequestDto("login@example.com", "Password123!", "로그인테스트");
        mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequestDto loginRequest = new LoginRequestDto("login@example.com", "Password123!");

        // when & then: 올바른 이메일·비밀번호로 로그인하면 access/refresh 토큰을 발급받습니다.
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(header().exists("Authorization"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.data.refreshToken").value(notNullValue()))
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인에_실패한다() throws Exception {
        // given: 가입된 회원이 있고, 로그인 요청에는 틀린 비밀번호를 담습니다.
        SignupRequestDto signupRequest = new SignupRequestDto("wrongpw@example.com", "Password123!", "비번틀림");
        mockMvc.perform(post("/api/v1/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequestDto loginRequest = new LoginRequestDto("wrongpw@example.com", "WrongPassword123!");

        // when & then: 비밀번호가 일치하지 않아 INVALID_LOGIN(MEMBER_003)으로 실패합니다.
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_003"));
    }

    @Test
    void 리프레시_토큰으로_토큰을_재발급받는다() throws Exception {
        // given: 로그인해서 리프레시 토큰을 발급받았습니다.
        signup("refresh@example.com", "Password123!", "리프레시테스트");
        String refreshToken = login("refresh@example.com", "Password123!").get("refreshToken").asText();

        RefreshRequestDto request = new RefreshRequestDto(refreshToken);

        // when & then: 그 리프레시 토큰으로 재발급을 요청하면 새 토큰 쌍을 받습니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(header().exists("Authorization"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").value(notNullValue()))
            .andExpect(jsonPath("$.data.refreshToken").value(notNullValue()));
    }

    @Test
    void 위조된_리프레시_토큰으로는_재발급에_실패한다() throws Exception {
        // given: 서명이 유효하지 않은(위조된) 토큰입니다.
        RefreshRequestDto request = new RefreshRequestDto("invalid.refresh.token");

        // when & then: INVALID_REFRESH_TOKEN(MEMBER_004)으로 실패합니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    void Access_Token으로는_재발급에_실패한다() throws Exception {
        // given: 로그인으로 access token을 발급받았습니다.
        signup("accessasrefresh@example.com", "Password123!", "엑세스토큰테스트");
        String accessToken = login("accessasrefresh@example.com", "Password123!").get("accessToken").asText();

        RefreshRequestDto request = new RefreshRequestDto(accessToken);

        // when & then: access token을 refreshToken 자리에 넣으면 타입 불일치로 실패합니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    void 사용한_리프레시_토큰은_재사용할_수_없다() throws Exception {
        // given: 리프레시 토큰으로 한 번 재발급을 받아 그 토큰을 소모했습니다.
        signup("rotate@example.com", "Password123!", "회전테스트");
        String refreshToken = login("rotate@example.com", "Password123!").get("refreshToken").asText();

        RefreshRequestDto request = new RefreshRequestDto(refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());

        // when & then: 같은 리프레시 토큰으로 다시 요청하면 블랙리스트에 걸려 실패합니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    void 로그아웃에_성공한다() throws Exception {
        // given: 로그인해서 access/refresh 토큰을 모두 가지고 있습니다.
        signup("logout@example.com", "Password123!", "로그아웃테스트");
        JsonNode tokens = login("logout@example.com", "Password123!");

        LogoutRequestDto request = new LogoutRequestDto(tokens.get("refreshToken").asText());

        // when & then: access token 헤더와 refresh token body로 로그아웃하면 성공합니다.
        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 로그아웃하면_기존_액세스_토큰을_재사용할_수_없다() throws Exception {
        // given: 로그인 후 로그아웃까지 완료해서 access token이 블랙리스트에 등록되었습니다.
        signup("logoutaccess@example.com", "Password123!", "액세스무효화테스트");
        JsonNode tokens = login("logoutaccess@example.com", "Password123!");
        String accessToken = tokens.get("accessToken").asText();

        LogoutRequestDto request = new LogoutRequestDto(tokens.get("refreshToken").asText());

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // when & then: 로그아웃 전에 쓰던 access token으로 다른 보호된 API를 호출하면 401입니다.
        mockMvc.perform(get("/api/v1/protected-test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃하면_기존_리프레시_토큰도_재사용할_수_없다() throws Exception {
        // given: 로그인 후 로그아웃까지 완료해서 refresh token도 블랙리스트에 등록되었습니다.
        signup("logoutrefresh@example.com", "Password123!", "리프레시무효화테스트");
        JsonNode tokens = login("logoutrefresh@example.com", "Password123!");
        String refreshToken = tokens.get("refreshToken").asText();

        LogoutRequestDto logoutRequest = new LogoutRequestDto(refreshToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)));

        RefreshRequestDto refreshRequest = new RefreshRequestDto(refreshToken);

        // when & then: 로그아웃한 refresh token으로 재발급을 시도하면 실패합니다.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    void 인증_없이_로그아웃을_호출하면_실패한다() throws Exception {
        // given: Authorization 헤더 없이 로그아웃을 요청합니다.
        LogoutRequestDto request = new LogoutRequestDto("any-refresh-token");

        // when & then: 인증되지 않아 401로 실패합니다.
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void 위조된_리프레시_토큰으로_로그아웃하면_500이_아닌_401로_실패한다() throws Exception {
        // given: 유효한 access token은 있지만 body의 refresh token은 위조되었습니다.
        signup("logoutinvalid@example.com", "Password123!", "로그아웃위조테스트");
        String accessToken = login("logoutinvalid@example.com", "Password123!").get("accessToken").asText();

        LogoutRequestDto request = new LogoutRequestDto("invalid.refresh.token");

        // when & then: 예외가 GlobalExceptionHandler의 500으로 새지 않고 401로 처리됩니다.
        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    void Access_Token을_리프레시_토큰_자리에_넣고_로그아웃하면_실패한다() throws Exception {
        // given: body에 refresh token 대신 access token을 담아 로그아웃을 요청합니다.
        signup("logoutaccessasrefresh@example.com", "Password123!", "로그아웃타입테스트");
        String accessToken = login("logoutaccessasrefresh@example.com", "Password123!").get("accessToken").asText();

        LogoutRequestDto request = new LogoutRequestDto(accessToken);

        // when & then: 타입 불일치로 401 실패합니다.
        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    void 다른_회원의_리프레시_토큰으로_로그아웃하면_실패하고_그_토큰은_무효화되지_않는다() throws Exception {
        // given: 서로 다른 두 회원이 각각 로그인해서 토큰을 가지고 있습니다.
        signup("victim@example.com", "Password123!", "피해자테스트");
        JsonNode victimTokens = login("victim@example.com", "Password123!");
        String victimRefreshToken = victimTokens.get("refreshToken").asText();

        signup("attacker@example.com", "Password123!", "공격자테스트");
        String attackerAccessToken = login("attacker@example.com", "Password123!").get("accessToken").asText();

        LogoutRequestDto request = new LogoutRequestDto(victimRefreshToken);

        // when: 공격자가 자신의 access token과 피해자의 refresh token으로 로그아웃을 시도하면
        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + attackerAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MEMBER_004"));

        // then: 소유자가 다르므로 거절되고, 피해자의 refresh token은 여전히 재발급에 사용할 수 있다.
        RefreshRequestDto refreshRequest = new RefreshRequestDto(victimRefreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk());
    }

    private void signup(String email, String password, String name) throws Exception {
        SignupRequestDto request = new SignupRequestDto(email, password, name);

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private JsonNode login(String email, String password) throws Exception {
        LoginRequestDto request = new LoginRequestDto(email, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data");
    }
}
