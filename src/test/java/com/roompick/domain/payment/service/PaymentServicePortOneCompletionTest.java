package com.roompick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PaymentServicePortOneCompletionTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private Payment payment;

    @Mock
    private Reservation reservation;

    @Mock
    private Member member;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName(
        "PortOne 결제 완료 요청 회원이 예약 소유자가 아니면 거절한다"
    )
    void rejectPortOneCompletionWhenOwnerIsDifferent() {

        // given
        Long paymentId = 100L;
        Long requestMemberId = 10L;
        Long ownerMemberId = 20L;

        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    paymentId
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(ownerMemberId);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPortOneCompletion(
                            paymentId,
                            requestMemberId
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_ACCESS_DENIED
            );

        then(payment)
            .should(never())
            .getStatus();
    }

    @Test
    @DisplayName(
        "PortOne 결제 완료 시 Payment가 READY 상태가 아니면 거절한다"
    )
    void rejectPortOneCompletionWhenPaymentIsNotReady() {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    paymentId
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(memberId);

        given(payment.getStatus())
            .willReturn(PaymentStatus.PAID);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPortOneCompletion(
                            paymentId,
                            memberId
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_PAYMENT_STATUS
            );
    }

    @Test
    @DisplayName(
        "예약 소유자가 요청하고 Payment가 READY이면 PortOne 완료 조회에 성공한다"
    )
    void findPaymentForPortOneCompletion() {

        // given
        Long paymentId = 100L;
        Long memberId = 10L;

        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    paymentId
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(memberId);

        given(payment.getStatus())
            .willReturn(PaymentStatus.READY);

        // when
        Payment result =
            paymentService
                .findForPortOneCompletion(
                    paymentId,
                    memberId
                );

        // then
        assertThat(result)
            .isSameAs(payment);

        then(paymentRepository)
            .should()
            .findByIdWithReservationAndMember(
                paymentId
            );
    }

    @Test
    @DisplayName(
        "존재하지 않는 결제이면 PortOne 결제 완료 조회가 실패한다"
    )
    void rejectMissingPayment() {

        // given
        Long paymentId = 999L;
        Long memberId = 10L;

        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    paymentId
                )
        ).willReturn(
            Optional.empty()
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPortOneCompletion(
                            paymentId,
                            memberId
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_NOT_FOUND
            );
    }

    @Test
    @DisplayName(
        "회원 ID가 없으면 PortOne 결제 완료 조회가 거절된다"
    )
    void rejectPortOneCompletionWhenMemberIdIsNull() {

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPortOneCompletion(
                            100L,
                            null
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.UNAUTHORIZED
            );

        then(paymentRepository)
            .shouldHaveNoInteractions();
    }
}
