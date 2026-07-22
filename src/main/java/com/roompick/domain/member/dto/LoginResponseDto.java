package com.roompick.domain.member.dto;

public record LoginResponseDto(
        String accessToken,
        String refreshToken,
        String tokenType
) {

    private static final String TOKEN_TYPE = "Bearer";

    public static LoginResponseDto of(String accessToken, String refreshToken) {
        return new LoginResponseDto(accessToken, refreshToken, TOKEN_TYPE);
    }
}
