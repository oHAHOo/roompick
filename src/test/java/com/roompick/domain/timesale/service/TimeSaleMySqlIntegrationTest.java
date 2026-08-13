package com.roompick.domain.timesale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.roompick.domain.admin.timesale.dto.request.TimeSaleCreateRequestDto;
import com.roompick.domain.admin.timesale.facade.AdminTimeSaleFacade;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.entity.TimeSaleStatus;
import com.roompick.domain.timesale.repository.TimeSaleRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 실제 MySQL 환경에서 타임세일 저장·조회,
 * 할인 가격 계산과 상태 전환을 검증합니다.
 *
 * 테스트 시간은 고정 Clock으로 제어하며,
 * 각 테스트는 실제 MySQL 쿼리를 실행합니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
    properties = {
        /*
         * Testcontainers가 종료된 다음 Hibernate가
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
    TimeSaleMySqlIntegrationTest.FixedClockConfig.class
)
class TimeSaleMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;

    private static final String DATABASE_NAME =
        "roompick_time_sale_test";

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

    private static final LocalDateTime NOW =
        LocalDateTime.of(
            2026,
            8,
            12,
            12,
            0
        );

    private static final int CONCURRENT_REQUEST_COUNT = 2;

    private static final Duration ASYNC_TIMEOUT =
        Duration.ofSeconds(15);

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
    private TimeSaleService timeSaleService;

    @Autowired
    private TimeSalePriceService
        timeSalePriceService;

    @Autowired
    private TimeSaleRepository timeSaleRepository;

    @Autowired
    private AccommodationRepository
        accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private AdminTimeSaleFacade adminTimeSaleFacade;

    private Accommodation accommodation;

    private Room firstRoom;

    private Room secondRoom;

    @BeforeEach
    void setUp() {
        createTestData();
    }

    @AfterEach
    void tearDown() {
        /*
         * FK 참조 관계의 자식 테이블부터 정리합니다.
         */
        timeSaleRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "숙소 전체 타임세일을 MySQL에 저장하고 "
            + "할인된 객실 가격을 조회한다"
    )
    void accommodationSaleAppliesDiscountedPrice() {
        // given
        TimeSale timeSale =
            timeSaleService.create(
                accommodation,
                null,
                20,
                NOW.minusHours(1),
                NOW.plusHours(2)
            );

        // when
        long pricePerNight =
            timeSalePriceService
                .calculatePricePerNight(
                    firstRoom
                );

        TimeSale savedTimeSale =
            timeSaleRepository
                .findById(timeSale.getId())
                .orElseThrow();

        // then
        assertThat(savedTimeSale.getId())
            .isNotNull();

        assertThat(
            savedTimeSale
                .getAccommodation()
                .getId()
        ).isEqualTo(
            accommodation.getId()
        );

        assertThat(savedTimeSale.getRoom())
            .isNull();

        assertThat(
            savedTimeSale.getDiscountRate()
        ).isEqualTo(20);

        assertThat(savedTimeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );

        /*
         * 정상 가격 100,000원에
         * 20% 할인을 적용합니다.
         */
        assertThat(pricePerNight)
            .isEqualTo(80_000L);
    }

    @Test
    @DisplayName(
        "객실 전용 타임세일은 숙소 전체 "
            + "타임세일보다 우선 적용된다"
    )
    void roomSaleTakesPriorityOverAccommodationSale() {
        // given
        timeSaleService.create(
            accommodation,
            null,
            20,
            NOW.minusHours(1),
            NOW.plusHours(2)
        );

        timeSaleService.create(
            accommodation,
            firstRoom,
            30,
            NOW.minusMinutes(30),
            NOW.plusHours(1)
        );

        // when
        long firstRoomPrice =
            timeSalePriceService
                .calculatePricePerNight(
                    firstRoom
                );

        long secondRoomPrice =
            timeSalePriceService
                .calculatePricePerNight(
                    secondRoom
                );

        // then
        /*
         * 첫 번째 객실은 객실 전용
         * 30% 할인을 적용합니다.
         */
        assertThat(firstRoomPrice)
            .isEqualTo(70_000L);

        /*
         * 두 번째 객실에는 전용 할인이 없으므로
         * 숙소 전체 20% 할인을 적용합니다.
         *
         * 정상 가격 120,000원의
         * 20% 할인 가격은 96,000원입니다.
         */
        assertThat(secondRoomPrice)
            .isEqualTo(96_000L);
    }

    @Test
    @DisplayName(
        "현재 적용 가능한 타임세일이 없으면 "
            + "객실 정상 가격을 반환한다"
    )
    void returnsNormalPriceWhenNoSaleApplies() {
        // given
        timeSaleService.create(
            accommodation,
            null,
            20,
            NOW.plusHours(1),
            NOW.plusHours(3)
        );

        // when
        long pricePerNight =
            timeSalePriceService
                .calculatePricePerNight(
                    firstRoom
                );

        // then
        assertThat(pricePerNight)
            .isEqualTo(100_000L);
    }

    @Test
    @DisplayName(
        "같은 숙소 전체 타임세일 기간이 겹치면 "
            + "MySQL 조회 결과를 기준으로 등록을 차단한다"
    )
    void overlappingAccommodationSaleIsRejected() {
        // given
        timeSaleService.create(
            accommodation,
            null,
            20,
            NOW.plusHours(1),
            NOW.plusHours(4)
        );

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                accommodation,
                null,
                30,
                NOW.plusHours(3),
                NOW.plusHours(5)
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP
            );

        assertThat(
            timeSaleRepository.count()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "같은 객실의 타임세일 기간이 겹치면 "
            + "MySQL 조회 결과를 기준으로 등록을 차단한다"
    )
    void overlappingRoomSaleIsRejected() {
        // given
        timeSaleService.create(
            accommodation,
            firstRoom,
            20,
            NOW.plusHours(1),
            NOW.plusHours(4)
        );

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                accommodation,
                firstRoom,
                30,
                NOW.plusHours(2),
                NOW.plusHours(5)
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP
            );

        assertThat(
            timeSaleRepository.count()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "서로 다른 객실의 타임세일 기간은 "
            + "겹쳐도 각각 등록할 수 있다"
    )
    void overlappingSalesForDifferentRoomsAreAllowed() {
        // when
        TimeSale firstSale =
            timeSaleService.create(
                accommodation,
                firstRoom,
                20,
                NOW.plusHours(1),
                NOW.plusHours(4)
            );

        TimeSale secondSale =
            timeSaleService.create(
                accommodation,
                secondRoom,
                30,
                NOW.plusHours(1),
                NOW.plusHours(4)
            );

        // then
        assertThat(firstSale.getId())
            .isNotNull();

        assertThat(secondSale.getId())
            .isNotNull();

        assertThat(
            timeSaleRepository.count()
        ).isEqualTo(2L);
    }

    @Test
    @DisplayName(
        "시작 시각에 도달한 SCHEDULED 타임세일을 "
            + "MySQL에서 조회해 ACTIVE로 변경한다"
    )
    void scheduledSaleBecomesActive() {
        // given
        Clock creationClock =
            Clock.fixed(
                Instant.parse(
                    "2026-08-12T01:00:00Z"
                ),
                TEST_ZONE_ID
            );

        /*
         * 생성 시점만 한국 시간 10:00으로 사용합니다.
         *
         * 이 서비스는 타임세일 저장을 위해서만 사용하고,
         * 상태 전환은 Spring이 관리하는 timeSaleService를
         * 사용해 @Transactional이 적용되도록 합니다.
         */
        TimeSaleService creationService =
            new TimeSaleService(
                timeSaleRepository,
                creationClock
            );

        /*
         * 생성 시각은 한국 시간 10:00이고,
         * 타임세일은 11:00에 시작하므로
         * SCHEDULED 상태로 생성됩니다.
         */
        TimeSale timeSale =
            creationService.create(
                accommodation,
                null,
                20,
                NOW.minusHours(1),
                NOW.plusHours(1)
            );

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.SCHEDULED
            );

        // when
        int activatedCount =
            timeSaleService.activateDueSales();

        TimeSale activatedSale =
            timeSaleRepository
                .findById(timeSale.getId())
                .orElseThrow();

        // then
        assertThat(activatedCount)
            .isEqualTo(1);

        assertThat(activatedSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );
    }

    @Test
    @DisplayName(
        "종료 시각에 도달한 ACTIVE 타임세일을 "
            + "MySQL에서 조회해 ENDED로 변경한다"
    )
    void activeSaleBecomesEnded() {
        // given
        Clock creationClock =
            Clock.fixed(
                Instant.parse(
                    "2026-08-12T00:00:00Z"
                ),
                TEST_ZONE_ID
            );

        /*
         * 생성 시점만 한국 시간 09:00으로 사용합니다.
         *
         * 상태 전환은 Spring이 관리하는
         * timeSaleService를 사용합니다.
         */
        TimeSaleService creationService =
            new TimeSaleService(
                timeSaleRepository,
                creationClock
            );

        /*
         * 생성 시각은 한국 시간 09:00입니다.
         * 시작 시각은 08:00, 종료 시각은 11:00이므로
         * 생성 당시 ACTIVE 상태입니다.
         */
        TimeSale timeSale =
            creationService.create(
                accommodation,
                null,
                20,
                NOW.minusHours(4),
                NOW.minusHours(1)
            );

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );

        // when
        int endedCount =
            timeSaleService.endDueSales();

        TimeSale endedSale =
            timeSaleRepository
                .findById(timeSale.getId())
                .orElseThrow();

        // then
        assertThat(endedCount)
            .isEqualTo(1);

        assertThat(endedSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ENDED
            );

        assertThat(
            timeSalePriceService
                .calculatePricePerNight(
                    firstRoom
                )
        ).isEqualTo(100_000L);
    }

    @Test
    @DisplayName(
        "같은 숙소 전체 타임세일의 겹치는 동시 등록은 한 건만 저장된다"
    )
    void concurrentOverlappingAccommodationSalesCreateOnlyOne()
        throws Exception {
        TimeSaleCreateRequestDto request =
            new TimeSaleCreateRequestDto(
                null,
                20,
                NOW.plusHours(1),
                NOW.plusHours(4)
            );

        List<ErrorCode> results =
            executeConcurrentCreates(
                request,
                request
            );

        assertThat(results)
            .filteredOn(errorCode -> errorCode == null)
            .hasSize(1);

        assertThat(results)
            .filteredOn(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP::equals
            )
            .hasSize(1);

        assertThat(timeSaleRepository.count())
            .isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "같은 객실 타임세일의 겹치는 동시 등록은 한 건만 저장된다"
    )
    void concurrentOverlappingRoomSalesCreateOnlyOne()
        throws Exception {
        TimeSaleCreateRequestDto request =
            new TimeSaleCreateRequestDto(
                firstRoom.getId(),
                20,
                NOW.plusHours(1),
                NOW.plusHours(4)
            );

        List<ErrorCode> results =
            executeConcurrentCreates(
                request,
                request
            );

        assertThat(results)
            .filteredOn(errorCode -> errorCode == null)
            .hasSize(1);

        assertThat(results)
            .filteredOn(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP::equals
            )
            .hasSize(1);

        assertThat(timeSaleRepository.count())
            .isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "서로 다른 객실의 타임세일은 동시에 등록할 수 있다"
    )
    void concurrentSalesForDifferentRoomsBothSucceed()
        throws Exception {
        TimeSaleCreateRequestDto firstRequest =
            new TimeSaleCreateRequestDto(
                firstRoom.getId(),
                20,
                NOW.plusHours(1),
                NOW.plusHours(4)
            );

        TimeSaleCreateRequestDto secondRequest =
            new TimeSaleCreateRequestDto(
                secondRoom.getId(),
                30,
                NOW.plusHours(1),
                NOW.plusHours(4)
            );

        List<ErrorCode> results =
            executeConcurrentCreates(
                firstRequest,
                secondRequest
            );

        assertThat(results)
            .containsOnlyNulls();

        assertThat(timeSaleRepository.count())
            .isEqualTo(2L);
    }

    private List<ErrorCode> executeConcurrentCreates(
        TimeSaleCreateRequestDto firstRequest,
        TimeSaleCreateRequestDto secondRequest
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(
            CONCURRENT_REQUEST_COUNT
        );

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
            Executors.newFixedThreadPool(
                CONCURRENT_REQUEST_COUNT
            );

        try {
            Future<ErrorCode> firstFuture =
                executor.submit(() ->
                    attemptCreate(
                        firstRequest,
                        ready,
                        start
                    )
                );

            Future<ErrorCode> secondFuture =
                executor.submit(() ->
                    attemptCreate(
                        secondRequest,
                        ready,
                        start
                    )
                );

            boolean bothReady = ready.await(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            assertThat(bothReady).isTrue();
            start.countDown();

            return Arrays.asList(
                firstFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                ),
                secondFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    private ErrorCode attemptCreate(
        TimeSaleCreateRequestDto request,
        CountDownLatch ready,
        CountDownLatch start
    ) {
        ready.countDown();

        try {
            boolean started = start.await(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            if (!started) {
                throw new IllegalStateException(
                    "타임세일 동시 등록 시작 신호 대기 시간이 초과됐습니다."
                );
            }

            adminTimeSaleFacade.create(
                accommodation.getId(),
                request
            );

            return null;
        } catch (BusinessException exception) {
            return exception.getErrorCode();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "타임세일 동시 등록 테스트가 중단됐습니다.",
                exception
            );
        }
    }

    /**
     * 테스트에 필요한 운영 중 숙소와
     * 객실 두 개를 생성합니다.
     */
    private void createTestData() {
        Accommodation createdAccommodation =
            Accommodation.create(
                "타임세일 테스트 호텔",
                "서울특별시 테스트구 할인로 1",
                "타임세일 MySQL 통합 테스트용 숙소",
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

        Room createdFirstRoom =
            Room.create(
                accommodation,
                "101",
                "타임세일 테스트 객실 A",
                "정상 가격 100,000원 객실",
                100_000L,
                2,
                2
            );

        createdFirstRoom.activate();

        firstRoom =
            roomRepository.saveAndFlush(
                createdFirstRoom
            );

        Room createdSecondRoom =
            Room.create(
                accommodation,
                "102",
                "타임세일 테스트 객실 B",
                "정상 가격 120,000원 객실",
                120_000L,
                2,
                2
            );

        createdSecondRoom.activate();

        secondRoom =
            roomRepository.saveAndFlush(
                createdSecondRoom
            );
    }

    /**
     * 테스트 실행 시각에 영향을 받지 않도록
     * 고정된 한국 시간 Clock을 제공합니다.
     *
     * @Primary를 적용해 운영 Clock Bean보다
     * 테스트 Clock을 우선 주입합니다.
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
