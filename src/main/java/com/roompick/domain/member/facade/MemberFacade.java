package com.roompick.domain.member.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.member.dto.MemberProfileResponseDto;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.service.MemberService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MemberFacade {

    private final MemberService memberService;

    public MemberProfileResponseDto getMyProfile(Long memberId) {
        Member member = memberService.findById(memberId);
        return MemberProfileResponseDto.from(member);
    }
}
