package com.roompick.domain.accommodation.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;

    @Transactional(readOnly = true)
    public Accommodation findById(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.ACCOMMODATION_NOT_FOUND
                )
            );
    }

    @Transactional
    public Accommodation createAccommodation(
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime
    ) {
        Accommodation accommodation =
            Accommodation.create(
                name,
                address,
                description,
                checkInTime,
                checkOutTime
            );

        return accommodationRepository.save(accommodation);
    }
}
