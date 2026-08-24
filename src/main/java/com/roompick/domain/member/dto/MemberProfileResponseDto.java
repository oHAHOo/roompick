package com.roompick.domain.member.dto;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.entity.MemberRole;

public record MemberProfileResponseDto(
    Long memberId,
    String email,
    String name,
    MemberRole role
) {

    public static MemberProfileResponseDto from(Member member) {
        return new MemberProfileResponseDto(
            member.getId(),
            member.getEmail(),
            member.getName(),
            member.getRole()
        );
    }
}
