package com.roompick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private Reservation reservation;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("예약 금액을 기준으로 결제를 준비한다")
    void 예약_금액을_기준으로_결제를_준비한다() {
        // given
        given(reservation.getId())
            .willReturn(1L);

        given(reservation.getTotalAmount())
            .willReturn(200000L);

        given(
            paymentRepository.existsByReservationId(1L)
        ).willReturn(false);

        given(
            paymentRepository.save(any(Payment.class))
        ).willAnswer(invocation ->
            invocation.getArgument(0)
        );

        // when
        Payment payment =
            paymentService.preparePayment(
                reservation
            );

        // then
        assertThat(payment.getReservation())
            .isSameAs(reservation);

        assertThat(payment.getAmount())
            .isEqualTo(200000L);

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        then(paymentRepository)
            .should()
            .existsByReservationId(1L);

        then(paymentRepository)
            .should()
            .save(any(Payment.class));
    }

    @Test
    @DisplayName("0원 예약도 결제를 준비할 수 있다")
    void 가격이_0원_예약도_결제를_준비할_수_있다() {
        // given
        given(reservation.getId())
            .willReturn(1L);

        given(reservation.getTotalAmount())
            .willReturn(0L);

        given(
            paymentRepository.existsByReservationId(1L)
        ).willReturn(false);

        given(
            paymentRepository.save(any(Payment.class))
        ).willAnswer(invocation ->
            invocation.getArgument(0)
        );

        // when
        Payment payment =
            paymentService.preparePayment(
                reservation
            );

        // then
        assertThat(payment.getAmount())
            .isZero();

        assertThat(payment.getStatus())
            .isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("동일한 예약에 결제가 이미 존재하면 결제 준비에 실패한다")
    void 동일한_예약에_결제가_이미_존재하면_결제_준비에_실패한다() {
        // given
        given(reservation.getId())
            .willReturn(1L);

        given(
            paymentRepository.existsByReservationId(1L)
        ).willReturn(true);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () ->
                    paymentService.preparePayment(
                        reservation
                    )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_ALREADY_EXISTS
            );

        then(paymentRepository)
            .should(never())
            .save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 ID에 해당하는 결제가 없으면 조회에 실패한다")
    void 결제_ID에_해당하는_결제가_없으면_조회에_실패한다() {
        // given
        given(paymentRepository.findById(1L))
            .willReturn(Optional.empty());

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> paymentService.findById(1L)
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_NOT_FOUND
            );
    }

    @Test
    @DisplayName("예약 ID에 해당하는 결제가 없으면 조회에 실패한다")
    void 예약_ID에_해당하는_결제가_없으면_조회에_실패한다() {
        // given
        given(
            paymentRepository.findByReservationId(1L)
        ).willReturn(Optional.empty());

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () ->
                    paymentService
                        .findByReservationId(1L)
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_NOT_FOUND
            );
    }
}
