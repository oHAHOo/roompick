package com.roompick.domain.accommodation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.facade.AccommodationFacade;
import com.roompick.global.common.ApiResponseDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/accommodations")
@RequiredArgsConstructor
public class AccommodationController {

    private final AccommodationFacade accommodationFacade;

    /**
     * 등록된 숙소의 기본 정보와 소속 객실 목록을 조회합니다.
     */
    @GetMapping("/{accommodationId}")
    public ResponseEntity<ApiResponseDto<AccommodationDetailResponseDto>> getAccommodationDetail(
        @PathVariable Long accommodationId
    ) {
        AccommodationDetailResponseDto result = accommodationFacade.getAccommodationDetail(accommodationId);

        ResponseEntity<ApiResponseDto<AccommodationDetailResponseDto>> response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponseDto.success(
                    "숙소 상세 조회에 성공했습니다.",
                    result
                ));

        return response;
    }
}
