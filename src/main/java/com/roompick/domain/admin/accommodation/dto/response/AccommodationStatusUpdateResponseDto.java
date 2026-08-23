package com.roompick.domain.admin.accommodation.dto.response;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;

public record AccommodationStatusUpdateResponseDto(

    Long accommodationId,

    AccommodationStatus status

) {

    public static AccommodationStatusUpdateResponseDto from(
        Accommodation accommodation
    ) {
        return new AccommodationStatusUpdateResponseDto(
            accommodation.getId(),
            accommodation.getStatus()
        );
    }
}
