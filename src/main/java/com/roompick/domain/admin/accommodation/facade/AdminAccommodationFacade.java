package com.roompick.domain.admin.accommodation.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.accommodation.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccommodationFacade {

    private final AccommodationService accommodationService;

    public AccommodationCreateResponseDto createAccommodation(
        AccommodationCreateRequestDto request
    ) {
        Accommodation accommodation =
            accommodationService.createAccommodation(
                request.name(),
                request.address(),
                request.description(),
                request.checkInTimeAsLocalTime(),
                request.checkOutTimeAsLocalTime()
            );

        return AccommodationCreateResponseDto.from(
            accommodation
        );
    }
}
