package com.roompick.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.config.JpaConfig;

import jakarta.persistence.EntityManager;

/**
 * ReservationRepository의 활성 예약 날짜 겹침 쿼리를 검증합니다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(JpaConfig.class)
class ReservationRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("결제 대기 중인 예약과 날짜가 겹치면 활성 예약이 존재한다")
    void existsActiveOverlappingPendingReservation() {
        // given
        LocalDateTime now =
            LocalDateTime.of(2026, 8, 1, 14, 0);

        Long roomId = savePendingReservation(
            now.plusMinutes(10)
        );

        flushAndClear();

        // when
        boolean exists =
            reservationRepository.existsActiveOverlappingReservation(
                roomId,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13),
                now
            );

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("결제 대기 시간이 만료된 예약은 신규 예약을 막지 않는다")
    void expiredPendingReservationDoesNotBlock() {
        // given
        LocalDateTime now =
            LocalDateTime.of(2026, 8, 1, 14, 0);

        Long roomId = savePendingReservation(
            now.minusMinutes(1)
        );

        flushAndClear();

        // when
        boolean exists =
            reservationRepository.existsActiveOverlappingReservation(
                roomId,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13),
                now
            );

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("기존 체크아웃 날짜와 요청 체크인 날짜가 같으면 겹치지 않는다")
    void adjoiningStayPeriodDoesNotOverlap() {
        // given
        LocalDateTime now =
            LocalDateTime.of(2026, 8, 1, 14, 0);

        Long roomId = savePendingReservation(
            now.plusMinutes(10)
        );

        flushAndClear();

        // when
        boolean exists =
            reservationRepository.existsActiveOverlappingReservation(
                roomId,
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 14),
                now
            );

        // then
        assertThat(exists).isFalse();
    }

    /**
     * 2026년 8월 10일부터 12일까지의 결제 대기 예약을 저장합니다.
     */
    private Long savePendingReservation(LocalDateTime expiresAt) {
        Member member = memberRepository.save(
            Member.create(
                "roompick@example.com",
                "encoded-password",
                "룸픽 회원"
            )
        );

        Accommodation accommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "룸픽 호텔",
                    "서울특별시 강남구 테헤란로 123",
                    "RoomPick MVP 예약 테스트 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Room room = roomRepository.save(
            Room.create(
                accommodation,
                "101",
                "디럭스 더블룸",
                "2인이 이용할 수 있는 더블룸입니다.",
                100_000L,
                2,
                2
            )
        );

        reservationRepository.save(
            Reservation.create(
                member,
                room,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2,
                expiresAt
            )
        );

        return room.getId();
    }

    /**
     * 저장 내용을 DB에 반영한 뒤 영속성 컨텍스트를 비웁니다.
     */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
