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
        given(memberRepository.existsByEmail("test@example.com")).willReturn(false);
        given(memberRepository.existsByName("길동")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("encoded-password");
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> invocation.getArgument(0));

        Member member = memberService.signup("test@example.com", "Password123!", "길동");

        assertThat(member.getEmail()).isEqualTo("test@example.com");
        assertThat(member.getPassword()).isEqualTo("encoded-password");
        assertThat(member.getName()).isEqualTo("길동");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void 이메일이_중복되면_회원가입에_실패한다() {
        given(memberRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup("test@example.com", "Password123!", "길동"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException)exception).getErrorCode())
            .isEqualTo(ErrorCode.DUPLICATED_EMAIL);
    }

    @Test
    void 닉네임이_중복되면_회원가입에_실패한다() {
        given(memberRepository.existsByEmail("test@example.com")).willReturn(false);
        given(memberRepository.existsByName("길동")).willReturn(true);

        assertThatThrownBy(() -> memberService.signup("test@example.com", "Password123!", "길동"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException)exception).getErrorCode())
            .isEqualTo(ErrorCode.DUPLICATED_NICKNAME);
    }

    @Test
    void 로그인에_성공한다() {
        Member member = Member.create("test@example.com", "encoded-password", "길동");
        given(memberRepository.findByEmail("test@example.com")).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches("Password123!", "encoded-password")).willReturn(true);

        Member authenticated = memberService.authenticate("test@example.com", "Password123!");

        assertThat(authenticated).isEqualTo(member);
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인에_실패한다() {
        Member member = Member.create("test@example.com", "encoded-password", "길동");
        given(memberRepository.findByEmail("test@example.com")).willReturn(java.util.Optional.of(member));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> memberService.authenticate("test@example.com", "wrong-password"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException)exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_LOGIN);
    }

    @Test
    void 존재하지_않는_이메일로_로그인하면_실패한다() {
        given(memberRepository.findByEmail("unknown@example.com")).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> memberService.authenticate("unknown@example.com", "Password123!"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException)exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_LOGIN);
    }
}
