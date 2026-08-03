package com.roompick.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 실제 MySQL에서 Payment 비관적 쓰기 락의 동작을 검증합니다.
 *
 * 동일 Payment에 대한 상태 변경 요청은 순차 처리되어야 하고,
 * 서로 다른 Payment에 대한 요청은 불필요하게 서로 대기하지 않아야 합니다.
 *
 * 각 스레드가 서로 다른 DB Connection과 트랜잭션을 사용해야 하므로
 * 테스트 클래스에는 @Transactional을 적용하지 않습니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",
        "spring.datasource.hikari.maximum-pool-size=5"
    }
)
@ActiveProfiles("test")
class PaymentPessimisticLockMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;

    private static final String DATABASE_NAME =
        "roompick_lock_test";

    private static final String DATABASE_USERNAME =
        "roompick";

    private static final String DATABASE_PASSWORD =
        "roompick-password";

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private static final String FIRST_PORTONE_TRANSACTION_ID =
        "transaction-lock-test-001";

    private static final String SECOND_PORTONE_TRANSACTION_ID =
        "transaction-lock-test-002";

    private static final Duration LOCK_CHECK_DURATION =
        Duration.ofMillis(500);

    private static final Duration ASYNC_TIMEOUT =
        Duration.ofSeconds(10);

    private static final Duration DIFFERENT_PAYMENT_COMPLETION_TIMEOUT =
        Duration.ofSeconds(3);

    @Container
    @ServiceConnection
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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @AfterEach
    void tearDown() {
        paymentRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "동일 Payment 승인 요청이 동시에 실행되면 "
            + "두 번째 요청은 락을 기다린 뒤 최신 상태에서 거절된다"
    )
    void concurrentPaymentTransitionWaitsForPessimisticLock()
        throws Exception {

        // given
        LocalDateTime now =
            LocalDateTime
                .now(TEST_ZONE_ID)
                .withNano(0);

        LocalDateTime firstApprovedAt =
            now.plusSeconds(1);

        LocalDateTime secondApprovedAt =
            now.plusSeconds(2);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        TransactionTemplate firstTransactionTemplate =
            createNewTransactionTemplate();

        TransactionTemplate secondTransactionTemplate =
            createNewTransactionTemplate();

        CountDownLatch firstLockAcquired =
            new CountDownLatch(1);

        CountDownLatch releaseFirstTransaction =
            new CountDownLatch(1);

        CountDownLatch secondLockAttemptStarted =
            new CountDownLatch(1);

        CountDownLatch secondAttemptFinished =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        Future<Void> firstFuture = null;
        Future<SecondAttemptResult> secondFuture = null;

        try {
            firstFuture =
                executorService.submit(() -> {
                    firstTransactionTemplate
                        .executeWithoutResult(
                            transactionStatus -> {
                                Payment lockedPayment =
                                    paymentService
                                        .findForPaymentTransitionForUpdate(
                                            testData.paymentId(),
                                            testData.memberId()
                                        );

                                firstLockAcquired.countDown();

                                awaitLatch(
                                    releaseFirstTransaction,
                                    ASYNC_TIMEOUT,
                                    "첫 번째 승인 트랜잭션 해제 신호를 "
                                        + "기다리는 중 시간이 초과됐습니다."
                                );

                                Reservation reservation =
                                    lockedPayment.getReservation();

                                paymentService.approvePortOnePayment(
                                    lockedPayment,
                                    FIRST_PORTONE_TRANSACTION_ID,
                                    testData.amount(),
                                    firstApprovedAt
                                );

                                reservationService.confirmPayment(
                                    reservation,
                                    testData.memberId(),
                                    firstApprovedAt
                                );
                            }
                        );

                    return null;
                });

            assertThat(
                firstLockAcquired.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            secondFuture =
                executorService.submit(() -> {
                    long startedAtNanos =
                        System.nanoTime();

                    try {
                        secondTransactionTemplate
                            .executeWithoutResult(
                                transactionStatus -> {
                                    secondLockAttemptStarted
                                        .countDown();

                                    Payment lockedPayment =
                                        paymentService
                                            .findForPaymentTransitionForUpdate(
                                                testData.paymentId(),
                                                testData.memberId()
                                            );

                                    paymentService.approvePortOnePayment(
                                        lockedPayment,
                                        SECOND_PORTONE_TRANSACTION_ID,
                                        testData.amount(),
                                        secondApprovedAt
                                    );
                                }
                            );

                        return SecondAttemptResult
                            .unexpectedSuccess(
                                elapsedMillis(startedAtNanos)
                            );
                    } catch (BusinessException exception) {
                        return SecondAttemptResult.failure(
                            exception.getErrorCode(),
                            elapsedMillis(startedAtNanos)
                        );
                    } finally {
                        secondAttemptFinished.countDown();
                    }
                });

            assertThat(
                secondLockAttemptStarted.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            boolean secondCompletedBeforeLockRelease =
                secondAttemptFinished.await(
                    LOCK_CHECK_DURATION.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(secondCompletedBeforeLockRelease)
                .as(
                    "첫 번째 트랜잭션이 락을 보유하는 동안 "
                        + "두 번째 승인 요청은 완료되면 안 됩니다."
                )
                .isFalse();

            releaseFirstTransaction.countDown();

            firstFuture.get(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            SecondAttemptResult secondResult =
                secondFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            // then
            assertThat(secondResult.success())
                .isFalse();

            assertThat(secondResult.errorCode())
                .isEqualTo(
                    ErrorCode.INVALID_PAYMENT_STATUS
                );

            assertThat(secondResult.elapsedMillis())
                .isGreaterThanOrEqualTo(
                    LOCK_CHECK_DURATION.toMillis()
                );

            Payment savedPayment =
                paymentRepository
                    .findById(testData.paymentId())
                    .orElseThrow();

            Reservation savedReservation =
                reservationRepository
                    .findById(testData.reservationId())
                    .orElseThrow();

            assertThat(savedPayment.getStatus())
                .isEqualTo(PaymentStatus.PAID);

            assertThat(savedPayment.getPortOneTransactionId())
                .isEqualTo(
                    FIRST_PORTONE_TRANSACTION_ID
                );

            assertThat(savedPayment.getPortOneTransactionId())
                .isNotEqualTo(
                    SECOND_PORTONE_TRANSACTION_ID
                );

            assertThat(savedPayment.getApprovedAt())
                .isNotNull();

            assertThat(savedPayment.getFailedAt())
                .isNull();

            assertThat(savedReservation.getStatus())
                .isEqualTo(
                    ReservationStatus.CONFIRMED
                );

            assertThat(savedReservation.getCanceledAt())
                .isNull();
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
        "승인이 먼저 락을 획득하면 실패 요청은 대기 후 거절되고 "
            + "Payment와 Reservation은 승인 상태로 유지된다"
    )
    void approvalFirstRejectsConcurrentFailure()
        throws Exception {

        // given
        LocalDateTime now =
            LocalDateTime
                .now(TEST_ZONE_ID)
                .withNano(0);

        LocalDateTime approvedAt =
            now.plusSeconds(1);

        LocalDateTime failedAt =
            now.plusSeconds(2);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        TransactionTemplate approvalTransactionTemplate =
            createNewTransactionTemplate();

        TransactionTemplate failureTransactionTemplate =
            createNewTransactionTemplate();

        CountDownLatch approvalLockAcquired =
            new CountDownLatch(1);

        CountDownLatch releaseApprovalTransaction =
            new CountDownLatch(1);

        CountDownLatch failureLockAttemptStarted =
            new CountDownLatch(1);

        CountDownLatch failureAttemptFinished =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        Future<Void> approvalFuture = null;
        Future<SecondAttemptResult> failureFuture = null;

        try {
            approvalFuture =
                executorService.submit(() -> {
                    approvalTransactionTemplate
                        .executeWithoutResult(
                            transactionStatus -> {
                                Payment lockedPayment =
                                    paymentService
                                        .findForPaymentTransitionForUpdate(
                                            testData.paymentId(),
                                            testData.memberId()
                                        );

                                approvalLockAcquired.countDown();

                                awaitLatch(
                                    releaseApprovalTransaction,
                                    ASYNC_TIMEOUT,
                                    "승인 트랜잭션 해제 신호를 "
                                        + "기다리는 중 시간이 초과됐습니다."
                                );

                                Reservation reservation =
                                    lockedPayment.getReservation();

                                paymentService.approvePayment(
                                    lockedPayment,
                                    testData.amount(),
                                    approvedAt
                                );

                                reservationService.confirmPayment(
                                    reservation,
                                    testData.memberId(),
                                    approvedAt
                                );
                            }
                        );

                    return null;
                });

            assertThat(
                approvalLockAcquired.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            failureFuture =
                executorService.submit(() -> {
                    long startedAtNanos =
                        System.nanoTime();

                    try {
                        failureTransactionTemplate
                            .executeWithoutResult(
                                transactionStatus -> {
                                    failureLockAttemptStarted
                                        .countDown();

                                    Payment lockedPayment =
                                        paymentService
                                            .findForPaymentTransitionForUpdate(
                                                testData.paymentId(),
                                                testData.memberId()
                                            );

                                    paymentService.failPayment(
                                        lockedPayment,
                                        failedAt
                                    );

                                    reservationService
                                        .cancelByPaymentFailure(
                                            lockedPayment.getReservation(),
                                            testData.memberId(),
                                            failedAt
                                        );
                                }
                            );

                        return SecondAttemptResult
                            .unexpectedSuccess(
                                elapsedMillis(startedAtNanos)
                            );
                    } catch (BusinessException exception) {
                        return SecondAttemptResult.failure(
                            exception.getErrorCode(),
                            elapsedMillis(startedAtNanos)
                        );
                    } finally {
                        failureAttemptFinished.countDown();
                    }
                });

            assertThat(
                failureLockAttemptStarted.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            boolean failureCompletedBeforeLockRelease =
                failureAttemptFinished.await(
                    LOCK_CHECK_DURATION.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(failureCompletedBeforeLockRelease)
                .as(
                    "승인 트랜잭션이 락을 보유하는 동안 "
                        + "실패 요청은 완료되면 안 됩니다."
                )
                .isFalse();

            releaseApprovalTransaction.countDown();

            approvalFuture.get(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            SecondAttemptResult failureResult =
                failureFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            // then
            assertThat(failureResult.success())
                .isFalse();

            assertThat(failureResult.errorCode())
                .isEqualTo(
                    ErrorCode.INVALID_PAYMENT_STATUS
                );

            assertThat(failureResult.elapsedMillis())
                .isGreaterThanOrEqualTo(
                    LOCK_CHECK_DURATION.toMillis()
                );

            Payment savedPayment =
                paymentRepository
                    .findById(testData.paymentId())
                    .orElseThrow();

            Reservation savedReservation =
                reservationRepository
                    .findById(testData.reservationId())
                    .orElseThrow();

            assertThat(savedPayment.getStatus())
                .isEqualTo(PaymentStatus.PAID);

            assertThat(savedPayment.getApprovedAt())
                .isNotNull();

            assertThat(savedPayment.getFailedAt())
                .isNull();

            assertThat(savedReservation.getStatus())
                .isEqualTo(
                    ReservationStatus.CONFIRMED
                );

            assertThat(savedReservation.getCanceledAt())
                .isNull();
        } finally {
            releaseApprovalTransaction.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    @Test
    @DisplayName(
        "실패가 먼저 락을 획득하면 승인 요청은 대기 후 거절되고 "
            + "Payment와 Reservation은 취소 상태로 유지된다"
    )
    void failureFirstRejectsConcurrentApproval()
        throws Exception {

        // given
        LocalDateTime now =
            LocalDateTime
                .now(TEST_ZONE_ID)
                .withNano(0);

        LocalDateTime failedAt =
            now.plusSeconds(1);

        LocalDateTime approvedAt =
            now.plusSeconds(2);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        TransactionTemplate failureTransactionTemplate =
            createNewTransactionTemplate();

        TransactionTemplate approvalTransactionTemplate =
            createNewTransactionTemplate();

        CountDownLatch failureLockAcquired =
            new CountDownLatch(1);

        CountDownLatch releaseFailureTransaction =
            new CountDownLatch(1);

        CountDownLatch approvalLockAttemptStarted =
            new CountDownLatch(1);

        CountDownLatch approvalAttemptFinished =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        Future<Void> failureFuture = null;
        Future<SecondAttemptResult> approvalFuture = null;

        try {
            failureFuture =
                executorService.submit(() -> {
                    failureTransactionTemplate
                        .executeWithoutResult(
                            transactionStatus -> {
                                Payment lockedPayment =
                                    paymentService
                                        .findForPaymentTransitionForUpdate(
                                            testData.paymentId(),
                                            testData.memberId()
                                        );

                                failureLockAcquired.countDown();

                                awaitLatch(
                                    releaseFailureTransaction,
                                    ASYNC_TIMEOUT,
                                    "실패 트랜잭션 해제 신호를 "
                                        + "기다리는 중 시간이 초과됐습니다."
                                );

                                Reservation reservation =
                                    lockedPayment.getReservation();

                                paymentService.failPayment(
                                    lockedPayment,
                                    failedAt
                                );

                                reservationService
                                    .cancelByPaymentFailure(
                                        reservation,
                                        testData.memberId(),
                                        failedAt
                                    );
                            }
                        );

                    return null;
                });

            assertThat(
                failureLockAcquired.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            approvalFuture =
                executorService.submit(() -> {
                    long startedAtNanos =
                        System.nanoTime();

                    try {
                        approvalTransactionTemplate
                            .executeWithoutResult(
                                transactionStatus -> {
                                    approvalLockAttemptStarted
                                        .countDown();

                                    Payment lockedPayment =
                                        paymentService
                                            .findForPaymentTransitionForUpdate(
                                                testData.paymentId(),
                                                testData.memberId()
                                            );

                                    paymentService.approvePayment(
                                        lockedPayment,
                                        testData.amount(),
                                        approvedAt
                                    );

                                    reservationService.confirmPayment(
                                        lockedPayment.getReservation(),
                                        testData.memberId(),
                                        approvedAt
                                    );
                                }
                            );

                        return SecondAttemptResult
                            .unexpectedSuccess(
                                elapsedMillis(startedAtNanos)
                            );
                    } catch (BusinessException exception) {
                        return SecondAttemptResult.failure(
                            exception.getErrorCode(),
                            elapsedMillis(startedAtNanos)
                        );
                    } finally {
                        approvalAttemptFinished.countDown();
                    }
                });

            assertThat(
                approvalLockAttemptStarted.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            boolean approvalCompletedBeforeLockRelease =
                approvalAttemptFinished.await(
                    LOCK_CHECK_DURATION.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(approvalCompletedBeforeLockRelease)
                .as(
                    "실패 트랜잭션이 락을 보유하는 동안 "
                        + "승인 요청은 완료되면 안 됩니다."
                )
                .isFalse();

            releaseFailureTransaction.countDown();

            failureFuture.get(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            SecondAttemptResult approvalResult =
                approvalFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            // then
            assertThat(approvalResult.success())
                .isFalse();

            assertThat(approvalResult.errorCode())
                .isEqualTo(
                    ErrorCode.INVALID_PAYMENT_STATUS
                );

            assertThat(approvalResult.elapsedMillis())
                .isGreaterThanOrEqualTo(
                    LOCK_CHECK_DURATION.toMillis()
                );

            Payment savedPayment =
                paymentRepository
                    .findById(testData.paymentId())
                    .orElseThrow();

            Reservation savedReservation =
                reservationRepository
                    .findById(testData.reservationId())
                    .orElseThrow();

            assertThat(savedPayment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

            assertThat(savedPayment.getApprovedAt())
                .isNull();

            assertThat(savedPayment.getFailedAt())
                .isNotNull();

            assertThat(savedReservation.getStatus())
                .isEqualTo(
                    ReservationStatus.CANCELED
                );

            assertThat(savedReservation.getCanceledAt())
                .isNotNull();

            assertThat(savedPayment.getFailedAt())
                .isEqualTo(
                    savedReservation.getCanceledAt()
                );
        } finally {
            releaseFailureTransaction.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    @Test
    @DisplayName(
        "동일 Payment 실패 요청이 동시에 실행되면 "
            + "두 번째 요청은 락을 기다린 뒤 최신 FAILED 상태에서 거절된다"
    )
    void concurrentFailureRequestsOnlyOneSucceeds()
        throws Exception {

        // given
        LocalDateTime now =
            LocalDateTime
                .now(TEST_ZONE_ID)
                .withNano(0);

        LocalDateTime firstFailedAt =
            now.plusSeconds(1);

        LocalDateTime secondFailedAt =
            now.plusSeconds(2);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        TransactionTemplate firstTransactionTemplate =
            createNewTransactionTemplate();

        TransactionTemplate secondTransactionTemplate =
            createNewTransactionTemplate();

        CountDownLatch firstLockAcquired =
            new CountDownLatch(1);

        CountDownLatch releaseFirstTransaction =
            new CountDownLatch(1);

        CountDownLatch secondLockAttemptStarted =
            new CountDownLatch(1);

        CountDownLatch secondAttemptFinished =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        Future<Void> firstFuture = null;
        Future<SecondAttemptResult> secondFuture = null;

        try {
            firstFuture =
                executorService.submit(() -> {
                    firstTransactionTemplate
                        .executeWithoutResult(
                            transactionStatus -> {
                                Payment lockedPayment =
                                    paymentService
                                        .findForPaymentTransitionForUpdate(
                                            testData.paymentId(),
                                            testData.memberId()
                                        );

                                firstLockAcquired.countDown();

                                awaitLatch(
                                    releaseFirstTransaction,
                                    ASYNC_TIMEOUT,
                                    "첫 번째 실패 트랜잭션 해제 신호를 "
                                        + "기다리는 중 시간이 초과됐습니다."
                                );

                                Reservation reservation =
                                    lockedPayment.getReservation();

                                paymentService.failPayment(
                                    lockedPayment,
                                    firstFailedAt
                                );

                                reservationService
                                    .cancelByPaymentFailure(
                                        reservation,
                                        testData.memberId(),
                                        firstFailedAt
                                    );
                            }
                        );

                    return null;
                });

            assertThat(
                firstLockAcquired.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            secondFuture =
                executorService.submit(() -> {
                    long startedAtNanos =
                        System.nanoTime();

                    try {
                        secondTransactionTemplate
                            .executeWithoutResult(
                                transactionStatus -> {
                                    secondLockAttemptStarted
                                        .countDown();

                                    Payment lockedPayment =
                                        paymentService
                                            .findForPaymentTransitionForUpdate(
                                                testData.paymentId(),
                                                testData.memberId()
                                            );

                                    paymentService.failPayment(
                                        lockedPayment,
                                        secondFailedAt
                                    );

                                    reservationService
                                        .cancelByPaymentFailure(
                                            lockedPayment.getReservation(),
                                            testData.memberId(),
                                            secondFailedAt
                                        );
                                }
                            );

                        return SecondAttemptResult
                            .unexpectedSuccess(
                                elapsedMillis(startedAtNanos)
                            );
                    } catch (BusinessException exception) {
                        return SecondAttemptResult.failure(
                            exception.getErrorCode(),
                            elapsedMillis(startedAtNanos)
                        );
                    } finally {
                        secondAttemptFinished.countDown();
                    }
                });

            assertThat(
                secondLockAttemptStarted.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            boolean secondCompletedBeforeLockRelease =
                secondAttemptFinished.await(
                    LOCK_CHECK_DURATION.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(secondCompletedBeforeLockRelease)
                .as(
                    "첫 번째 실패 트랜잭션이 락을 보유하는 동안 "
                        + "두 번째 실패 요청은 완료되면 안 됩니다."
                )
                .isFalse();

            releaseFirstTransaction.countDown();

            firstFuture.get(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            SecondAttemptResult secondResult =
                secondFuture.get(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                );

            // then
            assertThat(secondResult.success())
                .isFalse();

            assertThat(secondResult.errorCode())
                .isEqualTo(
                    ErrorCode.INVALID_PAYMENT_STATUS
                );

            assertThat(secondResult.elapsedMillis())
                .isGreaterThanOrEqualTo(
                    LOCK_CHECK_DURATION.toMillis()
                );

            Payment savedPayment =
                paymentRepository
                    .findById(testData.paymentId())
                    .orElseThrow();

            Reservation savedReservation =
                reservationRepository
                    .findById(testData.reservationId())
                    .orElseThrow();

            assertThat(savedPayment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

            assertThat(savedPayment.getApprovedAt())
                .isNull();

            assertThat(savedPayment.getFailedAt())
                .isEqualTo(firstFailedAt);

            assertThat(savedPayment.getFailedAt())
                .isNotEqualTo(secondFailedAt);

            assertThat(savedReservation.getStatus())
                .isEqualTo(
                    ReservationStatus.CANCELED
                );

            assertThat(savedReservation.getCanceledAt())
                .isEqualTo(firstFailedAt);

            assertThat(savedPayment.getFailedAt())
                .isEqualTo(
                    savedReservation.getCanceledAt()
                );
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
        "서로 다른 Payment 상태 변경 요청은 "
            + "각각의 락을 획득하고 동시에 처리된다"
    )
    void differentPaymentsDoNotBlockEachOther()
        throws Exception {

        // given
        LocalDateTime now =
            LocalDateTime
                .now(TEST_ZONE_ID)
                .withNano(0);

        LocalDateTime firstApprovedAt =
            now.plusSeconds(1);

        LocalDateTime secondApprovedAt =
            now.plusSeconds(2);

        Member member =
            createMember(
                "different-payments"
            );

        TestData firstTestData =
            createTestData(
                member,
                now.plusMinutes(10),
                1
            );

        TestData secondTestData =
            createTestData(
                member,
                now.plusMinutes(10),
                2
            );

        assertThat(firstTestData.paymentId())
            .isNotEqualTo(
                secondTestData.paymentId()
            );

        assertThat(firstTestData.reservationId())
            .isNotEqualTo(
                secondTestData.reservationId()
            );

        assertThat(firstTestData.memberId())
            .isEqualTo(
                secondTestData.memberId()
            );

        TransactionTemplate firstTransactionTemplate =
            createNewTransactionTemplate();

        TransactionTemplate secondTransactionTemplate =
            createNewTransactionTemplate();

        CountDownLatch firstLockAcquired =
            new CountDownLatch(1);

        CountDownLatch releaseFirstTransaction =
            new CountDownLatch(1);

        CountDownLatch secondTransactionFinished =
            new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        Future<Void> firstFuture = null;
        Future<Void> secondFuture = null;

        try {
            firstFuture =
                executorService.submit(() -> {
                    firstTransactionTemplate
                        .executeWithoutResult(
                            transactionStatus -> {
                                Payment lockedPayment =
                                    paymentService
                                        .findForPaymentTransitionForUpdate(
                                            firstTestData.paymentId(),
                                            firstTestData.memberId()
                                        );

                                firstLockAcquired.countDown();

                                awaitLatch(
                                    releaseFirstTransaction,
                                    ASYNC_TIMEOUT,
                                    "첫 번째 결제 트랜잭션 해제 신호를 "
                                        + "기다리는 중 시간이 초과됐습니다."
                                );

                                Reservation reservation =
                                    lockedPayment.getReservation();

                                paymentService.approvePayment(
                                    lockedPayment,
                                    firstTestData.amount(),
                                    firstApprovedAt
                                );

                                reservationService.confirmPayment(
                                    reservation,
                                    firstTestData.memberId(),
                                    firstApprovedAt
                                );
                            }
                        );

                    return null;
                });

            assertThat(
                firstLockAcquired.await(
                    ASYNC_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            ).isTrue();

            secondFuture =
                executorService.submit(() -> {
                    try {
                        secondTransactionTemplate
                            .executeWithoutResult(
                                transactionStatus -> {
                                    Payment lockedPayment =
                                        paymentService
                                            .findForPaymentTransitionForUpdate(
                                                secondTestData.paymentId(),
                                                secondTestData.memberId()
                                            );

                                    Reservation reservation =
                                        lockedPayment.getReservation();

                                    paymentService.approvePayment(
                                        lockedPayment,
                                        secondTestData.amount(),
                                        secondApprovedAt
                                    );

                                    reservationService.confirmPayment(
                                        reservation,
                                        secondTestData.memberId(),
                                        secondApprovedAt
                                    );
                                }
                            );

                        return null;
                    } finally {
                        secondTransactionFinished.countDown();
                    }
                });

            boolean secondCompletedWhileFirstLockHeld =
                secondTransactionFinished.await(
                    DIFFERENT_PAYMENT_COMPLETION_TIMEOUT
                        .toMillis(),
                    TimeUnit.MILLISECONDS
                );

            assertThat(secondCompletedWhileFirstLockHeld)
                .as(
                    "서로 다른 Payment 요청은 "
                        + "첫 번째 Payment 락을 기다리면 안 됩니다."
                )
                .isTrue();

            secondFuture.get(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            releaseFirstTransaction.countDown();

            firstFuture.get(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );

            // then
            Payment savedFirstPayment =
                paymentRepository
                    .findById(firstTestData.paymentId())
                    .orElseThrow();

            Payment savedSecondPayment =
                paymentRepository
                    .findById(secondTestData.paymentId())
                    .orElseThrow();

            Reservation savedFirstReservation =
                reservationRepository
                    .findById(firstTestData.reservationId())
                    .orElseThrow();

            Reservation savedSecondReservation =
                reservationRepository
                    .findById(secondTestData.reservationId())
                    .orElseThrow();

            assertThat(savedFirstPayment.getStatus())
                .isEqualTo(PaymentStatus.PAID);

            assertThat(savedSecondPayment.getStatus())
                .isEqualTo(PaymentStatus.PAID);

            assertThat(savedFirstPayment.getApprovedAt())
                .isNotNull();

            assertThat(savedSecondPayment.getApprovedAt())
                .isNotNull();

            assertThat(savedFirstPayment.getFailedAt())
                .isNull();

            assertThat(savedSecondPayment.getFailedAt())
                .isNull();

            assertThat(savedFirstReservation.getStatus())
                .isEqualTo(
                    ReservationStatus.CONFIRMED
                );

            assertThat(savedSecondReservation.getStatus())
                .isEqualTo(
                    ReservationStatus.CONFIRMED
                );

            assertThat(savedFirstReservation.getCanceledAt())
                .isNull();

            assertThat(savedSecondReservation.getCanceledAt())
                .isNull();
        } finally {
            releaseFirstTransaction.countDown();

            executorService.shutdownNow();

            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    private TransactionTemplate createNewTransactionTemplate() {
        TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        transactionTemplate.setPropagationBehavior(
            TransactionDefinition
                .PROPAGATION_REQUIRES_NEW
        );

        transactionTemplate.setTimeout(
            Math.toIntExact(
                ASYNC_TIMEOUT.toSeconds()
            )
        );

        return transactionTemplate;
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
                "동시성 테스트 대기 중 스레드가 중단됐습니다.",
                exception
            );
        }
    }

    private static long elapsedMillis(
        long startedAtNanos
    ) {
        return TimeUnit.NANOSECONDS
            .toMillis(
                System.nanoTime()
                    - startedAtNanos
            );
    }

    private TestData createTestData(
        LocalDateTime expiresAt
    ) {
        Member member =
            createMember(
                "default"
            );

        return createTestData(
            member,
            expiresAt,
            1
        );
    }

    private Member createMember(
        String identifier
    ) {
        return memberRepository.saveAndFlush(
            Member.create(
                "payment-lock-"
                    + identifier
                    + "@roompick.com",
                "encoded-password",
                "결제 락 테스트 회원 "
                    + identifier
            )
        );
    }

    private TestData createTestData(
        Member member,
        LocalDateTime expiresAt,
        int sequence
    ) {
        Accommodation accommodation =
            accommodationRepository
                .saveAndFlush(
                    Accommodation.create(
                        "룸픽 락 테스트 호텔 "
                            + sequence,
                        "서울특별시 테스트구 락로 "
                            + sequence,
                        "결제 동시성 통합 테스트용 숙소 "
                            + sequence,
                        LocalTime.of(15, 0),
                        LocalTime.of(11, 0)
                    )
                );

        Room room =
            roomRepository.saveAndFlush(
                Room.create(
                    accommodation,
                    "10" + sequence,
                    "락 테스트 객실 "
                        + sequence,
                    "결제 동시성 통합 테스트용 객실 "
                        + sequence,
                    100_000L,
                    2,
                    2
                )
            );

        LocalDate checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(sequence);

        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        Reservation reservation =
            reservationRepository
                .saveAndFlush(
                    Reservation.create(
                        member,
                        room,
                        checkInDate,
                        checkOutDate,
                        2,
                        expiresAt
                    )
                );

        Payment payment =
            paymentRepository.saveAndFlush(
                Payment.create(
                    reservation
                )
            );

        return new TestData(
            member.getId(),
            reservation.getId(),
            payment.getId(),
            payment.getAmount()
        );
    }

    private record TestData(
        Long memberId,
        Long reservationId,
        Long paymentId,
        long amount
    ) {
    }

    private record SecondAttemptResult(
        boolean success,
        ErrorCode errorCode,
        long elapsedMillis
    ) {

        private static SecondAttemptResult unexpectedSuccess(
            long elapsedMillis
        ) {
            return new SecondAttemptResult(
                true,
                null,
                elapsedMillis
            );
        }

        private static SecondAttemptResult failure(
            ErrorCode errorCode,
            long elapsedMillis
        ) {
            return new SecondAttemptResult(
                false,
                errorCode,
                elapsedMillis
            );
        }
    }
}
