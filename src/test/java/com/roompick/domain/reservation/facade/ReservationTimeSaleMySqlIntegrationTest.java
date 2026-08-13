package com.roompick.domain.reservation.facade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.repository.ReservationIdempotencyRepository;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.entity.TimeSaleStatus;
import com.roompick.domain.timesale.repository.TimeSaleRepository;
import com.roompick.domain.timesale.service.TimeSaleService;

/**
 * 실제 MySQL 환경에서 타임세일 가격이
 * 예약 가격 스냅샷으로 저장되는지 검증합니다.
 *
 * 예약 생성은 실제 ReservationFacade를 통해 실행하여
 * 객실 비관적 락, 멱등성 처리, 타임세일 가격 계산,
 * 예약 저장 흐름을 함께 확인합니다.
 *
 * 테스트 클래스에는 @Transactional을 적용하지 않습니다.
 * ReservationFacade가 실제 트랜잭션을 시작하고
 * 커밋하도록 구성합니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
    properties = {
        /*
         * Testcontainers 종료 후 Hibernate가
         * drop DDL을 실행하지 않도록 create를 사용합니다.
         */
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",

        /*
         * 테스트 중 스케줄러가 타임세일 상태를
         * 자동으로 변경하지 않도록 비활성화합니다.
         */
        "spring.task.scheduling.enabled=false"
    }
)
@ActiveProfiles("test")
@Import(
    ReservationTimeSaleMySqlIntegrationTest
        .FixedClockConfig.class
)
class ReservationTimeSaleMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;

    private static final String DATABASE_NAME =
        "roompick_reservation_time_sale_test";

    private static final String DATABASE_USERNAME =
        "roompick";

    private static final String DATABASE_PASSWORD =
        "roompick-password";

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    /**
     * UTC 2026-08-12 03:00은
     * 한국 시간 2026-08-12 12:00입니다.
     */
    private static final Instant FIXED_INSTANT =
        Instant.parse(
            "2026-08-12T03:00:00Z"
        );

    private static final LocalDateTime TIME_SALE_NOW =
        LocalDateTime.of(
            2026,
            8,
            12,
            12,
            0
        );

    private static final String
        ACCOMMODATION_SALE_IDEMPOTENCY_KEY =
        "reservation-time-sale-accommodation";

    private static final String
        ROOM_SALE_IDEMPOTENCY_KEY =
        "reservation-time-sale-room";

    private static final String
        NORMAL_PRICE_IDEMPOTENCY_KEY =
        "reservation-time-sale-normal";

    private static final String
        ENDED_SALE_IDEMPOTENCY_KEY =
        "reservation-time-sale-ended";

    private static final String
        SNAPSHOT_IDEMPOTENCY_KEY =
        "reservation-time-sale-snapshot";

    @Container
    static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>(
            DockerImageName.parse(
                "mysql:8.4"
            )
        )
            .withDatabaseName(
                DATABASE_NAME
            )
            .withUsername(
                DATABASE_USERNAME
            )
            .withPassword(
                DATABASE_PASSWORD
            )
            .withStartupTimeout(
                Duration.ofMinutes(2)
            );

    /**
     * application-test.yml의 H2 설정 대신
     * Testcontainers MySQL 접속 정보를 등록합니다.
     */
    @DynamicPropertySource
    static void registerMySqlProperties(
        DynamicPropertyRegistry registry
    ) {
        registry.add(
            "spring.datasource.url",
            () ->
                "jdbc:mysql://"
                    + MYSQL_CONTAINER.getHost()
                    + ":"
                    + MYSQL_CONTAINER.getMappedPort(
                    MYSQL_PORT
                )
                    + "/"
                    + DATABASE_NAME
                    + "?useSSL=false"
                    + "&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=UTF-8"
                    + "&serverTimezone=Asia/Seoul"
        );

        registry.add(
            "spring.datasource.username",
            () -> DATABASE_USERNAME
        );

        registry.add(
            "spring.datasource.password",
            () -> DATABASE_PASSWORD
        );

        registry.add(
            "spring.datasource.driver-class-name",
            () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Autowired
    private ReservationFacade reservationFacade;

    @Autowired
    private TimeSaleService timeSaleService;

    @Autowired
    private ReservationRepository
        reservationRepository;

    @Autowired
    private ReservationIdempotencyRepository
        reservationIdempotencyRepository;

    @Autowired
    private TimeSaleRepository timeSaleRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private AccommodationRepository
        accommodationRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member member;

    private Accommodation accommodation;

    private Room room;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    @BeforeEach
    void setUp() {
        createTestData();

        /*
         * ReservationService는 현재 시스템 날짜를 사용하므로
         * 테스트 실행일을 기준으로 미래 숙박 기간을 사용합니다.
         */
        checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(5);

        checkOutDate =
            checkInDate.plusDays(2);
    }

    @AfterEach
    void tearDown() {
        /*
         * FK 참조 관계의 자식 테이블부터 정리합니다.
         */
        reservationIdempotencyRepository
            .deleteAllInBatch();

        reservationRepository.deleteAllInBatch();
        timeSaleRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "숙소 전체 타임세일 가격이 "
            + "예약 가격으로 저장된다"
    )
    void accommodationSalePriceIsStoredInReservation() {
        // given
        timeSaleService.create(
            accommodation,
            null,
            20,
            TIME_SALE_NOW.minusHours(1),
            TIME_SALE_NOW.plusHours(2)
        );

        ReservationCreateRequestDto request =
            createRequest();

        // when
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                member.getId(),
                ACCOMMODATION_SALE_IDEMPOTENCY_KEY,
                request
            );

        Reservation savedReservation =
            reservationRepository
                .findById(
                    response.reservationId()
                )
                .orElseThrow();

        // then
        assertThat(response.reservationId())
            .isNotNull();

        assertThat(response.memberId())
            .isEqualTo(member.getId());

        assertThat(response.pricePerNight())
            .isEqualTo(80_000L);

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.totalAmount())
            .isEqualTo(160_000L);

        assertThat(response.status())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(savedReservation.getPricePerNight())
            .isEqualTo(80_000L);

        assertThat(savedReservation.getNightCount())
            .isEqualTo(2);

        assertThat(savedReservation.getTotalAmount())
            .isEqualTo(160_000L);

        assertThat(reservationRepository.count())
            .isEqualTo(1L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "객실 전용 타임세일은 숙소 전체 타임세일보다 "
            + "우선하여 예약 가격에 적용된다"
    )
    void roomSaleTakesPriorityWhenCreatingReservation() {
        // given
        timeSaleService.create(
            accommodation,
            null,
            20,
            TIME_SALE_NOW.minusHours(1),
            TIME_SALE_NOW.plusHours(2)
        );

        timeSaleService.create(
            accommodation,
            room,
            30,
            TIME_SALE_NOW.minusMinutes(30),
            TIME_SALE_NOW.plusHours(1)
        );

        ReservationCreateRequestDto request =
            createRequest();

        // when
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                member.getId(),
                ROOM_SALE_IDEMPOTENCY_KEY,
                request
            );

        Reservation savedReservation =
            reservationRepository
                .findById(
                    response.reservationId()
                )
                .orElseThrow();

        // then
        assertThat(response.pricePerNight())
            .isEqualTo(70_000L);

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.totalAmount())
            .isEqualTo(140_000L);

        assertThat(savedReservation.getPricePerNight())
            .isEqualTo(70_000L);

        assertThat(savedReservation.getTotalAmount())
            .isEqualTo(140_000L);

        assertThat(timeSaleRepository.count())
            .isEqualTo(2L);
    }

    @Test
    @DisplayName(
        "적용 가능한 타임세일이 없으면 "
            + "객실 정상 가격으로 예약한다"
    )
    void normalPriceIsStoredWhenNoSaleApplies() {
        // given
        /*
         * 현재 시각보다 나중에 시작하므로
         * 예약 생성 시 적용되지 않습니다.
         */
        timeSaleService.create(
            accommodation,
            null,
            20,
            TIME_SALE_NOW.plusHours(1),
            TIME_SALE_NOW.plusHours(3)
        );

        ReservationCreateRequestDto request =
            createRequest();

        // when
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                member.getId(),
                NORMAL_PRICE_IDEMPOTENCY_KEY,
                request
            );

        Reservation savedReservation =
            reservationRepository
                .findById(
                    response.reservationId()
                )
                .orElseThrow();

        // then
        assertThat(response.pricePerNight())
            .isEqualTo(100_000L);

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.totalAmount())
            .isEqualTo(200_000L);

        assertThat(savedReservation.getPricePerNight())
            .isEqualTo(100_000L);

        assertThat(savedReservation.getTotalAmount())
            .isEqualTo(200_000L);
    }

    @Test
    @DisplayName(
        "종료 상태의 타임세일은 "
            + "예약 가격에 적용되지 않는다"
    )
    void endedSaleDoesNotApplyToReservation() {
        // given
        TimeSale timeSale =
            timeSaleService.create(
                accommodation,
                null,
                20,
                TIME_SALE_NOW.minusHours(1),
                TIME_SALE_NOW.plusHours(2)
            );

        /*
         * 타임세일 종료 시각 이후의 시각을 전달하여
         * ENDED 상태로 변경한 뒤 MySQL에 반영합니다.
         */
        timeSale.end(
            TIME_SALE_NOW.plusHours(3)
        );

        TimeSale endedTimeSale =
            timeSaleRepository.saveAndFlush(
                timeSale
            );

        assertThat(endedTimeSale.getStatus())
            .isEqualTo(TimeSaleStatus.ENDED);

        ReservationCreateRequestDto request =
            createRequest();

        // when
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                member.getId(),
                ENDED_SALE_IDEMPOTENCY_KEY,
                request
            );

        // then
        assertThat(response.pricePerNight())
            .isEqualTo(100_000L);

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.totalAmount())
            .isEqualTo(200_000L);
    }

    @Test
    @DisplayName(
        "예약 후 타임세일이 종료돼도 "
            + "예약 당시 할인 가격은 유지된다"
    )
    void reservationKeepsPriceSnapshotAfterSaleEnds() {
        // given
        TimeSale timeSale =
            timeSaleService.create(
                accommodation,
                null,
                20,
                TIME_SALE_NOW.minusHours(1),
                TIME_SALE_NOW.plusHours(2)
            );

        ReservationCreateRequestDto request =
            createRequest();

        ReservationCreateResponseDto createdResponse =
            reservationFacade.createReservation(
                member.getId(),
                SNAPSHOT_IDEMPOTENCY_KEY,
                request
            );

        assertThat(createdResponse.pricePerNight())
            .isEqualTo(80_000L);

        assertThat(createdResponse.totalAmount())
            .isEqualTo(160_000L);

        /*
         * 예약 생성 이후 타임세일을 종료합니다.
         */
        timeSale.end(
            TIME_SALE_NOW.plusHours(3)
        );

        timeSaleRepository.saveAndFlush(
            timeSale
        );

        // when
        Reservation savedReservation =
            reservationRepository
                .findById(
                    createdResponse.reservationId()
                )
                .orElseThrow();

        /*
         * 같은 멱등성 키와 같은 요청을 다시 전달합니다.
         *
         * 타임세일 가격을 다시 계산하지 않고
         * 최초 예약 결과를 반환해야 합니다.
         */
        ReservationCreateResponseDto replayedResponse =
            reservationFacade.createReservation(
                member.getId(),
                SNAPSHOT_IDEMPOTENCY_KEY,
                request
            );

        // then
        assertThat(savedReservation.getPricePerNight())
            .isEqualTo(80_000L);

        assertThat(savedReservation.getNightCount())
            .isEqualTo(2);

        assertThat(savedReservation.getTotalAmount())
            .isEqualTo(160_000L);

        assertThat(replayedResponse.reservationId())
            .isEqualTo(
                createdResponse.reservationId()
            );

        assertThat(replayedResponse.pricePerNight())
            .isEqualTo(80_000L);

        assertThat(replayedResponse.totalAmount())
            .isEqualTo(160_000L);

        assertThat(reservationRepository.count())
            .isEqualTo(1L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(1L);
    }

    /**
     * 공통 예약 생성 요청을 만듭니다.
     */
    private ReservationCreateRequestDto createRequest() {
        return new ReservationCreateRequestDto(
            room.getId(),
            checkInDate,
            checkOutDate,
            2
        );
    }

    /**
     * 테스트에 필요한 회원, 운영 중인 숙소와
     * 객실을 MySQL에 저장합니다.
     */
    private void createTestData() {
        member =
            memberRepository.saveAndFlush(
                Member.create(
                    "reservation-time-sale@roompick.com",
                    "encoded-password",
                    "타임세일 예약 테스트 회원"
                )
            );

        Accommodation createdAccommodation =
            Accommodation.create(
                "타임세일 예약 테스트 호텔",
                "서울특별시 테스트구 할인로 1",
                "예약 타임세일 MySQL 통합 테스트용 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        ReflectionTestUtils.setField(
            createdAccommodation,
            "status",
            AccommodationStatus.ACTIVE
        );

        accommodation =
            accommodationRepository.saveAndFlush(
                createdAccommodation
            );

        Room createdRoom =
            Room.create(
                accommodation,
                "101",
                "타임세일 예약 테스트 객실",
                "정상 가격 100,000원 객실",
                100_000L,
                2,
                2
            );

        createdRoom.activate();

        room =
            roomRepository.saveAndFlush(
                createdRoom
            );
    }

    /**
     * 타임세일 가격 계산이 실행 시각에 영향을 받지 않도록
     * 고정된 한국 시간 Clock을 제공합니다.
     */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(
                FIXED_INSTANT,
                TEST_ZONE_ID
            );
        }
    }
}
