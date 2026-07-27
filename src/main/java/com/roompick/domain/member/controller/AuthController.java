package com.roompick.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.member.dto.LoginRequestDto;
import com.roompick.domain.member.dto.LoginResponseDto;
import com.roompick.domain.member.dto.LogoutRequestDto;
import com.roompick.domain.member.dto.RefreshRequestDto;
import com.roompick.domain.member.dto.SignupRequestDto;
import com.roompick.domain.member.dto.SignupResponseDto;
import com.roompick.domain.member.facade.AuthFacade;
import com.roompick.global.common.ApiResponseDto;
import com.roompick.global.security.AuthMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String TOKEN_PREFIX = "Bearer ";

    private final AuthFacade authFacade;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponseDto<SignupResponseDto>> signup(
        @Valid @RequestBody SignupRequestDto request
    ) {
        SignupResponseDto result = authFacade.signup(request);

        ResponseEntity<ApiResponseDto<SignupResponseDto>> response = ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponseDto.success("회원가입이 완료되었습니다.", result));

        return response;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> login(
        @Valid @RequestBody LoginRequestDto request
    ) {
        LoginResponseDto result = authFacade.login(request);

        ResponseEntity<ApiResponseDto<LoginResponseDto>> response = ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.AUTHORIZATION, result.tokenType() + " " + result.accessToken())
            .body(ApiResponseDto.success("로그인에 성공했습니다.", result));

        return response;
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> refresh(
        @Valid @RequestBody RefreshRequestDto request
    ) {
        LoginResponseDto result = authFacade.refresh(request);

        ResponseEntity<ApiResponseDto<LoginResponseDto>> response = ResponseEntity
            .status(HttpStatus.OK)
            .header(HttpHeaders.AUTHORIZATION, result.tokenType() + " " + result.accessToken())
            .body(ApiResponseDto.success("토큰이 재발급되었습니다.", result));

        return response;
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(
        @AuthenticationPrincipal AuthMember authMember,
        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
        @Valid @RequestBody LogoutRequestDto request
    ) {
        String accessToken = authorizationHeader.substring(TOKEN_PREFIX.length());
        authFacade.logout(authMember.memberId(), accessToken, request.refreshToken());

        ResponseEntity<ApiResponseDto<Void>> response = ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponseDto.success("로그아웃되었습니다."));

        return response;
    }
}
