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
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 실제 MySQL 환경에서 동일 객실 예약 생성의
 * 동시성 제어를 검증합니다.
 *
 * 서로 다른 회원이 동일한 객실과 숙박 기간을
 * 동시에 예약하더라도 하나의 예약만 생성되어야 합니다.
 *
 * 각 요청이 서로 다른 Connection과 트랜잭션을 사용해야 하므로
 * 테스트 클래스에는 @Transactional을 적용하지 않습니다.
 */
@Tag("integration")
@Testcontainers
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
        "spring.datasource.hikari.maximum-pool-size=5"
    }
)
@ActiveProfiles("test")
class ReservationConcurrencyMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;

    private static final int CONCURRENT_REQUEST_COUNT = 2;

    private static final String DATABASE_NAME =
        "roompick_reservation_lock_test";

    private static final String DATABASE_USERNAME =
        "roompick";

    private static final String DATABASE_PASSWORD =
        "roompick-password";

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private static final Duration ASYNC_TIMEOUT =
        Duration.ofSeconds(15);

    @Container
    static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4")
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
     * Testcontainers MySQL 접속 정보를 사용합니다.
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
    private MemberRepository memberRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private TestData testData;

    @BeforeEach
    void setUp() {
        testData = createTestData();
    }

    @AfterEach
    void tearDown() {
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
            new ReservationCreateRequestDto(
                testData.roomId(),
                checkInDate,
                checkOutDate,
                2
            );

        /*
         * 두 작업 스레드가 모두 준비될 때까지 기다린 뒤
         * startRequests를 열어 거의 같은 시각에 요청합니다.
         */
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
                        request,
                        requestsReady,
                        startRequests
                    )
                );

            Future<ReservationAttemptResult> secondFuture =
                executorService.submit(() ->
                    attemptReservation(
                        testData.secondMemberId(),
                        request,
                        requestsReady,
                        startRequests
                    )
                );

            boolean allRequestsReady =
                requestsReady.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(allRequestsReady)
                .as(
                    "두 예약 요청이 실행 준비를 "
                        + "완료해야 합니다."
                )
                .isTrue();

            startRequests.countDown();

            ReservationAttemptResult firstResult =
                firstFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            ReservationAttemptResult secondResult =
                secondFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            // then
            List<ReservationAttemptResult> results =
                List.of(
                    firstResult,
                    secondResult
                );

            long successCount =
                results.stream()
                    .filter(
                        ReservationAttemptResult::success
                    )
                    .count();

            long failureCount =
                results.stream()
                    .filter(result ->
                        !result.success()
                    )
                    .count();

            assertThat(successCount)
                .as(
                    "동일 객실과 기간의 동시 예약 중 "
                        + "하나의 요청만 성공해야 합니다."
                )
                .isEqualTo(1L);

            assertThat(failureCount)
                .as(
                    "락을 나중에 획득한 요청은 "
                        + "예약 불가로 거절되어야 합니다."
                )
                .isEqualTo(1L);

            ReservationAttemptResult successfulResult =
                results.stream()
                    .filter(
                        ReservationAttemptResult::success
                    )
                    .findFirst()
                    .orElseThrow();

            ReservationAttemptResult failedResult =
                results.stream()
                    .filter(result ->
                        !result.success()
                    )
                    .findFirst()
                    .orElseThrow();

            assertThat(
                successfulResult.reservationId()
            ).isNotNull();

            assertThat(
                successfulResult.errorCode()
            ).isNull();

            assertThat(
                failedResult.reservationId()
            ).isNull();

            assertThat(
                failedResult.errorCode()
            ).isEqualTo(
                ErrorCode.ROOM_NOT_AVAILABLE
            );

            assertThat(
                reservationRepository.count()
            )
                .as(
                    "동일 객실과 기간에는 예약이 "
                        + "한 건만 저장되어야 합니다."
                )
                .isEqualTo(1L);
        } finally {
            /*
             * 준비 단계에서 예외가 발생하더라도
             * 대기 중인 작업 스레드를 해제합니다.
             */
            startRequests.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * 시작 신호가 열리면 예약 생성을 요청합니다.
     *
     * ReservationFacade의 @Transactional로 인해 각 스레드는
     * 서로 다른 트랜잭션과 DB Connection을 사용합니다.
     */
    private ReservationAttemptResult attemptReservation(
        Long memberId,
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

        try {
            ReservationCreateResponseDto response =
                reservationFacade.createReservation(
                    memberId,
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

        Room room =
            Room.create(
                savedAccommodation,
                "101",
                "예약 락 테스트 객실",
                "예약 생성 동시성 통합 테스트용 객실",
                100_000L,
                2,
                2
            );

        room.activate();

        Room savedRoom =
            roomRepository.saveAndFlush(
                room
            );

        return new TestData(
            firstMember.getId(),
            secondMember.getId(),
            savedRoom.getId()
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
        Long roomId
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
}
