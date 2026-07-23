package com.roompick.domain.admin.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.dto.response.AccommodationCreateResponseDto;

import lombok.RequiredArgsConstructor;

/**
 * 관리자의 숙소 등록 유스케이스를 조율합니다.
 */
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
                request.checkInTime(),
                request.checkOutTime()
            );

        return AccommodationCreateResponseDto.from(accommodation);
    }
}
