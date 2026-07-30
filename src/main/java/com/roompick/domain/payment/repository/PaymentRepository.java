package com.roompick.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.payment.entity.Payment;

public interface PaymentRepository
    extends JpaRepository<Payment, Long> {

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

    /**
     * PortOne 결제 완료 처리에 필요한
     * Payment, Reservation, Member를 한 번에 조회합니다.
     *
     * 외부 API 호출 전 소유권을 확인할 때
     * LazyInitializationException이 발생하지 않도록
     * Reservation과 Member를 fetch join합니다.
     */
    @Query("""
        select p
        from Payment p
        join fetch p.reservation r
        join fetch r.member m
        where p.id = :paymentId
        """)
    Optional<Payment>
    findByIdWithReservationAndMember(
        @Param("paymentId")
        Long paymentId
    );
}
