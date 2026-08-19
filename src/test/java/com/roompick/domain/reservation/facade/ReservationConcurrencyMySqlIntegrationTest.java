package com.roompick.domain.reservation.facade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.repository.ReservationIdempotencyRepository;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.testsupport.SharedMySqlTestContainer;

/**
 * 실제 MySQL 환경에서 동일 객실 예약 생성의
 * 동시성 제어를 검증합니다.
 *
 * 동일 객실의 겹치는 기간은 한 요청만 성공하고,
 * 겹치지 않는 기간과 서로 다른 객실은 독립적으로 처리되는지
 * 실제 트랜잭션과 Connection을 사용해 확인합니다.
 *
 * 각 요청이 서로 다른 Connection과 트랜잭션을 사용해야 하므로
 * 테스트 클래스에는 @Transactional을 적용하지 않습니다.
 */
@Tag("integration")
@SpringBootTest(
    properties = {
        /*
         * Testcontainers가 종료된 다음 Hibernate가
         * drop DDL을 실행하는 문제를 피하기 위해 create를 사용합니다.
         */
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-init-sql="
            + "SET SESSION innodb_lock_wait_timeout = 3"
    }
)
@ActiveProfiles("test")
class ReservationConcurrencyMySqlIntegrationTest {

    private static final int CONCURRENT_REQUEST_COUNT = 2;

    private static final String FIRST_IDEMPOTENCY_KEY =
        "reservation-concurrency-request-1";

    private static final String SECOND_IDEMPOTENCY_KEY =
        "reservation-concurrency-request-2";

    private static final String DATABASE_NAME =
        "roompick_reservation_lock_test";

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private static final Duration ASYNC_TIMEOUT =
        Duration.ofSeconds(15);

    private static final long MINIMUM_LOCK_WAIT_MILLIS =
        2_500L;

    private static final long MAXIMUM_LOCK_WAIT_MILLIS =
        6_000L;

    /**
     * application-test.yml의 H2 설정 대신
     * Testcontainers MySQL 접속 정보를 사용합니다.
     */
    @DynamicPropertySource
    static void registerMySqlProperties(
        DynamicPropertyRegistry registry
    ) {
        SharedMySqlTestContainer.createDatabaseIfAbsent(DATABASE_NAME);
        registry.add(
            "spring.datasource.url",
            () -> SharedMySqlTestContainer.jdbcUrl(DATABASE_NAME)
        );

        registry.add(
            "spring.datasource.username",
            () -> SharedMySqlTestContainer.USERNAME
        );

        registry.add(
            "spring.datasource.password",
            () -> SharedMySqlTestContainer.PASSWORD
        );

        registry.add(
            "spring.datasource.driver-class-name",
            () -> "com.mysql.cj.jdbc.Driver"
        );
    }

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

    @Autowired
    private ReservationIdempotencyRepository
        reservationIdempotencyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TestData testData;

    @BeforeEach
    void setUp() {
        testData = createTestData();
    }

    @AfterEach
    void tearDown() {
        reservationIdempotencyRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "서로 다른 회원이 동일 객실과 기간을 동시에 예약하면 "
            + "하나의 예약만 생성된다"
    )
    void concurrentReservationsForSameRoomAndPeriodCreateOnlyOneReservation()
        throws Exception {

        // given
        LocalDate checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(5);

        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        ReservationCreateRequestDto request =
            createRequest(
                testData.firstRoomId(),
                checkInDate,
                checkOutDate
            );

        // when
        List<ReservationAttemptResult> results =
            executeConcurrentReservations(
                request,
                request
            );

        // then
        assertSingleWinner(results);
    }

