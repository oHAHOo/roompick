package com.roompick.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.room.entity.Room;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String MEMBER_EMAIL = "member@roompick.com";
    private static final String MEMBER_NAME = "홍길동";
    private static final String ACCOMMODATION_NAME = "룸픽 호텔";
    private static final String ROOM_NAME = "디럭스 더블룸";
    private static final long PAYMENT_AMOUNT = 100_000L;

    @Mock
    private PaymentService paymentService;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private Payment payment;

    @Mock
    private Reservation reservation;

    @Mock
    private Member member;

    @Mock
    private Room room;

    @Mock
    private Accommodation accommodation;

    private PaymentNotificationService paymentNotificationService;

    @Test
    @DisplayName("결제 ID로 조회한 예약자에게 결제 완료 이메일을 발송한다")
    void sendCompletionEmailSendsMailToReservationMember() {
        // given
        paymentNotificationService =
            new PaymentNotificationService(
                paymentService,
                mailSender
            );

        given(paymentService.findById(PAYMENT_ID))
            .willReturn(payment);

        given(payment.getReservation())
            .willReturn(reservation);

        given(reservation.getMember())
            .willReturn(member);

        given(reservation.getRoom())
            .willReturn(room);

        given(room.getAccommodation())
            .willReturn(accommodation);

        given(member.getEmail())
            .willReturn(MEMBER_EMAIL);

        given(member.getName())
            .willReturn(MEMBER_NAME);

        given(accommodation.getName())
            .willReturn(ACCOMMODATION_NAME);

        given(room.getName())
            .willReturn(ROOM_NAME);

        given(reservation.getCheckInDate())
            .willReturn(LocalDate.of(2026, 8, 20));

        given(reservation.getCheckOutDate())
            .willReturn(LocalDate.of(2026, 8, 22));

        given(payment.getAmount())
            .willReturn(PAYMENT_AMOUNT);

        // when
        paymentNotificationService.sendCompletionEmail(PAYMENT_ID);

        // then
        ArgumentCaptor<SimpleMailMessage> messageCaptor =
            ArgumentCaptor.forClass(SimpleMailMessage.class);

        then(mailSender)
            .should()
            .send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();

        assertThat(sentMessage.getTo())
            .containsExactly(MEMBER_EMAIL);

        assertThat(sentMessage.getText())
            .contains(MEMBER_NAME)
            .contains(ACCOMMODATION_NAME)
            .contains(ROOM_NAME)
            .contains(String.valueOf(PAYMENT_AMOUNT));
    }

    @Test
    @DisplayName("결제를 찾을 수 없으면 메일을 발송하지 않고 예외를 전파한다")
    void sendCompletionEmailPropagatesExceptionWhenPaymentNotFound() {
        // given
        paymentNotificationService =
            new PaymentNotificationService(
                paymentService,
                mailSender
            );

        given(paymentService.findById(PAYMENT_ID))
            .willThrow(
                new com.roompick.global.common.BusinessException(
                    com.roompick.global.common.ErrorCode.PAYMENT_NOT_FOUND
                )
            );

        // when & then
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() ->
                paymentNotificationService
                    .sendCompletionEmail(PAYMENT_ID)
            )
            .isInstanceOf(
                com.roompick.global.common.BusinessException.class
            );

        then(mailSender)
            .should(org.mockito.Mockito.never())
            .send(any(SimpleMailMessage.class));
    }
}
