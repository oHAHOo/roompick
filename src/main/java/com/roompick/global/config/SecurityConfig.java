package com.roompick.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.common.ErrorResponseDto;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http
    ) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            // 인증 및 권한 예외 응답 설정
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(
                    (request, response, authenticationException) ->
                        writeErrorResponse(
                            response,
                            ErrorCode.UNAUTHORIZED
                        )
                )
                .accessDeniedHandler(
                    (request, response, accessDeniedException) ->
                        writeErrorResponse(
                            response,
                            ErrorCode.FORBIDDEN
                        )
                )
            )

            // URL별 접근 권한 설정
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/admin/**")
                .hasRole("ADMIN")

                // 인증 기능이 완성되기 전까지
                // 관리자 외 API는 임시 허용
                .anyRequest()
                .permitAll()
            );

        return http.build();
    }

    private void writeErrorResponse(
        HttpServletResponse response,
        ErrorCode errorCode
    ) throws IOException {

        response.setStatus(
            errorCode.getHttpStatus().value()
        );

        response.setContentType(
            MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );

        ErrorResponseDto errorResponse =
            ErrorResponseDto.from(errorCode);

        objectMapper.writeValue(
            response.getWriter(),
            errorResponse
        );
    }
}
