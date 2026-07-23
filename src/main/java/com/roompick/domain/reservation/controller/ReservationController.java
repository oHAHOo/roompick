package com.roompick.domain.reservation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.facade.ReservationFacade;
import com.roompick.global.common.ApiResponseDto;
import com.roompick.global.security.AuthMember;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


/**
 * 예약 생성·조회·취소 요청을 처리하는 Controller입니다.
 *
 * 인증된 회원 정보와 요청 DTO를 ReservationFacade에 전달하고
 * HTTP 응답을 생성합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationFacade reservationFacade;

    /**
     * 인증된 회원의 예약을 결제 대기 상태로 생성합니다.
     */
    @PostMapping
    public ResponseEntity<ApiResponseDto<ReservationCreateResponseDto>> createReservation(
        @AuthenticationPrincipal AuthMember authMember,
        @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                authMember.memberId(),
                request
            );

        ResponseEntity<ApiResponseDto<ReservationCreateResponseDto>> result =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "예약이 생성되었습니다. 제한 시간 내에 결제를 완료해 주세요.",
                        response
                    )
                );

        return result;
    }
}
