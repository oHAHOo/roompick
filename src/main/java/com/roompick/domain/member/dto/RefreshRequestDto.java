package com.roompick.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDto(
    @NotBlank
    String refreshToken
) {
}
