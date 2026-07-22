package com.roompick.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequestDto(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "비밀번호는 영문과 숫자를 포함해 8자 이상 64자 이하로 입력해야 합니다."
        )
        String password,

        @NotBlank
        @Size(min = 1, max = 50)
        String name
) {
}
