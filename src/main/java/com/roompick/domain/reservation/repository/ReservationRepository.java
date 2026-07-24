package com.roompick.domain.reservation.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    /**
     * 회원의 예약 목록을 최신 생성순으로 조회합니다.
     *
     * 응답 변환에 필요한 객실과 숙소를 fetch join하여
     * 예약 건수만큼 추가 조회가 발생하는 N+1 문제를 방지합니다.
     */
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        JOIN FETCH reservation.room room
        JOIN FETCH room.accommodation accommodation
        WHERE reservation.member.id = :memberId
        ORDER BY reservation.createdAt DESC,
                 reservation.id DESC
        """)
    List<Reservation> findAllByMemberIdWithRoomAndAccommodation(
        @Param("memberId") Long memberId
    );

    /**
     * 예약 ID로 예약과 객실·숙소 정보를 함께 조회합니다.
     *
     * 회원 ID 조건은 조회 쿼리에 넣지 않고 Service에서 소유자를 검증하여
     * 예약 없음과 다른 회원의 예약 접근을 구분합니다.
     */
    @Query("""
        SELECT reservation
        FROM Reservation reservation
        JOIN FETCH reservation.room room
        JOIN FETCH room.accommodation accommodation
        WHERE reservation.id = :reservationId
        """)
    Optional<Reservation> findByIdWithRoomAndAccommodation(
        @Param("reservationId") Long reservationId
    );
}
