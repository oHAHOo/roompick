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
import com.roompick.domain.accommodation.facade.AccommodationFacade;
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
