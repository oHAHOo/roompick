package com.roompick.domain.member.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.member.dto.LoginRequestDto;
import com.roompick.domain.member.dto.LoginResponseDto;
import com.roompick.domain.member.dto.RefreshRequestDto;
import com.roompick.domain.member.dto.SignupRequestDto;
import com.roompick.domain.member.dto.SignupResponseDto;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.service.MemberService;
import com.roompick.domain.member.service.TokenService;
import com.roompick.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthFacade {

    private final MemberService memberService;
    private final TokenService tokenService;

    public SignupResponseDto signup(SignupRequestDto request) {
        Member member = memberService.signup(request.email(), request.password(), request.name());
        return SignupResponseDto.from(member);
    }

    public LoginResponseDto login(LoginRequestDto request) {
        Member member = memberService.authenticate(request.email(), request.password());
        return tokenService.issue(member.getId(), member.getRole());
    }

    public LoginResponseDto refresh(RefreshRequestDto request) {
        return tokenService.reissue(request.refreshToken());
    }

    public void logout(String accessToken, String refreshToken) {
        tokenService.logout(accessToken, refreshToken);
    }
}
