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

    /**
     * 숙소 ID로 숙소를 조회합니다.
     *
     * 조회 결과가 없으면 공통 예외 형식으로
     * ACCOMMODATION_NOT_FOUND 오류를 반환합니다.
     */
    @Transactional(readOnly = true)
    public Accommodation findById(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.ACCOMMODATION_NOT_FOUND)
            );
    }

    /**
     * 새로운 숙소를 등록합니다.
     *
     * 숙소 생성 규칙과 입력값 검증은
     * Accommodation Entity의 create() 메서드에서 처리합니다.
     */
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
