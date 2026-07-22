package com.roompick.domain.member.facade;

import com.roompick.domain.member.dto.LoginRequestDto;
import com.roompick.domain.member.dto.LoginResponseDto;
import com.roompick.domain.member.dto.SignupRequestDto;
import com.roompick.domain.member.dto.SignupResponseDto;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.service.MemberService;
import com.roompick.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthFacade {

    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;

    public SignupResponseDto signup(SignupRequestDto request) {
        Member member = memberService.signup(request.email(), request.password(), request.name());
        return SignupResponseDto.from(member);
    }

    public LoginResponseDto login(LoginRequestDto request) {
        Member member = memberService.authenticate(request.email(), request.password());

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        return LoginResponseDto.of(accessToken, refreshToken);
    }
}
