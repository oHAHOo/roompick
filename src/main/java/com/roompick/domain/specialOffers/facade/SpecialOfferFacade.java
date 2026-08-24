package com.roompick.domain.specialOffers.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.dto.SpecialOfferListResponseDto;
import com.roompick.domain.specialOffers.service.SpecialOfferService;

import lombok.RequiredArgsConstructor;

/**
 * 특가 공개 조회 흐름을 조율합니다.
 *
 * 점유 요청·상태 조회는 OfferOccupyFacade가 별도로 담당합니다.
 */
@Component
@RequiredArgsConstructor
public class SpecialOfferFacade {

    private final SpecialOfferService specialOfferService;

    public List<SpecialOfferListResponseDto> getActiveOffers() {
        return specialOfferService.findActiveSummaries();
    }
}
