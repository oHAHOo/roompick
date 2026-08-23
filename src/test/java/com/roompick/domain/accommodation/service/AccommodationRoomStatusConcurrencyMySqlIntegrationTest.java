package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.domain.room.service.RoomService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.testsupport.SharedMySqlTestContainer;

/**
 * 실제 MySQL 환경에서 숙소 비공개 전환과 소속 객실 공개 전환이
 * 동시에 실행돼도 최종 상태가 일관되게 유지되는지 검증합니다.
 *
 * 두 작업 모두 같은 숙소 행에 비관적 쓰기 락을 먼저 획득한 뒤에만
 * 상태를 바꾸므로, 어떤 순서로 실행되더라도 숙소가 INACTIVE로
 * 확정되면 소속 객실도 반드시 INACTIVE로 끝나야 한다. 락이 없다면
 * 객실 공개 트랜잭션이 낡은 "숙소는 ACTIVE" 값을 근거로 객실을
 * ACTIVE로 커밋해버려 숙소는 INACTIVE인데 객실만 ACTIVE로 남는
 * 불일치가 발생할 수 있다.
 *
 * 각 요청이 서로 다른 Connection과 트랜잭션을 사용해야 하므로
 * 테스트 클래스에는 @Transactional을 적용하지 않는다.
 */
@Tag("integration")
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=5"
    }
)
@ActiveProfiles("test")
class AccommodationRoomStatusConcurrencyMySqlIntegrationTest {

    private static final String DATABASE_NAME =
        "roompick_accommodation_room_status_lock_test";

    private static final Duration ASYNC_TIMEOUT =
        Duration.ofSeconds(15);