    @Test
    @DisplayName(
        "동일 객실의 숙박 기간이 일부 겹치는 동시 예약은 "
            + "하나만 생성된다"
    )
    void partiallyOverlappingReservationsCreateOnlyOneReservation()
        throws Exception {

        // given
        LocalDate firstCheckInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(5);

        ReservationCreateRequestDto firstRequest =
            createRequest(
                testData.firstRoomId(),
                firstCheckInDate,
                firstCheckInDate.plusDays(3)
            );

        ReservationCreateRequestDto secondRequest =
            createRequest(
                testData.firstRoomId(),
                firstCheckInDate.plusDays(2),
                firstCheckInDate.plusDays(5)
            );

        // when
        List<ReservationAttemptResult> results =
            executeConcurrentReservations(
                firstRequest,
                secondRequest
            );

        // then
        assertSingleWinner(results);
    }

    @Test
    @DisplayName(
        "MySQL Connection의 객실 락 대기 한도는 3초이다"
    )
    void mysqlConnectionUsesThreeSecondLockWaitTimeout() {
        // when
        Long lockWaitTimeoutSeconds =
            jdbcTemplate.queryForObject(
                "SELECT @@SESSION.innodb_lock_wait_timeout",
                Long.class
            );

        // then
        assertThat(lockWaitTimeoutSeconds)
            .isEqualTo(3L);
    }

