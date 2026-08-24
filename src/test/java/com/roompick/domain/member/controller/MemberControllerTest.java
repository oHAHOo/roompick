package com.roompick.domain.member.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
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
import com.roompick.domain.member.dto.SignupRequestDto;
import com.roompick.global.common.ErrorCode;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("인증된 회원은 자신의 프로필을 조회할 수 있다")
    void 인증된_회원은_자신의_프로필을_조회할_수_있다() throws Exception {
        // given
        signup("me@example.com", "Password123!", "내정보테스트");
        String accessToken = login("me@example.com", "Password123!")
            .get("accessToken").asText();

        // when & then
        mockMvc.perform(
                get("/api/v1/members/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.memberId").value(notNullValue()))
            .andExpect(jsonPath("$.data.email").value("me@example.com"))
            .andExpect(jsonPath("$.data.name").value("내정보테스트"))
            .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("인증되지 않은 요청은 프로필을 조회할 수 없다")
    void 인증되지_않은_요청은_프로필을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
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
