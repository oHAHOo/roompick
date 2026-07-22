package com.roompick.domain.member.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Member signup(String email, String rawPassword, String name) {
        validateEmailNotDuplicated(email);
        validateNameNotDuplicated(name);

        String encodedPassword = passwordEncoder.encode(rawPassword);
        Member member = Member.create(email, encodedPassword, name);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Member authenticate(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_LOGIN));

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_LOGIN);
        }

        return member;
    }

    private void validateEmailNotDuplicated(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATED_EMAIL);
        }
    }

    private void validateNameNotDuplicated(String name) {
        if (memberRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATED_NICKNAME);
        }
    }
}
