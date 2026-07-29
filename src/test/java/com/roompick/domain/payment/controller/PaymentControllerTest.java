package com.roompick.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.payment.dto.response.PaymentApproveResponseDto;
import com.roompick.domain.payment.dto.response.PaymentFailResponseDto;
import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.facade.PaymentFacade;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.security.AuthMember;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName("인증된 회원은 자신의 예약 결제를 준비할 수 있다")
    void authenticatedMemberCanPreparePayment()
        throws Exception {

        // given
        Long reservationId = 1L;
        Long memberId = 10L;
        String portOnePaymentId = "roompick-payment-test-001";

        PaymentPrepareResponseDto result =
            new PaymentPrepareResponseDto(
                100L,
                portOnePaymentId,
                reservationId,
                200000L,
                PaymentStatus.READY
            );

        given(
            paymentFacade.preparePayment(
                reservationId,
                memberId
            )
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("결제가 준비되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.paymentId")
                    .value(100L)
            )
            .andExpect(
                jsonPath("$.data.portOnePaymentId")
                    .value(portOnePaymentId)
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(reservationId)
            )
            .andExpect(
                jsonPath("$.data.amount")
                    .value(200000L)
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("READY")
            );

        then(paymentFacade)
            .should()
            .preparePayment(
                reservationId,
                memberId
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 결제를 준비할 수 없다")
    void unauthenticatedMemberCannotPreparePayment()
        throws Exception {

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    1L
                )
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("다른 회원의 예약이면 결제 준비 요청이 거절된다")
    void rejectOtherMemberReservation()
        throws Exception {

        // given
        Long reservationId = 1L;
        Long memberId = 10L;

        given(
            paymentFacade.preparePayment(
                reservationId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_ACCESS_DENIED
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_ACCESS_DENIED
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.RESERVATION_ACCESS_DENIED
                            .getMessage()
                    )
            );
    }

    @Test
    @DisplayName("존재하지 않는 예약이면 결제 준비 요청이 실패한다")
    void rejectMissingReservation()
        throws Exception {

        // given
        Long reservationId = 999L;
        Long memberId = 10L;

        given(
            paymentFacade.preparePayment(
                reservationId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_NOT_FOUND
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_NOT_FOUND
                            .getCode()
                    )
            );
    }

    @Test
    @DisplayName("결제할 수 없는 예약 상태이면 결제 준비 요청이 실패한다")
    void rejectNonPayableReservation()
        throws Exception {

        // given
        Long reservationId = 1L;
        Long memberId = 10L;

        given(
            paymentFacade.preparePayment(
                reservationId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_NOT_PAYABLE
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_NOT_PAYABLE
                            .getCode()
                    )
            );
    }

    @Test
    @DisplayName("결제 대기 시간이 만료된 예약이면 결제 준비 요청이 실패한다")
    void rejectExpiredReservation()
        throws Exception {

        // given
        Long reservationId = 1L;
        Long memberId = 10L;

        given(
            paymentFacade.preparePayment(
                reservationId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_PAYMENT_EXPIRED
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_PAYMENT_EXPIRED
                            .getCode()
                    )
            );
    }

    @Test
    @DisplayName("동일한 예약에 결제가 이미 존재하면 결제 준비 요청이 실패한다")
    void rejectDuplicatedPayment()
        throws Exception {

        // given
        Long reservationId = 1L;
        Long memberId = 10L;

        given(
            paymentFacade.preparePayment(
                reservationId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.PAYMENT_ALREADY_EXISTS
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    reservationId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.PAYMENT_ALREADY_EXISTS
                            .getCode()
                    )
            );
    }

    @Test
    @DisplayName("인증된 회원은 READY 상태의 결제를 승인할 수 있다")
    void authenticatedMemberCanApprovePayment()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long reservationId = 1L;
        Long memberId = 10L;

        LocalDateTime approvedAt =
            LocalDateTime.of(
                2026,
                7,
                24,
                20,
                0
            );

        PaymentApproveResponseDto result =
            new PaymentApproveResponseDto(
                paymentId,
                reservationId,
                200_000L,
                PaymentStatus.PAID,
                ReservationStatus.CONFIRMED,
                approvedAt
            );

        given(
            paymentFacade.approvePayment(
                paymentId,
                memberId,
                200_000L
            )
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/approve",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "amount": 200000
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("결제가 완료되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.paymentId")
                    .value(paymentId)
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(reservationId)
            )
            .andExpect(
                jsonPath("$.data.amount")
                    .value(200_000L)
            )
            .andExpect(
                jsonPath("$.data.paymentStatus")
                    .value("PAID")
            )
            .andExpect(
                jsonPath("$.data.reservationStatus")
                    .value("CONFIRMED")
            )
            .andExpect(
                jsonPath("$.data.approvedAt")
                    .exists()
            );

        then(paymentFacade)
            .should()
            .approvePayment(
                paymentId,
                memberId,
                200_000L
            );
    }

    @Test
    @DisplayName("요청 금액이 결제 금액과 다르면 결제 승인이 거절된다")
    void rejectApprovalWhenAmountDoesNotMatch()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentFacade.approvePayment(
                paymentId,
                memberId,
                190_000L
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/approve",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "amount": 190000
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.PAYMENT_AMOUNT_MISMATCH
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.PAYMENT_AMOUNT_MISMATCH
                            .getMessage()
                    )
            );

        then(paymentFacade)
            .should()
            .approvePayment(
                paymentId,
                memberId,
                190_000L
            );
    }

    @Test
    @DisplayName("이미 승인된 결제는 다시 승인할 수 없다")
    void rejectDuplicatedPaymentApproval()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentFacade.approvePayment(
                paymentId,
                memberId,
                200_000L
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/approve",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "amount": 200000
                        }
                        """
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.INVALID_PAYMENT_STATUS
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_PAYMENT_STATUS
                            .getMessage()
                    )
            );

        then(paymentFacade)
            .should()
            .approvePayment(
                paymentId,
                memberId,
                200_000L
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 결제를 승인할 수 없다")
    void unauthenticatedMemberCannotApprovePayment()
        throws Exception {

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/approve",
                    100L
                )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "amount": 200000
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("결제 금액이 음수이면 결제를 승인할 수 없다")
    void rejectApprovalWithNegativeAmount()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/approve",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        """
                        {
                          "amount": -1
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentFacade);
    }

    @Test
    @DisplayName("인증된 회원은 READY 상태의 결제를 실패 처리할 수 있다")
    void authenticatedMemberCanFailPayment()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long reservationId = 1L;
        Long memberId = 10L;

        LocalDateTime failedAt =
            LocalDateTime.of(
                2026,
                7,
                27,
                16,
                0
            );

        PaymentFailResponseDto result =
            new PaymentFailResponseDto(
                paymentId,
                reservationId,
                200_000L,
                PaymentStatus.FAILED,
                ReservationStatus.CANCELED,
                failedAt,
                failedAt
            );

        given(
            paymentFacade.failPayment(
                paymentId,
                memberId
            )
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/fail",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "결제 실패 처리가 완료되었습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.paymentId")
                    .value(paymentId)
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(reservationId)
            )
            .andExpect(
                jsonPath("$.data.amount")
                    .value(200_000L)
            )
            .andExpect(
                jsonPath("$.data.paymentStatus")
                    .value("FAILED")
            )
            .andExpect(
                jsonPath("$.data.reservationStatus")
                    .value("CANCELED")
            )
            .andExpect(
                jsonPath("$.data.failedAt")
                    .exists()
            )
            .andExpect(
                jsonPath("$.data.canceledAt")
                    .exists()
            );

        then(paymentFacade)
            .should()
            .failPayment(
                paymentId,
                memberId
            );
    }

    @Test
    @DisplayName("이미 처리된 결제는 실패 처리할 수 없다")
    void rejectFailureWhenPaymentStatusIsInvalid()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentFacade.failPayment(
                paymentId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_PAYMENT_STATUS
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/fail",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.INVALID_PAYMENT_STATUS
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_PAYMENT_STATUS
                            .getMessage()
                    )
            );

        then(paymentFacade)
            .should()
            .failPayment(
                paymentId,
                memberId
            );
    }

    @Test
    @DisplayName("다른 회원의 결제 실패 처리 요청은 거절된다")
    void rejectFailureForOtherMembersReservation()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentFacade.failPayment(
                paymentId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_ACCESS_DENIED
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/fail",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_ACCESS_DENIED
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.RESERVATION_ACCESS_DENIED
                            .getMessage()
                    )
            );

        then(paymentFacade)
            .should()
            .failPayment(
                paymentId,
                memberId
            );
    }

    @Test
    @DisplayName("존재하지 않는 결제는 실패 처리할 수 없다")
    void rejectFailureForMissingPayment()
        throws Exception {

        // given
        Long paymentId = 999L;
        Long memberId = 10L;

        given(
            paymentFacade.failPayment(
                paymentId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.PAYMENT_NOT_FOUND
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/fail",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.PAYMENT_NOT_FOUND
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.PAYMENT_NOT_FOUND
                            .getMessage()
                    )
            );

        then(paymentFacade)
            .should()
            .failPayment(
                paymentId,
                memberId
            );
    }

    @Test
    @DisplayName("결제 대기 상태가 아닌 예약은 결제 실패로 취소할 수 없다")
    void rejectFailureForNonPayableReservation()
        throws Exception {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentFacade.failPayment(
                paymentId,
                memberId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_NOT_PAYABLE
            )
        );

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/fail",
                    paymentId
                )
                    .with(
                        authentication(
                            userAuthentication(memberId)
                        )
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_NOT_PAYABLE
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.RESERVATION_NOT_PAYABLE
                            .getMessage()
                    )
            );

        then(paymentFacade)
            .should()
            .failPayment(
                paymentId,
                memberId
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 결제를 실패 처리할 수 없다")
    void unauthenticatedMemberCannotFailPayment()
        throws Exception {

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/payments/{paymentId}/fail",
                    100L
                )
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentFacade);
    }

    private Authentication userAuthentication(
        Long memberId
    ) {
        AuthMember authMember =
            new AuthMember(
                memberId,
                MemberRole.USER
            );

        return new UsernamePasswordAuthenticationToken(
            authMember,
            null,
            List.of(
                new SimpleGrantedAuthority(
                    "ROLE_USER"
                )
            )
        );
    }
}
