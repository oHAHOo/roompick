package com.roompick.domain.admin.accommodation.dto.request;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자의 숙소 등록 요청 정보를 전달합니다.
 */
public record AccommodationCreateRequestDto(

    @NotBlank(message = "숙소 이름은 필수입니다.")
    @Size(
        max = 100,
        message = "숙소 이름은 100자 이하여야 합니다."
    )
    String name,

    @NotBlank(message = "숙소 주소는 필수입니다.")
    String address,

    String description,

    @NotNull(message = "체크인 시간은 필수입니다.")
    @JsonFormat(pattern = "HH:mm:ss")
    LocalTime checkInTime,

    @NotNull(message = "체크아웃 시간은 필수입니다.")
    @JsonFormat(pattern = "HH:mm:ss")
    LocalTime checkOutTime

) {
}
