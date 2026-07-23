package com.roompick.domain.admin.accommodation.dto.response;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;

public record AccommodationCreateResponseDto(

    Long accommodationId,

    String name,

    String address,

    String description,

    @JsonFormat(pattern = "HH:mm:ss")
    LocalTime checkInTime,

    @JsonFormat(pattern = "HH:mm:ss")
    LocalTime checkOutTime,

    AccommodationStatus status

) {

    public static AccommodationCreateResponseDto from(
        Accommodation accommodation
    ) {
        return new AccommodationCreateResponseDto(
            accommodation.getId(),
            accommodation.getName(),
            accommodation.getAddress(),
            accommodation.getDescription(),
            accommodation.getCheckInTime(),
            accommodation.getCheckOutTime(),
            accommodation.getStatus()
        );
    }
}
