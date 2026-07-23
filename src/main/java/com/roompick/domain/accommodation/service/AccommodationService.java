package com.roompick.domain.accommodation.service;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * 숙소 도메인의 생성과 조회 정책을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;


    @Transactional
    public Accommodation createAccommodation(
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime
    ) {
        Accommodation accommodation = Accommodation.create(
            name,
            address,
            description,
            checkInTime,
            checkOutTime
        );

        return accommodationRepository.save(accommodation);
    }
}
