package com.roompick.domain.payment.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.room.entity.Room;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentNotificationService {

    private final PaymentService paymentService;
    private final JavaMailSender mailSender;

    @Transactional(readOnly = true)
    public void sendCompletionEmail(Long paymentId) {
        Payment payment = paymentService.findById(paymentId);
        Reservation reservation = payment.getReservation();
        Member member = reservation.getMember();
        Room room = reservation.getRoom();
        Accommodation accommodation = room.getAccommodation();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(member.getEmail());
        message.setSubject("[RoomPick] 결제가 완료되었습니다");
        message.setText(
            "%s님, %s %s 예약 결제가 완료되었습니다. (체크인: %s, 체크아웃: %s, 결제금액: %d원)"
                .formatted(
                    member.getName(),
                    accommodation.getName(),
                    room.getName(),
                    reservation.getCheckInDate(),
                    reservation.getCheckOutDate(),
                    payment.getAmount()
                )
        );

        mailSender.send(message);
    }
}
