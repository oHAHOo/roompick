package com.roompick.domain.reservation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.roompick.domain.reservation.repository.ReservationIdempotencyRepository;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 실제 MySQL 환경에서 예약 생성 요청의
 * 멱등성 처리를 검증합니다.
 *
 * 동일 회원과 멱등성 키 조합의 Unique Constraint,
 * 네이티브 Upsert 및 비관적 행 잠금이 실제 트랜잭션에서
 * 올바르게 동작하는지 확인합니다.
 *
 * 각 동시 요청은 서로 다른 Connection과 트랜잭션을
 * 사용해야 하므로 클래스에는 @Transactional을 적용하지 않습니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",
        "spring.datasource.hikari.maximum-pool-size=5"
    }
)
@ActiveProfiles("test")
class ReservationIdempotencyMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;

    private static final int CONCURRENT_REQUEST_COUNT = 2;

    private static final String DATABASE_NAME =
        "roompick_reservation_idempotency_test";

    private static final String DATABASE_USERNAME =
        "roompick";

    private static final String DATABASE_PASSWORD =
        "roompick-password";

    private static final String SEQUENTIAL_KEY =
        "reservation-idempotency-sequential";

    private static final String CONCURRENT_KEY =
        "reservation-idempotency-concurrent";

    private static final String CONFLICT_KEY =
        "reservation-idempotency-conflict";

    private static final String SHARED_MEMBER_KEY =
        "reservation-idempotency-shared-member";

    private static final String RETRY_KEY =
        "reservation-idempotency-retry";

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private static final Duration ASYNC_TIMEOUT =
        Duration.ofSeconds(15);

    @Container
    static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4")
        )
            .withDatabaseName(DATABASE_NAME)
            .withUsername(DATABASE_USERNAME)
            .withPassword(DATABASE_PASSWORD)
            .withStartupTimeout(
                Duration.ofMinutes(2)
            );

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

    @Autowired
    private ReservationIdempotencyRepository
        reservationIdempotencyRepository;

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
        "같은 회원이 동일한 멱등성 키와 요청을 순차 재전달하면 "
            + "기존 예약 결과를 반환한다"
    )
    void sequentialRetriesReturnSameReservation() {
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

        // when
        ReservationCreateResponseDto firstResponse =
            reservationFacade.createReservation(
                testData.firstMemberId(),
                SEQUENTIAL_KEY,
                request
            );

        ReservationCreateResponseDto secondResponse =
            reservationFacade.createReservation(
                testData.firstMemberId(),
                SEQUENTIAL_KEY,
                request
            );

        // then
        assertThat(firstResponse.reservationId())
            .isNotNull();

        assertThat(secondResponse.reservationId())
            .isEqualTo(
                firstResponse.reservationId()
            );

        assertThat(reservationRepository.count())
            .isEqualTo(1L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "같은 회원이 동일한 멱등성 키와 요청을 동시에 전달하면 "
            + "모든 응답이 같은 예약 ID를 반환한다"
    )
    void concurrentRetriesReturnSameReservation()
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

        // when
        List<ReservationAttemptResult> results =
            executeConcurrentRetries(
                testData.firstMemberId(),
                CONCURRENT_KEY,
                request
            );

        // then
        assertThat(results)
            .allMatch(
                ReservationAttemptResult::success
            );

        assertThat(results)
            .extracting(
                ReservationAttemptResult::reservationId
            )
            .doesNotContainNull()
            .containsOnly(
                results.get(0).reservationId()
            );

        assertThat(reservationRepository.count())
            .isEqualTo(1L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "같은 회원이 같은 멱등성 키로 다른 요청을 전달하면 "
            + "멱등성 충돌 예외를 반환한다"
    )
    void sameKeyWithDifferentRequestCausesConflict() {
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

        ReservationCreateRequestDto differentRequest =
            createRequest(
                testData.secondRoomId(),
                checkInDate,
                checkInDate.plusDays(2)
            );

        ReservationCreateResponseDto firstResponse =
            reservationFacade.createReservation(
                testData.firstMemberId(),
                CONFLICT_KEY,
                firstRequest
            );

        // when & then
        assertThatThrownBy(() ->
            reservationFacade.createReservation(
                testData.firstMemberId(),
                CONFLICT_KEY,
                differentRequest
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode
                    .RESERVATION_IDEMPOTENCY_CONFLICT
            );

        assertThat(firstResponse.reservationId())
            .isNotNull();

        assertThat(reservationRepository.count())
            .isEqualTo(1L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(1L);
    }

    @Test
    @DisplayName(
        "서로 다른 회원은 동일한 멱등성 키 문자열을 "
            + "독립적으로 사용할 수 있다"
    )
    void differentMembersCanUseSameIdempotencyKey() {
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

        // when
        ReservationCreateResponseDto firstResponse =
            reservationFacade.createReservation(
                testData.firstMemberId(),
                SHARED_MEMBER_KEY,
                firstRequest
            );

        ReservationCreateResponseDto secondResponse =
            reservationFacade.createReservation(
                testData.secondMemberId(),
                SHARED_MEMBER_KEY,
                secondRequest
            );

        // then
        assertThat(firstResponse.reservationId())
            .isNotNull();

        assertThat(secondResponse.reservationId())
            .isNotNull()
            .isNotEqualTo(
                firstResponse.reservationId()
            );

        assertThat(reservationRepository.count())
            .isEqualTo(2L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(2L);
    }

    @Test
    @DisplayName(
        "최초 예약 생성 트랜잭션이 실패하면 "
            + "같은 멱등성 키로 다시 요청할 수 있다"
    )
    void failedTransactionAllowsRetryWithSameKey() {
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

        // when & then
        assertThatThrownBy(() ->
            reservationFacade.createReservation(
                testData.firstMemberId(),
                RETRY_KEY,
                invalidRequest
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_STAY_PERIOD
            );

        assertThat(reservationRepository.count())
            .isZero();

        assertThat(
            reservationIdempotencyRepository.count()
        ).isZero();

        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                testData.firstMemberId(),
                RETRY_KEY,
                validRequest
            );

        assertThat(response.reservationId())
            .isNotNull();

        assertThat(reservationRepository.count())
            .isEqualTo(1L);

        assertThat(
            reservationIdempotencyRepository.count()
        ).isEqualTo(1L);
    }

    private List<ReservationAttemptResult>
    executeConcurrentRetries(
        Long memberId,
        String idempotencyKey,
        ReservationCreateRequestDto request
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
                        memberId,
                        idempotencyKey,
                        request,
                        requestsReady,
                        startRequests
                    )
                );

            Future<ReservationAttemptResult> secondFuture =
                executorService.submit(() ->
                    attemptReservation(
                        memberId,
                        idempotencyKey,
                        request,
                        requestsReady,
                        startRequests
                    )
                );

            awaitLatch(
                requestsReady,
                ASYNC_TIMEOUT,
                "두 멱등성 요청이 실행 준비를 "
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
            "멱등성 요청 시작 신호를 기다리는 중 "
                + "시간이 초과됐습니다."
        );

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

    private TestData createTestData() {
        Member firstMember =
            memberRepository.saveAndFlush(
                Member.create(
                    "reservation-idempotency-a@roompick.com",
                    "encoded-password",
                    "예약 멱등성 테스트 회원 A"
                )
            );

        Member secondMember =
            memberRepository.saveAndFlush(
                Member.create(
                    "reservation-idempotency-b@roompick.com",
                    "encoded-password",
                    "예약 멱등성 테스트 회원 B"
                )
            );

        Accommodation accommodation =
            Accommodation.create(
                "예약 멱등성 테스트 호텔",
                "서울특별시 테스트구 멱등로 1",
                "예약 생성 멱등성 통합 테스트용 숙소",
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
                "예약 멱등성 테스트 객실 A",
                "예약 생성 멱등성 통합 테스트용 객실 A",
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
                "예약 멱등성 테스트 객실 B",
                "예약 생성 멱등성 통합 테스트용 객실 B",
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
                "예약 멱등성 테스트 대기 중 "
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
}
