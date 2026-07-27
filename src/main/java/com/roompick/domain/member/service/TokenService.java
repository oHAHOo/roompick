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
        validateRefreshTokenType(refreshToken);
        consumeOrReject(refreshToken);

        Long memberId = jwtTokenProvider.getMemberId(refreshToken);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issue(member.getId(), member.getRole());
    }

    public void logout(Long memberId, String accessToken, String refreshToken) {
        validateOwnedRefreshToken(memberId, refreshToken);

        blacklist(accessToken);
        blacklist(refreshToken);
    }

    private void validateRefreshTokenType(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateOwnedRefreshToken(Long memberId, String refreshToken) {
        validateRefreshTokenType(refreshToken);

        if (!memberId.equals(jwtTokenProvider.getMemberId(refreshToken))) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    /**
     * Refresh Token을 원자적으로 소모합니다. 이미 사용된(또는 로그아웃된) 토큰이면 거절합니다.
     * check-then-act이 아닌 단일 원자 연산으로 처리해 동시 요청에서도 하나만 성공하도록 보장합니다.
     */
    private void consumeOrReject(String refreshToken) {
        String jti = jwtTokenProvider.getJti(refreshToken);
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(refreshToken);

        if (!tokenBlacklistRepository.consume(jti, remainingSeconds)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void blacklist(String token) {
        String jti = jwtTokenProvider.getJti(token);
        long remainingSeconds = jwtTokenProvider.getRemainingSeconds(token);
        tokenBlacklistRepository.consume(jti, remainingSeconds);
    }
}
