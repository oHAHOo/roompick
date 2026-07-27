package com.roompick.domain.payment.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.facade.PaymentFacade;
import com.roompick.global.common.ApiResponseDto;
import com.roompick.global.security.AuthMember;

import lombok.RequiredArgsConstructor;

/**
 * 결제 관련 HTTP 요청을 처리합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations/{reservationId}/payments")
public class PaymentController {

    private final PaymentFacade paymentFacade;

    /**
     * 로그인 회원의 예약에 대한 결제를 준비합니다.
     */
    @PostMapping
    public ResponseEntity<ApiResponseDto<PaymentPrepareResponseDto>>
    preparePayment(
        @PathVariable Long reservationId,
        @AuthenticationPrincipal AuthMember authMember
    ) {

        PaymentPrepareResponseDto result =
            paymentFacade.preparePayment(
                reservationId,
                authMember.memberId()
            );

        ResponseEntity<ApiResponseDto<PaymentPrepareResponseDto>> response =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "결제가 준비되었습니다.",
                        result
                    )
                );

        return response;
    }
}
