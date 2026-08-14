package com.roompick.domain.admin.accommodation.dto.request;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccommodationCreateRequestDto(

    @NotBlank(message = "숙소 이름은 필수입니다.")
    @Size(
        max = 100,
        message = "숙소 이름은 100자 이하로 입력해야 합니다."
    )
    String name,

    @NotBlank(message = "숙소 주소는 필수입니다.")
    @Size(
        max = 255,
        message = "숙소 주소는 255자 이하로 입력해야 합니다."
    )
    String address,

    String description,

    @NotNull(message = "숙소 위도는 필수입니다.")
    @DecimalMin(
        value = "-90",
        message = "숙소 위도는 -90 이상이어야 합니다."
    )
    @DecimalMax(
        value = "90",
        message = "숙소 위도는 90 이하여야 합니다."
    )
    BigDecimal latitude,

    @NotNull(message = "숙소 경도는 필수입니다.")
    @DecimalMin(
        value = "-180",
        message = "숙소 경도는 -180 이상이어야 합니다."
    )
    @DecimalMax(
        value = "180",
        message = "숙소 경도는 180 이하여야 합니다."
    )
    BigDecimal longitude,

    @NotBlank(message = "체크인 시간은 필수입니다.")
    @Pattern(
        regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$",
        message = "체크인 시간은 HH:mm:ss 형식이어야 합니다."
    )
    String checkInTime,

    @NotBlank(message = "체크아웃 시간은 필수입니다.")
    @Pattern(
        regexp = "^(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$",
        message = "체크아웃 시간은 HH:mm:ss 형식이어야 합니다."
    )
    String checkOutTime

) {

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    public LocalTime checkInTimeAsLocalTime() {
        return LocalTime.parse(
            checkInTime,
            TIME_FORMATTER
        );
    }

    public LocalTime checkOutTimeAsLocalTime() {
        return LocalTime.parse(
            checkOutTime,
            TIME_FORMATTER
        );
    }
}
