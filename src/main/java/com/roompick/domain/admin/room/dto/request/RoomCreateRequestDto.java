package com.roompick.domain.admin.room.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RoomCreateRequestDto(

    @NotBlank(message = "객실 번호는 필수입니다.")
    @Size(
        max = 30,
        message = "객실 번호는 30자 이하로 입력해야 합니다."
    )
    String roomNumber,

    @NotBlank(message = "객실 이름은 필수입니다.")
    @Size(
        max = 100,
        message = "객실 이름은 100자 이하로 입력해야 합니다."
    )
    String name,

    String description,

    @PositiveOrZero(message = "1박 가격은 0원 이상이어야 합니다.")
    long pricePerNight,

    @Positive(message = "기준 인원은 1명 이상이어야 합니다.")
    int standardCapacity,

    @Positive(message = "최대 인원은 1명 이상이어야 합니다.")
    int maxCapacity

) {
}
