package com.roompick.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.member.dto.LoginResponseDto;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.member.repository.TokenBlacklistRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.security.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private TokenService tokenService;

    @Test
    void 토큰_발급에_성공한다() {
        // given: 회원 ID와 권한이 주어집니다.
        given(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");

        // when: 토큰을 발급합니다.
        LoginResponseDto result = tokenService.issue(1L, MemberRole.USER);

        // then: JwtTokenProvider가 만든 access/refresh 토큰이 그대로 반환됩니다.
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void 리프레시_토큰으로_재발급에_성공한다() {
        // given: 유효하고 블랙리스트에 없는 리프레시 토큰과 존재하는 회원이 있습니다.
        Member member = Member.create("test@example.com", "encoded-password", "길동");
        ReflectionTestUtils.setField(member, "id", 1L);

        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.isRefreshToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getJti("refresh-token")).willReturn("refresh-jti");
        given(tokenBlacklistRepository.isBlacklisted("refresh-jti")).willReturn(false);
        given(jwtTokenProvider.getMemberId("refresh-token")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(jwtTokenProvider.getRemainingSeconds("refresh-token")).willReturn(1209600L);
        given(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("new-refresh-token");

        // when: 리프레시 토큰으로 재발급을 요청합니다.
        LoginResponseDto result = tokenService.reissue("refresh-token");

        // then: 새 토큰 쌍이 발급되고, 사용한 리프레시 토큰은 블랙리스트에 등록됩니다.
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        verify(tokenBlacklistRepository).blacklist("refresh-jti", 1209600L);
    }

    @Test
    void 유효하지_않은_토큰이면_재발급에_실패한다() {
        // given: 서명·만료 검증에 실패하는 토큰입니다.
        given(jwtTokenProvider.validateToken("invalid-token")).willReturn(false);

        // when & then: INVALID_REFRESH_TOKEN 예외가 발생하고 블랙리스트에는 등록하지 않습니다.
        assertThatThrownBy(() -> tokenService.reissue("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(tokenBlacklistRepository, never()).blacklist(anyString(), anyLong());
    }

    @Test
    void Access_Token으로는_재발급에_실패한다() {
        // given: 서명은 유효하지만 타입이 REFRESH가 아닌 토큰입니다.
        given(jwtTokenProvider.validateToken("access-token")).willReturn(true);
        given(jwtTokenProvider.isRefreshToken("access-token")).willReturn(false);

        // when & then: INVALID_REFRESH_TOKEN 예외가 발생합니다.
        assertThatThrownBy(() -> tokenService.reissue("access-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 블랙리스트에_등록된_토큰이면_재발급에_실패한다() {
        // given: 서명·타입은 유효하지만 이미 사용되어 블랙리스트에 등록된 토큰입니다.
        given(jwtTokenProvider.validateToken("used-refresh-token")).willReturn(true);
        given(jwtTokenProvider.isRefreshToken("used-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getJti("used-refresh-token")).willReturn("used-jti");
        given(tokenBlacklistRepository.isBlacklisted("used-jti")).willReturn(true);

        // when & then: INVALID_REFRESH_TOKEN 예외가 발생합니다.
        assertThatThrownBy(() -> tokenService.reissue("used-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 존재하지_않는_회원이면_재발급에_실패한다() {
        // given: 토큰은 유효하지만 토큰에 담긴 회원 ID가 더 이상 존재하지 않습니다.
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.isRefreshToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getJti("refresh-token")).willReturn("refresh-jti");
        given(tokenBlacklistRepository.isBlacklisted("refresh-jti")).willReturn(false);
        given(jwtTokenProvider.getMemberId("refresh-token")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then: INVALID_REFRESH_TOKEN 예외가 발생합니다.
        assertThatThrownBy(() -> tokenService.reissue("refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 로그아웃하면_액세스_토큰과_리프레시_토큰을_모두_블랙리스트에_등록한다() {
        // given: 로그아웃할 access 토큰과 refresh 토큰이 있습니다.
        given(jwtTokenProvider.getJti("access-token")).willReturn("access-jti");
        given(jwtTokenProvider.getRemainingSeconds("access-token")).willReturn(1800L);
        given(jwtTokenProvider.getJti("refresh-token")).willReturn("refresh-jti");
        given(jwtTokenProvider.getRemainingSeconds("refresh-token")).willReturn(1209600L);

        // when: 로그아웃을 수행합니다.
        tokenService.logout("access-token", "refresh-token");

        // then: 두 토큰 모두 각자의 남은 만료 시간만큼 블랙리스트에 등록됩니다.
        verify(tokenBlacklistRepository).blacklist("access-jti", 1800L);
        verify(tokenBlacklistRepository).blacklist("refresh-jti", 1209600L);
    }
}
