package com.roompick.global.security;

import com.roompick.domain.member.entity.MemberRole;

public record AuthMember(
    Long memberId,
    MemberRole role
) {
}
