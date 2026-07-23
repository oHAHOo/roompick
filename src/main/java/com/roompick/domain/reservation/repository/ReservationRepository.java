package com.roompick.domain.reservation.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.reservation.entity.Reservation;

/**
 * 예약 데이터를 저장하고 조회하는 Repository입니다.
 */
public interface ReservationRepository
    extends JpaRepository<Reservation, Long> {

    /**
     * 같은 객실에 요청 기간과 겹치는 활성 예약이 있는지 확인합니다.
     *
     * CONFIRMED 예약과 아직 만료되지 않은 PENDING_PAYMENT 예약만
     * 새로운 예약을 막습니다.
     */
    @Query("""
        SELECT CASE
            WHEN COUNT(reservation) > 0 THEN true
            ELSE false
        END
        FROM Reservation reservation
        WHERE reservation.room.id = :roomId
          AND reservation.checkInDate < :checkOutDate
          AND reservation.checkOutDate > :checkInDate
          AND (
              reservation.status =
                  com.roompick.domain.reservation.entity.ReservationStatus.CONFIRMED
              OR (
                  reservation.status =
                      com.roompick.domain.reservation.entity.ReservationStatus.PENDING_PAYMENT
                  AND reservation.expiresAt > :now
              )
          )
        """)
    boolean existsActiveOverlappingReservation(
        @Param("roomId") Long roomId,
        @Param("checkInDate") LocalDate checkInDate,
        @Param("checkOutDate") LocalDate checkOutDate,
        @Param("now") LocalDateTime now
    );
}
