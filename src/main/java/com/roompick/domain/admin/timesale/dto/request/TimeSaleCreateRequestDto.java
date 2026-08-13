package com.roompick.domain.admin.timesale.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자의 타임세일 등록 요청입니다.
 *
 * roomId가 null이면 숙소 전체에 적용하고,
 * 값이 존재하면 해당 객실에만 적용합니다.
 */
public record TimeSaleCreateRequestDto(

    Long roomId,

    @NotNull(message = "할인율은 필수입니다.")
    @Min(
        value = 1,
        message = "할인율은 1% 이상이어야 합니다."
    )
    @Max(
        value = 99,
        message = "할인율은 99% 이하여야 합니다."
    )
    Integer discountRate,

    @NotNull(message = "타임세일 시작 시각은 필수입니다.")
    LocalDateTime startAt,

    @NotNull(message = "타임세일 종료 시각은 필수입니다.")
    LocalDateTime endAt

) {
}
