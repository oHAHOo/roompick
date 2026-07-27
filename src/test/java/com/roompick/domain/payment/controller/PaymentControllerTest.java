package com.roompick.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.facade.PaymentFacade;
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

        PaymentPrepareResponseDto result =
            new PaymentPrepareResponseDto(
                100L,
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
                    .with(authentication(userAuthentication(memberId)))
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
                    .with(authentication(userAuthentication(memberId)))
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
                    .with(authentication(userAuthentication(memberId)))
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
                    .with(authentication(userAuthentication(memberId)))
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
                    .with(authentication(userAuthentication(memberId)))
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
                    .with(authentication(userAuthentication(memberId)))
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
