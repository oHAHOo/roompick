package com.roompick.domain.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.payment.entity.Payment;

import jakarta.persistence.LockModeType;

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
     * PortOne 외부 API 호출 전에 결제 소유권과
     * 현재 상태를 검증하기 위한 일반 조회입니다.
     *
     * 외부 API를 호출하는 동안 DB 락을 유지하지 않도록
     * 이 메서드에는 비관적 락을 적용하지 않습니다.
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

    /**
     * 결제 상태를 변경하기 직전에 Payment를
     * 비관적 쓰기 락과 함께 조회합니다.
     *
     * 같은 Payment에 대한 다른 상태 변경 요청은
     * 현재 트랜잭션이 종료될 때까지 대기하게 됩니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    where p.id = :paymentId
    """)
    Optional<Payment> findByIdForUpdate(
        @Param("paymentId") Long paymentId
    );
}
