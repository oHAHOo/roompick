package com.roompick.domain.member.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.member.dto.LoginRequestDto;
import com.roompick.domain.member.dto.LoginResponseDto;
import com.roompick.domain.member.dto.SignupRequestDto;
import com.roompick.domain.member.dto.SignupResponseDto;
import com.roompick.domain.member.facade.AuthFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

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
}
