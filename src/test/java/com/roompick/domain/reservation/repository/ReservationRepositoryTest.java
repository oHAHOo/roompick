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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import jakarta.persistence.PersistenceUnitUtil;

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

    @Test
    @DisplayName("회원의 예약 목록을 페이지 단위로 객실과 숙소 정보까지 함께 조회한다")
    void findMemberReservationsWithRoomAndAccommodation() {
        // given
        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 14, 10);

        savePendingReservation(expiresAt);

        Reservation savedReservation =
            reservationRepository.findAll()
                .get(0);

        Long memberId =
            savedReservation.getMember().getId();

        Long reservationId =
            savedReservation.getId();

        Long roomId =
            savedReservation.getRoom().getId();

        Sort sort = Sort
            .by(
                Sort.Direction.DESC,
                "createdAt"
            )
            .and(
                Sort.by(
                    Sort.Direction.DESC,
                    "id"
                )
            );

        Pageable pageable = PageRequest.of(
            0,
            10,
            sort
        );

        flushAndClear();

        // when
        Page<Reservation> reservationPage =
            reservationRepository
                .findAllByMemberIdWithRoomAndAccommodation(
                    memberId,
                    pageable
                );

        // then
        assertThat(reservationPage.getContent())
            .hasSize(1);

        assertThat(reservationPage.getNumber())
            .isZero();

        assertThat(reservationPage.getSize())
            .isEqualTo(10);

        assertThat(reservationPage.getTotalElements())
            .isEqualTo(1);

        assertThat(reservationPage.getTotalPages())
            .isEqualTo(1);

        assertThat(reservationPage.isLast())
            .isTrue();

        Reservation foundReservation =
            reservationPage.getContent()
                .get(0);

        assertThat(foundReservation.getId())
            .isEqualTo(reservationId);

        assertThat(foundReservation.getMember().getId())
            .isEqualTo(memberId);

        assertThat(foundReservation.getRoom().getId())
            .isEqualTo(roomId);

        assertThat(
            foundReservation
                .getRoom()
                .getAccommodation()
                .getName()
        ).isEqualTo("룸픽 호텔");

        /*
         * fetch join 쿼리이므로 영속성 컨텍스트를 비운 뒤 조회해도
         * 객실과 숙소가 이미 초기화된 상태여야 합니다.
         */
        PersistenceUnitUtil persistenceUnitUtil =
            entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertThat(
            persistenceUnitUtil.isLoaded(
                foundReservation,
                "room"
            )
        ).isTrue();

        assertThat(
            persistenceUnitUtil.isLoaded(
                foundReservation.getRoom(),
                "accommodation"
            )
        ).isTrue();
    }

    @Test
    @DisplayName("예약 상세를 객실과 숙소 정보까지 함께 조회한다")
    void findReservationDetailWithRoomAndAccommodation() {
        // given
        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 14, 10);

        savePendingReservation(expiresAt);

        Reservation savedReservation =
            reservationRepository.findAll()
                .get(0);

        Long reservationId =
            savedReservation.getId();

        Long memberId =
            savedReservation.getMember().getId();

        Long roomId =
            savedReservation.getRoom().getId();

        flushAndClear();

        // when
        Reservation foundReservation =
            reservationRepository
                .findByIdWithRoomAndAccommodation(
                    reservationId
                )
                .orElseThrow();

        // then
        assertThat(foundReservation.getId())
            .isEqualTo(reservationId);

        assertThat(foundReservation.getMember().getId())
            .isEqualTo(memberId);

        assertThat(foundReservation.getRoom().getId())
            .isEqualTo(roomId);

        assertThat(
            foundReservation
                .getRoom()
                .getAccommodation()
                .getName()
        ).isEqualTo("룸픽 호텔");

        /*
         * 상세 조회 쿼리에서 객실과 숙소를 fetch join하므로
         * 영속성 컨텍스트를 비운 뒤에도 두 연관관계가 초기화되어 있어야 합니다.
         */
        PersistenceUnitUtil persistenceUnitUtil =
            entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertThat(
            persistenceUnitUtil.isLoaded(
                foundReservation,
                "room"
            )
        ).isTrue();

        assertThat(
            persistenceUnitUtil.isLoaded(
                foundReservation.getRoom(),
                "accommodation"
            )
        ).isTrue();
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
