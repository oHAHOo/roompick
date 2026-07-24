package com.roompick.domain.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.member.dto.LoginResponseDto;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.member.repository.TokenBlacklistRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final MemberRepository memberRepository;

    public LoginResponseDto issue(Long memberId, MemberRole role) {
        String accessToken = jwtTokenProvider.createAccessToken(memberId, role);
        String refreshToken = jwtTokenProvider.createRefreshToken(memberId);
        return LoginResponseDto.of(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public LoginResponseDto reissue(String refreshToken) {
        validateRefreshToken(refreshToken);

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new BusinessException(
            ErrorCode.INVALID_REFRESH_TOKEN));

        blacklist(refreshToken);

        return issue(memberId, member.getRole());
    }

    public void logout(String accessToken, String refreshToken) {
        blacklist(accessToken);
        blacklist(refreshToken);
    }


    private void validateRefreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (tokenBlacklistRepository.isBlacklisted(jwtTokenProvider.getJti(refreshToken))){
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void blacklist(String token) {
        String jti = jwtTokenProvider.getJti(token);
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(token);
        tokenBlacklistRepository.blacklist(jti, remainingSeconds);
    }
}