    @Test
    @DisplayName(
        "동일 객실의 락을 3초 안에 획득하지 못하면 "
            + "예약 락 타임아웃을 반환한다"
    )
    void secondReservationTimesOutAfterThreeSeconds()
        throws Exception {

        // given
        LocalDate checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(5);

        ReservationCreateRequestDto request =
            createRequest(
                testData.firstRoomId(),
                checkInDate,
                checkInDate.plusDays(2)
            );

        CountDownLatch firstTransactionHoldingLock =
            new CountDownLatch(1);

        CountDownLatch releaseFirstTransaction =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(
                CONCURRENT_REQUEST_COUNT
            );

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        try {
            Future<ReservationAttemptResult> firstFuture =
                executorService.submit(() ->
                    transactionTemplate.execute(status -> {
                        ReservationAttemptResult result =
                            executeReservation(
                                testData.firstMemberId(),
                                FIRST_IDEMPOTENCY_KEY,
                                request
                            );

                        firstTransactionHoldingLock
                            .countDown();

                        awaitLatch(
                            releaseFirstTransaction,
                            ASYNC_TIMEOUT,
                            "첫 번째 객실 락 해제 신호를 "
                                + "기다리는 중 시간이 초과됐습니다."
                        );

                        return result;
                    })
                );

            awaitLatch(
                firstTransactionHoldingLock,
                ASYNC_TIMEOUT,
                "첫 번째 예약 트랜잭션이 객실 락을 "
                    + "유지해야 합니다."
            );

            Future<TimedReservationAttemptResult> secondFuture =
                executorService.submit(() -> {
                    long startedAt =
                        System.nanoTime();

                    ReservationAttemptResult result =
                        executeReservation(
                            testData.secondMemberId(),
                            SECOND_IDEMPOTENCY_KEY,
                            request
                        );

                    Duration elapsedTime =
                        Duration.ofNanos(
                            System.nanoTime()
                                - startedAt
                        );

                    return new TimedReservationAttemptResult(
                        result,
                        elapsedTime
                    );
                });

            TimedReservationAttemptResult timedSecondResult =
                secondFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            ReservationAttemptResult secondResult =
                timedSecondResult.result();

            assertThat(secondResult.success())
                .isFalse();

            assertThat(secondResult.errorCode())
                .isEqualTo(
                    ErrorCode.RESERVATION_LOCK_TIMEOUT
                );

            assertThat(
                timedSecondResult
                    .elapsedTime()
                    .toMillis()
            ).isBetween(
                MINIMUM_LOCK_WAIT_MILLIS,
                MAXIMUM_LOCK_WAIT_MILLIS
            );

            /*
             * 두 번째 요청의 타임아웃을 확인한 뒤에만
             * 첫 번째 트랜잭션을 커밋해 락을 해제합니다.
             */
            releaseFirstTransaction.countDown();

            ReservationAttemptResult firstResult =
                firstFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(firstResult.success())
                .isTrue();

            assertThat(
                reservationRepository.count()
            ).isEqualTo(1L);
        } finally {
            releaseFirstTransaction.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    @Test
    @DisplayName(
        "동일 객실의 숙박 기간이 겹치지 않는 동시 예약은 "
            + "모두 생성된다"
    )
    void nonOverlappingReservationsForSameRoomBothSucceed()
        throws Exception {

        // given
        LocalDate firstCheckInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(5);

        ReservationCreateRequestDto firstRequest =
            createRequest(
                testData.firstRoomId(),
                firstCheckInDate,
                firstCheckInDate.plusDays(2)
            );

        ReservationCreateRequestDto secondRequest =
            createRequest(
                testData.firstRoomId(),
                firstCheckInDate.plusDays(2),
                firstCheckInDate.plusDays(4)
            );

        // when
        List<ReservationAttemptResult> results =
            executeConcurrentReservations(
                firstRequest,
                secondRequest
            );

        // then
        assertThat(results)
            .allMatch(
                ReservationAttemptResult::success
            );

        assertThat(
            reservationRepository.count()
        ).isEqualTo(2L);
    }

    @Test
    @DisplayName(
        "서로 다른 객실의 예약은 한 객실의 락이 유지되는 동안에도 "
            + "서로 대기하지 않는다"
    )
    void reservationsForDifferentRoomsDoNotBlockEachOther()
        throws Exception {

        // given
        LocalDate checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(5);

        ReservationCreateRequestDto firstRequest =
            createRequest(
                testData.firstRoomId(),
                checkInDate,
                checkInDate.plusDays(2)
            );

        ReservationCreateRequestDto secondRequest =
            createRequest(
                testData.secondRoomId(),
                checkInDate,
                checkInDate.plusDays(2)
            );

        CountDownLatch firstTransactionHoldingLock =
            new CountDownLatch(1);

        CountDownLatch releaseFirstTransaction =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(
                CONCURRENT_REQUEST_COUNT
            );

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        try {
            Future<ReservationAttemptResult> firstFuture =
                executorService.submit(() ->
                    transactionTemplate.execute(status -> {
                        ReservationAttemptResult result =
                            executeReservation(
                                testData.firstMemberId(),
                                FIRST_IDEMPOTENCY_KEY,
                                firstRequest
                            );

                        firstTransactionHoldingLock
                            .countDown();

                        awaitLatch(
                            releaseFirstTransaction,
                            ASYNC_TIMEOUT,
                            "첫 번째 객실 락 해제 신호를 "
                                + "기다리는 중 시간이 초과됐습니다."
                        );

                        return result;
                    })
                );

            awaitLatch(
                firstTransactionHoldingLock,
                ASYNC_TIMEOUT,
                "첫 번째 예약 트랜잭션이 객실 락을 "
                    + "유지해야 합니다."
            );

            Future<ReservationAttemptResult> secondFuture =
                executorService.submit(() ->
                    executeReservation(
                        testData.secondMemberId(),
                        SECOND_IDEMPOTENCY_KEY,
                        secondRequest
                    )
                );

            /*
             * 첫 번째 트랜잭션을 해제하기 전에 두 번째 요청이
             * 완료돼야 서로 다른 객실이 독립적으로 처리된 것입니다.
             */
            ReservationAttemptResult secondResult =
                secondFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(secondResult.success())
                .isTrue();

            releaseFirstTransaction.countDown();

            ReservationAttemptResult firstResult =
                firstFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(firstResult.success())
                .isTrue();

            assertThat(
                reservationRepository.count()
            ).isEqualTo(2L);
        } finally {
            releaseFirstTransaction.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    @Test
    @DisplayName(
        "예약 생성 중 예외로 트랜잭션이 롤백되면 "
            + "다음 요청은 정상적으로 예약할 수 있다"
    )
    void rolledBackReservationReleasesRoomLock() {
        // given
        LocalDate today =
            LocalDate.now(TEST_ZONE_ID);

        ReservationCreateRequestDto invalidRequest =
            createRequest(
                testData.firstRoomId(),
                today.minusDays(1),
                today.plusDays(1)
            );

        ReservationCreateRequestDto validRequest =
            createRequest(
                testData.firstRoomId(),
                today.plusDays(5),
                today.plusDays(7)
            );

        // when
        ReservationAttemptResult failedResult =
            executeReservation(
                testData.firstMemberId(),
                FIRST_IDEMPOTENCY_KEY,
                invalidRequest
            );

        ReservationAttemptResult successfulResult =
            executeReservation(
                testData.secondMemberId(),
                SECOND_IDEMPOTENCY_KEY,
                validRequest
            );

        // then
        assertThat(failedResult.success())
            .isFalse();

        assertThat(failedResult.errorCode())
            .isEqualTo(
                ErrorCode.INVALID_STAY_PERIOD
            );

        assertThat(successfulResult.success())
            .isTrue();

        assertThat(
            reservationRepository.count()
        ).isEqualTo(1L);
    }

    private List<ReservationAttemptResult>
    executeConcurrentReservations(
        ReservationCreateRequestDto firstRequest,
        ReservationCreateRequestDto secondRequest
    ) throws Exception {
        CountDownLatch requestsReady =
            new CountDownLatch(
                CONCURRENT_REQUEST_COUNT
            );

        CountDownLatch startRequests =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(
                CONCURRENT_REQUEST_COUNT
            );

        try {
            Future<ReservationAttemptResult> firstFuture =
                executorService.submit(() ->
                    attemptReservation(
                        testData.firstMemberId(),
                        FIRST_IDEMPOTENCY_KEY,
                        firstRequest,
                        requestsReady,
                        startRequests
                    )
                );

            Future<ReservationAttemptResult> secondFuture =
                executorService.submit(() ->
                    attemptReservation(
                        testData.secondMemberId(),
                        SECOND_IDEMPOTENCY_KEY,
                        secondRequest,
                        requestsReady,
                        startRequests
                    )
                );

            awaitLatch(
                requestsReady,
                ASYNC_TIMEOUT,
                "두 예약 요청이 실행 준비를 "
                    + "완료해야 합니다."
            );

            startRequests.countDown();

            return List.of(
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
            startRequests.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void assertSingleWinner(
        List<ReservationAttemptResult> results
    ) {
        ReservationAttemptResult successfulResult =
            results.stream()
                .filter(
                    ReservationAttemptResult::success
                )
                .findFirst()
                .orElseThrow();

        assertThat(successfulResult.reservationId())
            .isNotNull();

        assertThat(successfulResult.errorCode())
            .isNull();

        assertThat(results)
            .filteredOn(result ->
                !result.success()
            )
            .singleElement()
            .extracting(
                ReservationAttemptResult::errorCode
            )
            .isEqualTo(
                ErrorCode.ROOM_NOT_AVAILABLE
            );

        assertThat(
            reservationRepository.count()
        ).isEqualTo(1L);
    }

    private ReservationCreateRequestDto createRequest(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        return new ReservationCreateRequestDto(
            roomId,
            checkInDate,
            checkOutDate,
            2
        );
    }

    /**
     * 시작 신호가 열리면 예약 생성을 요청합니다.
     *
     * ReservationFacade의 @Transactional로 인해 각 스레드는
     * 서로 다른 트랜잭션과 DB Connection을 사용합니다.
     */
    private ReservationAttemptResult attemptReservation(
        Long memberId,
        String idempotencyKey,
        ReservationCreateRequestDto request,
        CountDownLatch requestsReady,
        CountDownLatch startRequests
    ) {
        requestsReady.countDown();

        awaitLatch(
            startRequests,
            ASYNC_TIMEOUT,
            "예약 생성 시작 신호를 기다리는 중 "
                + "시간이 초과됐습니다."
        );

        return executeReservation(
            memberId,
            idempotencyKey,
            request
        );
    }

    private ReservationAttemptResult executeReservation(
        Long memberId,
        String idempotencyKey,
        ReservationCreateRequestDto request
    ) {
        try {
            ReservationCreateResponseDto response =
                reservationFacade.createReservation(
                    memberId,
                    idempotencyKey,
                    request
                );

            return ReservationAttemptResult.success(
                response.reservationId()
            );
        } catch (BusinessException exception) {
            return ReservationAttemptResult.failure(
                exception.getErrorCode()
            );
        }
    }

    /**
     * 테스트에 필요한 회원 2명과
     * 운영 중인 숙소·객실을 생성합니다.
     */
    private TestData createTestData() {
        Member firstMember =
            memberRepository.saveAndFlush(
                Member.create(
                    "reservation-lock-a@roompick.com",
                    "encoded-password",
                    "예약 락 테스트 회원 A"
                )
            );

        Member secondMember =
            memberRepository.saveAndFlush(
                Member.create(
                    "reservation-lock-b@roompick.com",
                    "encoded-password",
                    "예약 락 테스트 회원 B"
                )
            );

        Accommodation accommodation =
            Accommodation.create(
                "예약 락 테스트 호텔",
                "서울특별시 테스트구 예약로 1",
                "예약 생성 동시성 통합 테스트용 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        ReflectionTestUtils.setField(
            accommodation,
            "status",
            AccommodationStatus.ACTIVE
        );

        Accommodation savedAccommodation =
            accommodationRepository.saveAndFlush(
                accommodation
            );

        Room firstRoom =
            Room.create(
                savedAccommodation,
                "101",
                "예약 락 테스트 객실 A",
                "예약 생성 동시성 통합 테스트용 객실 A",
                100_000L,
                2,
                2
            );

        firstRoom.activate();

        Room savedFirstRoom =
            roomRepository.saveAndFlush(
                firstRoom
            );

        Room secondRoom =
            Room.create(
                savedAccommodation,
                "102",
                "예약 락 테스트 객실 B",
                "예약 생성 동시성 통합 테스트용 객실 B",
                120_000L,
                2,
                2
            );

        secondRoom.activate();

        Room savedSecondRoom =
            roomRepository.saveAndFlush(
                secondRoom
            );

        return new TestData(
            firstMember.getId(),
            secondMember.getId(),
            savedFirstRoom.getId(),
            savedSecondRoom.getId()
        );
    }

    private static void awaitLatch(
        CountDownLatch latch,
        Duration timeout,
        String timeoutMessage
    ) {
        try {
            boolean completed =
                latch.await(
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            if (!completed) {
                throw new IllegalStateException(
                    timeoutMessage
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "예약 동시성 테스트 대기 중 "
                    + "스레드가 중단됐습니다.",
                exception
            );
        }
    }

    private record TestData(
        Long firstMemberId,
        Long secondMemberId,
        Long firstRoomId,
        Long secondRoomId
    ) {
    }

    private record ReservationAttemptResult(
        boolean success,
        Long reservationId,
        ErrorCode errorCode
    ) {

        private static ReservationAttemptResult success(
            Long reservationId
        ) {
            return new ReservationAttemptResult(
                true,
                reservationId,
                null
            );
        }

        private static ReservationAttemptResult failure(
            ErrorCode errorCode
        ) {
            return new ReservationAttemptResult(
                false,
                null,
                errorCode
            );
        }
    }

    private record TimedReservationAttemptResult(
        ReservationAttemptResult result,
        Duration elapsedTime
    ) {
    }
}
