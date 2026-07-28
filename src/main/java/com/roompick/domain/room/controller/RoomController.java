package com.roompick.domain.room.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.room.dto.RoomAvailabilityRequestDto;
import com.roompick.domain.room.dto.RoomAvailabilityResponseDto;
import com.roompick.domain.room.dto.RoomDetailResponseDto;
import com.roompick.domain.room.facade.RoomFacade;
import com.roompick.global.common.ApiResponseDto;

import lombok.RequiredArgsConstructor;

/**
 * 객실 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomFacade roomFacade;

    /**
     * 객실 상세 화면에 필요한 객실 기본 정보를 조회합니다.
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponseDto<RoomDetailResponseDto>>
    getRoomDetail(
        @PathVariable Long roomId
    ) {
        RoomDetailResponseDto result =
            roomFacade.getRoomDetail(roomId);

        ResponseEntity<ApiResponseDto<RoomDetailResponseDto>> response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "객실 상세 조회에 성공했습니다.",
                        result
                    )
                );

        return response;
    }

    /**
     * 날짜와 인원을 기준으로 객실 예약 가능 여부를 조회합니다.
     */
    @GetMapping("/{roomId}/availability")
    public ResponseEntity<ApiResponseDto<RoomAvailabilityResponseDto>>
    getRoomAvailability(
        @PathVariable Long roomId,
        @ModelAttribute RoomAvailabilityRequestDto request
    ) {
        RoomAvailabilityResponseDto result =
            roomFacade.getRoomAvailability(
                roomId,
                request
            );

        ResponseEntity<ApiResponseDto<RoomAvailabilityResponseDto>> response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "객실 예약 가능 여부 확인에 성공했습니다.",
                        result
                    )
                );

        return response;
    }
}
