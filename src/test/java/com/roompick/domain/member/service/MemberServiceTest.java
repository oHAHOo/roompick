package com.roompick.domain.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    void 회원가입에_성공한다() {
        // given: 중복되지 않은 이메일로 회원가입을 요청합니다.
        given(memberRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when: 회원가입을 수행합니다.
        Member member = memberService.signup("test@example.com", "Password123!", "길동");

        // then: 비밀번호가 암호화된 회원이 저장됩니다.
        assertThat(member.getEmail()).isEqualTo("test@example.com");
        assertThat(member.getPassword()).isEqualTo("encoded-password");
        assertThat(member.getName()).isEqualTo("길동");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void 이메일이_중복되면_회원가입에_실패한다() {
        // given: 이미 가입된 이메일입니다.
        given(memberRepository.existsByEmail("test@example.com")).willReturn(true);

        // when & then: DUPLICATED_EMAIL 예외가 발생합니다.
        assertThatThrownBy(() -> memberService.signup("test@example.com", "Password123!", "길동"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.DUPLICATED_EMAIL);
    }

    @Test
    void 로그인에_성공한다() {
        // given: 가입된 회원과 일치하는 비밀번호가 있습니다.
        Member member = Member.create("test@example.com", "encoded-password", "길동");
        given(memberRepository.findByEmail("test@example.com")).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches("Password123!", "encoded-password")).willReturn(true);

        // when: 이메일·비밀번호로 인증합니다.
        Member authenticated = memberService.authenticate("test@example.com", "Password123!");

        // then: 조회된 회원이 그대로 반환됩니다.
        assertThat(authenticated).isEqualTo(member);
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인에_실패한다() {
        // given: 가입된 회원은 있지만 비밀번호가 일치하지 않습니다.
        Member member = Member.create("test@example.com", "encoded-password", "길동");
        given(memberRepository.findByEmail("test@example.com")).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        // when & then: INVALID_LOGIN 예외가 발생합니다.
        assertThatThrownBy(() -> memberService.authenticate("test@example.com", "wrong-password"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_LOGIN);
    }

    @Test
    void 존재하지_않는_이메일로_로그인하면_실패한다() {
        // given: 해당 이메일로 가입된 회원이 없습니다.
        given(memberRepository.findByEmail("unknown@example.com")).willReturn(java.util.Optional.empty());

        // when & then: INVALID_LOGIN 예외가 발생합니다.
        assertThatThrownBy(() -> memberService.authenticate("unknown@example.com", "Password123!"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_LOGIN);
    }
}
