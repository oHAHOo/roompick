package com.roompick.domain.accommodation.service;

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
}
