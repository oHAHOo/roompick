package com.roompick.domain.reservation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.dto.ReservationCancelResponseDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;

/**
 * 실제 Spring Context와 H2 DB를 사용하여
 * 예약 취소 후 객실 점유가 해제되는지 검증합니다.
 *
 * 테스트 클래스에는 @Transactional을 사용하지 않습니다.
 * ReservationService의 트랜잭션이 종료된 뒤
 * 실제 DB 상태를 다시 조회하기 위함입니다.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url="
            + "jdbc:h2:mem:reservation-cancel-integration-test;"
            + "MODE=MySQL;"
            + "DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false"
    }
)
@ActiveProfiles("test")
class ReservationCancelIntegrationTest {

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    /**
     * 외래키 제약조건을 고려하여 자식 테이블부터 삭제합니다.
     */
    @AfterEach
    void tearDown() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "결제 대기 예약 취소 후 해당 기간의 객실 점유가 해제된다"
    )
    void cancelReservationReleasesRoomOccupancy() {
        // given: 회원, 숙소, 객실을 실제 DB에 저장합니다.
        Member member =
            memberRepository.saveAndFlush(
                Member.create(
                    "reservation-cancel@roompick.com",
                    "encoded-password",
                    "예약 취소 테스트 회원"
                )
            );

        Accommodation accommodation =
            accommodationRepository.saveAndFlush(
                Accommodation.create(
                    "룸픽 테스트 호텔",
                    "서울특별시 테스트구 테스트로 1",
                    "예약 취소 통합 테스트용 숙소",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Room room =
            roomRepository.saveAndFlush(
                Room.create(
                    accommodation,
                    "101",
                    "테스트 객실",
                    "예약 취소 통합 테스트용 객실",
                    100_000L,
                    2,
                    2
                )
            );

        LocalDate checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(1);

        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        Reservation reservation =
            reservationRepository.saveAndFlush(
                Reservation.create(
                    member,
                    room,
                    checkInDate,
                    checkOutDate,
                    2,
                    now.plusMinutes(10)
                )
            );

        /*
         * 아직 만료되지 않은 PENDING_PAYMENT 예약이므로
         * 동일 기간의 객실은 점유된 상태여야 합니다.
         */
        boolean occupiedBeforeCancellation =
            reservationRepository
                .existsActiveOverlappingReservation(
                    room.getId(),
                    checkInDate,
                    checkOutDate,
                    now
                );

        assertThat(occupiedBeforeCancellation)
            .isTrue();

        // when: 실제 Facade 흐름으로 예약을 취소합니다.
        ReservationCancelResponseDto response =
            reservationFacade.cancelReservation(
                member.getId(),
                reservation.getId()
            );

        // then: 새로운 Repository 조회로 실제 DB 상태를 확인합니다.
        Reservation canceledReservation =
            reservationRepository
                .findById(reservation.getId())
                .orElseThrow();

        assertThat(canceledReservation.getStatus())
            .isEqualTo(ReservationStatus.CANCELED);

        assertThat(canceledReservation.getCanceledAt())
            .isNotNull();

        assertThat(response.reservationId())
            .isEqualTo(reservation.getId());

        assertThat(response.status())
            .isEqualTo(ReservationStatus.CANCELED);

        /*
         * DB 저장 과정에서 시간의 소수점 이하 정밀도가 달라질 수 있으므로
         * 동일한 취소 시각인지 1밀리초 범위 안에서 비교합니다.
         */
        assertThat(response.canceledAt())
            .isCloseTo(
                canceledReservation.getCanceledAt(),
                within(1, ChronoUnit.MILLIS)
            );

        /*
         * CANCELED 예약은 활성 예약 조회 조건에서 제외되므로
         * 동일 기간의 객실을 다시 예약할 수 있어야 합니다.
         */
        boolean occupiedAfterCancellation =
            reservationRepository
                .existsActiveOverlappingReservation(
                    room.getId(),
                    checkInDate,
                    checkOutDate,
                    LocalDateTime.now(TEST_ZONE_ID)
                );

        assertThat(occupiedAfterCancellation)
            .isFalse();
    }
}
