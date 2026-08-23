package com.roompick.domain.room.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.room.dto.RoomAvailabilityRequestDto;
import com.roompick.domain.room.dto.RoomAvailabilityResponseDto;
import com.roompick.domain.room.dto.RoomDetailResponseDto;
import com.roompick.domain.room.facade.RoomFacade;
import com.roompick.global.common.ApiResponseDto;
import com.roompick.global.security.AuthMember;

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
     *
     * 인증 없이 호출할 수 있으나, ADMIN으로 로그인한 요청은
     * INACTIVE 객실도 조회할 수 있습니다.
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponseDto<RoomDetailResponseDto>>
    getRoomDetail(
        @PathVariable Long roomId,
        @AuthenticationPrincipal AuthMember authMember
    ) {
        boolean admin =
            authMember != null
                && authMember.role() == MemberRole.ADMIN;

        RoomDetailResponseDto result =
            roomFacade.getRoomDetail(roomId, admin);

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
