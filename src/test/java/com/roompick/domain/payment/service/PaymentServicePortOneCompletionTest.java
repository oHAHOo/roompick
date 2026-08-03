package com.roompick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.springframework.dao.PessimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class PaymentServicePortOneCompletionTest {

    private static final Long PAYMENT_ID = 100L;

    private static final Long MEMBER_ID = 10L;

    private static final Long OTHER_MEMBER_ID = 20L;

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
        "PortOne 외부 호출 전에는 락이 없는 일반 조회 메서드를 사용한다"
    )
    void findForPortOneCompletionUsesNormalRepositoryMethod() {

        // given
        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(MEMBER_ID);


        // when
        Payment result =
            paymentService.findForPortOneCompletion(
                PAYMENT_ID,
                MEMBER_ID
            );

        // then
        assertThat(result)
            .isSameAs(payment);

        then(paymentRepository)
            .should()
            .findByIdWithReservationAndMember(
                PAYMENT_ID
            );

        then(paymentRepository)
            .should(never())
            .findByIdForUpdate(
                PAYMENT_ID
            );
    }

    @Test
    @DisplayName(
        "PortOne 외부 호출 전 조회에서는 승인 완료된 결제도 소유권 확인 후 반환한다"
    )
    void findForPortOneCompletionReturnsPaidPayment() {

        // given
        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(MEMBER_ID);

        given(payment.getStatus())
            .willReturn(PaymentStatus.PAID);

        // when
        Payment result =
            paymentService
                .findForPortOneCompletion(
                    PAYMENT_ID,
                    MEMBER_ID
                );

        // then
        assertThat(result)
            .isSameAs(payment);

        assertThat(result.getStatus())
            .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName(
        "PortOne 외부 호출 전 요청 회원이 결제 소유자가 아니면 상태 검증 전에 거절한다"
    )
    void rejectPortOneCompletionWhenOwnerIsDifferent() {

        // given
        given(
            paymentRepository
                .findByIdWithReservationAndMember(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(OTHER_MEMBER_ID);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPortOneCompletion(
                            PAYMENT_ID,
                            MEMBER_ID
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

        then(paymentRepository)
            .should(never())
            .findByIdForUpdate(
                PAYMENT_ID
            );
    }

    @Test
    @DisplayName(
        "결제 상태 변경 전에는 공통 비관적 락 조회 메서드를 사용한다"
    )
    void findForPaymentTransitionForUpdateUsesLockRepositoryMethod() {

        // given
        given(
            paymentRepository
                .findByIdForUpdate(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(MEMBER_ID);

        // when
        Payment result =
            paymentService
                .findForPaymentTransitionForUpdate(
                    PAYMENT_ID,
                    MEMBER_ID
                );

        // then
        assertThat(result)
            .isSameAs(payment);

        then(paymentRepository)
            .should()
            .findByIdForUpdate(
                PAYMENT_ID
            );

        then(paymentRepository)
            .should(never())
            .findByIdWithReservationAndMember(
                PAYMENT_ID
            );
    }

    @Test
    @DisplayName(
        "공통 비관적 락 조회에서는 Payment 상태를 검증하지 않는다"
    )
    void lockedPaymentLookupDoesNotValidatePaymentStatus() {

        // given
        given(
            paymentRepository
                .findByIdForUpdate(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(MEMBER_ID);

        // when
        Payment result =
            paymentService
                .findForPaymentTransitionForUpdate(
                    PAYMENT_ID,
                    MEMBER_ID
                );

        // then
        assertThat(result)
            .isSameAs(payment);

        /*
         * 공통 락 조회는 READY, PAID, FAILED 등의
         * 상태를 판단하지 않습니다.
         *
         * 실제 상태 검증은 Payment의 approve(),
         * approveWithPortOne(), fail()에서 수행합니다.
         */
        then(payment)
            .should(never())
            .getStatus();
    }

    @Test
    @DisplayName(
        "공통 비관적 락 조회 후 요청 회원이 결제 소유자가 아니면 거절한다"
    )
    void rejectLockedPaymentWhenOwnerIsDifferent() {

        // given
        given(
            paymentRepository
                .findByIdForUpdate(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(member.getId())
            .willReturn(OTHER_MEMBER_ID);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            PAYMENT_ID,
                            MEMBER_ID
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_ACCESS_DENIED
            );

        /*
         * 소유권 검증에 실패한 경우에도
         * Payment 상태는 확인하지 않습니다.
         */
        then(payment)
            .should(never())
            .getStatus();
    }

    @Test
    @DisplayName(
        "공통 비관적 락 조회 대상 결제가 존재하지 않으면 예외가 발생한다"
    )
    void rejectWhenLockedPaymentDoesNotExist() {

        // given
        given(
            paymentRepository
                .findByIdForUpdate(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.empty()
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            PAYMENT_ID,
                            MEMBER_ID
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
        "공통 비관적 락 조회 시 결제 ID가 없으면 Repository를 호출하지 않는다"
    )
    void rejectLockedLookupWhenPaymentIdIsNull() {

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            null,
                            MEMBER_ID
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        verifyNoInteractions(
            paymentRepository
        );
    }

    @Test
    @DisplayName(
        "공통 비관적 락 조회 시 회원 ID가 없으면 Repository를 호출하지 않는다"
    )
    void rejectLockedLookupWhenMemberIdIsNull() {

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            PAYMENT_ID,
                            null
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.UNAUTHORIZED
            );

        verifyNoInteractions(
            paymentRepository
        );
    }

    @Test
    @DisplayName(
        "공통 비관적 락 조회 후 예약 정보가 없으면 예외가 발생한다"
    )
    void rejectLockedPaymentWhenReservationIsMissing() {

        // given
        given(
            paymentRepository
                .findByIdForUpdate(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(null);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            PAYMENT_ID,
                            MEMBER_ID
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_NOT_FOUND
            );

        then(payment)
            .should(never())
            .getStatus();
    }

    @Test
    @DisplayName(
        "공통 비관적 락 조회 후 예약 회원 정보가 없으면 예외가 발생한다"
    )
    void rejectLockedPaymentWhenReservationMemberIsMissing() {

        // given
        given(
            paymentRepository
                .findByIdForUpdate(
                    PAYMENT_ID
                )
        ).willReturn(
            Optional.of(payment)
        );

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(null);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            PAYMENT_ID,
                            MEMBER_ID
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_NOT_FOUND
            );

        then(payment)
            .should(never())
            .getStatus();
    }

    @Test
    @DisplayName(
        "Payment 락 획득에 실패하면 결제 락 타임아웃 예외로 변환한다"
    )
    void convertPessimisticLockFailureToBusinessException() {

        // given
        given(
            paymentRepository.findByIdForUpdate(
                PAYMENT_ID
            )
        ).willThrow(
            new PessimisticLockingFailureException(
                "lock timeout"
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    paymentService
                        .findForPaymentTransitionForUpdate(
                            PAYMENT_ID,
                            MEMBER_ID
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_LOCK_TIMEOUT
            );
    }
}
