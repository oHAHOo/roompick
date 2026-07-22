package com.roompick.domain.member.dto;

import com.roompick.domain.member.entity.Member;

public record SignupResponseDto(
    Long memberId,
    String email,
    String name
) {

    public static SignupResponseDto from(Member member) {
        return new SignupResponseDto(member.getId(), member.getEmail(), member.getName());
    }
}
