package com.roompick.domain.specialOffers.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.specialOffers.dto.SpecialOfferListResponseDto;
import com.roompick.domain.specialOffers.facade.SpecialOfferFacade;
import com.roompick.global.common.ApiResponseDto;

import lombok.RequiredArgsConstructor;

/**
 * 특가 공개 조회 API를 제공합니다.
 *
 * 점유 요청·상태 조회 API는 OfferOccupyController가 별도로 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/special-offers")
public class SpecialOfferController {

    private final SpecialOfferFacade specialOfferFacade;

    /**
     * 현재 판매 중인 특가 목록을 판매 종료가 임박한 순으로 조회합니다.
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SpecialOfferListResponseDto>>> getActiveOffers() {
        List<SpecialOfferListResponseDto> result = specialOfferFacade.getActiveOffers();

        ResponseEntity<ApiResponseDto<List<SpecialOfferListResponseDto>>> response = ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponseDto.success("판매 중인 특가 목록 조회에 성공했습니다.", result));

        return response;
    }
}
