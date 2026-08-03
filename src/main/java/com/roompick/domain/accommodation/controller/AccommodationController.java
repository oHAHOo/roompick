package com.roompick.domain.accommodation.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationPageResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.facade.AccommodationFacade;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.global.common.ApiResponseDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/accommodations")
@RequiredArgsConstructor
public class AccommodationController {

    private final AccommodationFacade accommodationFacade;

    /**
     * 운영 중인 전체 숙소 목록을 페이지 단위로 조회합니다.
     *
     * 인증 없이 호출할 수 있으며 검색·필터 없이
     * W1 와이어프레임에 필요한 기본 숙소 목록을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<ApiResponseDto<AccommodationPageResponseDto>>
    getAccommodationList(
        @RequestParam(
            name = "page",
            defaultValue = "0"
        ) int page,
        @RequestParam(
            name = "size",
            defaultValue = "20"
        ) int size
    ) {
        AccommodationPageResponseDto result =
            accommodationFacade.getAccommodationList(
                page,
                size
            );

        ResponseEntity<ApiResponseDto<AccommodationPageResponseDto>> response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "숙소 목록 조회에 성공했습니다.",
                        result
                    )
                );

        return response;
    }

    /**
     * 요청한 일간 또는 주간 인기 숙소 목록을 조회합니다.
     *
     * 조회 개수의 기본값은 10이며,
     * 1개 이상 20개 이하만 요청할 수 있습니다.
     */
    @GetMapping("/popular")
    public ResponseEntity<
        ApiResponseDto<List<PopularAccommodationResponseDto>>
        > getPopularAccommodations(
        @RequestParam(
            name = "period",
            defaultValue = "DAILY"
        ) PopularAccommodationPeriod period,
        @RequestParam(
            name = "limit",
            defaultValue = "10"
        ) int limit
    ) {
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                period,
                limit
            );

        ResponseEntity<
            ApiResponseDto<List<PopularAccommodationResponseDto>>
            > response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "인기 숙소 목록 조회에 성공했습니다.",
                        result
                    )
                );

        return response;
    }

    /**
     * 특정 숙소에 소속된 운영 중인 객실 목록을 조회합니다.
     *
     * 숙소와 객실이 모두 운영 중인 경우에만
     * 와이어프레임에 필요한 객실 요약 정보를 반환합니다.
     */
    @GetMapping("/{accommodationId}/rooms")
    public ResponseEntity<ApiResponseDto<List<RoomListResponseDto>>>
    getRoomList(
        @PathVariable Long accommodationId
    ) {
        List<RoomListResponseDto> result =
            accommodationFacade.getRoomList(
                accommodationId
            );

        ResponseEntity<ApiResponseDto<List<RoomListResponseDto>>> response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "객실 목록 조회에 성공했습니다.",
                        result
                    )
                );

        return response;
    }

    /**
     * 운영 중인 숙소의 기본 정보를 조회합니다.
     *
     * 객실 목록은 숙소별 객실 목록 조회 API에서
     * 별도로 제공합니다.
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
