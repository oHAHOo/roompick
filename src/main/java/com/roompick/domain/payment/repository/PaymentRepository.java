package com.roompick.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.roompick.domain.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 해당 예약에 이미 생성된 결제가 있는지 확인합니다.
     */
    boolean existsByReservationId(
        Long reservationId
    );

    /**
     * 예약 ID를 기준으로 결제를 조회합니다.
     */
    Optional<Payment> findByReservationId(
        Long reservationId
    );
}
