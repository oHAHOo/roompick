package com.roompick.domain.admin.accommodation.dto.request;

import com.roompick.domain.accommodation.entity.AccommodationStatus;

import jakarta.validation.constraints.NotNull;

public record AccommodationStatusUpdateRequestDto(

    @NotNull AccommodationStatus status

) {
}