    private static final long LOCK_HOLD_MILLIS = 500L;

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
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private AccommodationService accommodationService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "객실 공개 요청은 숙소 비공개 트랜잭션이 끝날 때까지 대기한 뒤 "
            + "숙소가 이미 INACTIVE임을 확인하고 거절된다"
    )
    void 객실_공개_요청은_숙소_비공개_트랜잭션이_끝날_때까지_대기한다()
        throws Exception {

        // given: 운영 중인 숙소에 비공개 객실이 하나 있습니다.
        Accommodation accommodation =
            accommodationRepository.saveAndFlush(createAccommodation());
        Long accommodationId = accommodation.getId();

        Room room = roomRepository.saveAndFlush(createRoom(accommodation));
        Long roomId = room.getId();

        CountDownLatch inactivateHoldingLock = new CountDownLatch(1);
        CountDownLatch releaseInactivate = new CountDownLatch(1);

        ExecutorService executorService =
            Executors.newFixedThreadPool(2);

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(transactionManager);

        try {
            // when: 숙소 비공개 트랜잭션이 먼저 락을 잡고 유지합니다.
            Future<?> inactivateFuture =
                executorService.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        accommodationService.inactivateAccommodation(
                            accommodationId
                        );

                        inactivateHoldingLock.countDown();

                        awaitLatch(
                            releaseInactivate,
                            ASYNC_TIMEOUT,
                            "숙소 비공개 트랜잭션 커밋 신호를 기다리는 중 시간이 초과됐습니다."
                        );
                    })
                );

            awaitLatch(
                inactivateHoldingLock,
                ASYNC_TIMEOUT,
                "숙소 비공개 트랜잭션이 숙소 행 락을 유지해야 합니다."
            );

            Future<TimedActivationResult> activateFuture =
                executorService.submit(() -> {
                    long startedAt = System.nanoTime();

                    ActivationResult result = attemptActivateRoom(
                        accommodationId,
                        roomId
                    );

                    Duration elapsed = Duration.ofNanos(
                        System.nanoTime() - startedAt
                    );

                    return new TimedActivationResult(result, elapsed);
                });

            /*
             * 객실 공개 요청이 락 대기 없이 바로 통과하지 않도록,
             * 숙소 비공개 트랜잭션을 일정 시간 붙잡아 둔 뒤에만 커밋합니다.
             */
            Thread.sleep(LOCK_HOLD_MILLIS);
            releaseInactivate.countDown();

            inactivateFuture.get(
                ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS
            );

            TimedActivationResult timedResult = activateFuture.get(
                ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS
            );

            // then: 객실 공개 요청은 숙소 비공개가 끝날 때까지 대기했다가 거절됩니다.
            assertThat(timedResult.result().success()).isFalse();
            assertThat(timedResult.result().errorCode())
                .isEqualTo(ErrorCode.ACCOMMODATION_INACTIVE);
            assertThat(timedResult.elapsed().toMillis())
                .isGreaterThanOrEqualTo(LOCK_HOLD_MILLIS);

            assertFinalStateIsConsistent(accommodationId, roomId);
        } finally {
            releaseInactivate.countDown();
            executorService.shutdownNow();
            executorService.awaitTermination(
                ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS
            );
        }
    }

    @Test
    @DisplayName(
        "동시에 숙소 비공개와 객실 공개를 요청해도 "
            + "숙소가 INACTIVE라면 객실도 반드시 INACTIVE로 끝난다"
    )
    void 동시_요청에서도_숙소가_INACTIVE면_객실도_반드시_INACTIVE다()
        throws Exception {

        int iterationCount = 5;

        for (int i = 0; i < iterationCount; i++) {
            // given: 매 반복마다 새로운 숙소·객실을 만들어 이전 결과와 섞이지 않게 합니다.
            Accommodation accommodation =
                accommodationRepository.saveAndFlush(createAccommodation());
            Long accommodationId = accommodation.getId();

            Room room =
                roomRepository.saveAndFlush(createRoom(accommodation));
            Long roomId = room.getId();

            CountDownLatch requestsReady = new CountDownLatch(2);
            CountDownLatch startRequests = new CountDownLatch(1);

            ExecutorService executorService =
                Executors.newFixedThreadPool(2);

            try {
                Future<?> inactivateFuture = executorService.submit(() -> {
                    requestsReady.countDown();
                    awaitLatch(
                        startRequests,
                        ASYNC_TIMEOUT,
                        "숙소 비공개 시작 신호를 기다리는 중 시간이 초과됐습니다."
                    );
                    accommodationService.inactivateAccommodation(
                        accommodationId
                    );
                    return null;
                });

                Future<ActivationResult> activateFuture =
                    executorService.submit(() -> {
                        requestsReady.countDown();
                        awaitLatch(
                            startRequests,
                            ASYNC_TIMEOUT,
                            "객실 공개 시작 신호를 기다리는 중 시간이 초과됐습니다."
                        );
                        return attemptActivateRoom(accommodationId, roomId);
                    });

                awaitLatch(
                    requestsReady,
                    ASYNC_TIMEOUT,
                    "두 요청이 실행 준비를 완료해야 합니다."
                );
                startRequests.countDown();

                inactivateFuture.get(
                    ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS
                );
                activateFuture.get(
                    ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS
                );

                // then
                assertFinalStateIsConsistent(accommodationId, roomId);
            } finally {
                executorService.shutdownNow();
                executorService.awaitTermination(
                    ASYNC_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS
                );
            }
        }
    }

    /**
     * 숙소가 INACTIVE로 끝났다면 소속 객실도 반드시 INACTIVE여야 한다는
     * 핵심 불변조건을 실제 DB에서 다시 조회해 확인합니다.
     */
    private void assertFinalStateIsConsistent(
        Long accommodationId,
        Long roomId
    ) {
        Accommodation persistedAccommodation =
            accommodationRepository.findById(accommodationId)
                .orElseThrow();
        Room persistedRoom =
            roomRepository.findById(roomId).orElseThrow();

        assertThat(persistedAccommodation.getStatus())
            .isEqualTo(AccommodationStatus.INACTIVE);
        assertThat(persistedRoom.getStatus())
            .isEqualTo(RoomStatus.INACTIVE);
    }

    private ActivationResult attemptActivateRoom(
        Long accommodationId,
        Long roomId
    ) {
        try {
            roomService.activateRoom(accommodationId, roomId);
            return ActivationResult.succeeded();
        } catch (BusinessException exception) {
            return ActivationResult.failed(exception.getErrorCode());
        }
    }

    private Accommodation createAccommodation() {
        return Accommodation.create(
            "동시성 테스트 호텔",
            "서울특별시 테스트구 동시성로 1",
            "숙소·객실 상태 동시성 통합 테스트용 숙소",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );
    }

    private Room createRoom(Accommodation accommodation) {
        return Room.create(
            accommodation,
            "101",
            "동시성 테스트 객실",
            "숙소·객실 상태 동시성 통합 테스트용 객실",
            100_000L,
            2,
            2
        );
    }

    private static void awaitLatch(
        CountDownLatch latch,
        Duration timeout,
        String timeoutMessage
    ) {
        try {
            boolean completed = latch.await(
                timeout.toMillis(), TimeUnit.MILLISECONDS
            );

            if (!completed) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "숙소·객실 상태 동시성 테스트 대기 중 스레드가 중단됐습니다.",
                exception
            );
        }
    }

    private record ActivationResult(
        boolean success,
        ErrorCode errorCode
    ) {
        static ActivationResult succeeded() {
            return new ActivationResult(true, null);
        }

        static ActivationResult failed(ErrorCode errorCode) {
            return new ActivationResult(false, errorCode);
        }
    }

    private record TimedActivationResult(
        ActivationResult result,
        Duration elapsed
    ) {
    }
}
