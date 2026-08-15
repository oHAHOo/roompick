package com.roompick.domain.admin.specialoffer.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SpecialOfferCreateRequestDto(
    @NotNull(message = "특가 가격은 필수입니다.")
    @Positive(message = "특가 가격은 0보다 커야 합니다.")
    Long price,

    @NotNull(message = "판매 시작 시각은 필수입니다.")
    LocalDateTime startsAt,

    @NotNull(message = "판매 종료 시각은 필수입니다.")
    LocalDateTime endsAt
) {}
