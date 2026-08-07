package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.exception.PopularAccommodationSingleFlightInterruptedException;
import com.roompick.domain.accommodation.exception.PopularAccommodationSingleFlightTimeoutException;
import com.roompick.domain.accommodation.support.PopularAccommodationCacheCondition;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.global.config.cache.PopularAccommodationSingleFlightProperties;

class PopularAccommodationSingleFlightServiceTest {

    private static final int REQUEST_COUNT = 10;

    private static final long TEST_TIMEOUT_SECONDS = 3L;

    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void tearDown() throws InterruptedException {
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
            executor.awaitTermination(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        }
    }

    @Test
    void 동일_Key_동시_요청은_최초_작업과_결과를_공유하고_완료_후_새로_실행한다()
        throws Exception {

        PopularAccommodationSingleFlightService service =
            createService(
                true,
                Duration.ofSeconds(2)
            );
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch releaseOperation = new CountDownLatch(1);
        List<PopularAccommodationResponseDto> expected = result(1L);

        Supplier<List<PopularAccommodationResponseDto>> operation = () -> {
            executions.incrementAndGet();
            operationEntered.countDown();
            await(releaseOperation);
            return expected;
        };

        ExecutorService executor = newExecutor(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<PopularAccommodationResponseDto>>> futures =
            submitConcurrentRequests(
                executor,
                ready,
                start,
                () -> service.execute(
                    PopularAccommodationPeriod.DAILY,
                    10,
                    operation
                )
            );

        assertThat(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .isTrue();
        start.countDown();
        assertThat(
            operationEntered.await(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        ).isTrue();
        awaitWaitingRequestCount(
            service,
            REQUEST_COUNT - 1
        );
        releaseOperation.countDown();

        for (Future<List<PopularAccommodationResponseDto>> future : futures) {
            assertThat(future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isSameAs(expected);
        }
        assertThat(executions).hasValue(1);

        List<PopularAccommodationResponseDto> next = service.execute(
            PopularAccommodationPeriod.DAILY,
            10,
            () -> {
                executions.incrementAndGet();
                return result(2L);
            }
        );

        assertThat(executions).hasValue(2);
        assertThat(next.get(0).accommodationId()).isEqualTo(2L);
    }

    @Test
    void 서로_다른_period와_limit은_독립적으로_실행한다()
        throws Exception {

        PopularAccommodationSingleFlightService service =
            createService(true, Duration.ofSeconds(2));
        CountDownLatch operationsEntered = new CountDownLatch(3);
        CountDownLatch releaseOperations = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = newExecutor(3);

        Supplier<List<PopularAccommodationResponseDto>> operation = () -> {
            executions.incrementAndGet();
            operationsEntered.countDown();
            await(releaseOperations);
            return result(executions.get());
        };

        Future<?> daily10 = executor.submit(() -> service.execute(
            PopularAccommodationPeriod.DAILY,
            10,
            operation
        ));
        Future<?> weekly10 = executor.submit(() -> service.execute(
            PopularAccommodationPeriod.WEEKLY,
            10,
            operation
        ));
        Future<?> daily20 = executor.submit(() -> service.execute(
            PopularAccommodationPeriod.DAILY,
            20,
            operation
        ));

        assertThat(
            operationsEntered.await(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        ).isTrue();
        assertThat(executions).hasValue(3);
        releaseOperations.countDown();
        daily10.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        weekly10.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        daily20.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void 최초_작업_실패를_모두_공유하고_Map_정리_후_새_작업을_실행한다()
        throws Exception {

        PopularAccommodationSingleFlightService service =
            createService(true, Duration.ofSeconds(2));
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch operationEntered = new CountDownLatch(1);
        CountDownLatch releaseOperation = new CountDownLatch(1);
        IllegalArgumentException original =
            new IllegalArgumentException("원본 조회 실패");
        ExecutorService executor = newExecutor(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<List<PopularAccommodationResponseDto>>> futures =
            submitConcurrentRequests(
                executor,
                ready,
                start,
                () -> service.execute(
                    PopularAccommodationPeriod.DAILY,
                    10,
                    () -> {
                        executions.incrementAndGet();
                        operationEntered.countDown();
                        await(releaseOperation);
                        throw original;
                    }
                )
            );

        assertThat(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .isTrue();
        start.countDown();
        assertThat(
            operationEntered.await(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        ).isTrue();
        awaitWaitingRequestCount(
            service,
            REQUEST_COUNT - 1
        );
        releaseOperation.countDown();

        for (Future<List<PopularAccommodationResponseDto>> future : futures) {
            assertThatThrownBy(
                () -> future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            ).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        }
        assertThat(executions).hasValue(1);

        assertThat(
            service.execute(
                PopularAccommodationPeriod.DAILY,
                10,
                () -> result(2L)
            )
        ).extracting(PopularAccommodationResponseDto::accommodationId)
            .containsExactly(2L);
    }

    @Test
    void 캐시_비활성화_상태에서는_동일_Key도_각각_실행한다()
        throws Exception {

        PopularAccommodationSingleFlightService service =
            createService(false, Duration.ofSeconds(2));
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = newExecutor(REQUEST_COUNT);
        CountDownLatch ready = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<List<PopularAccommodationResponseDto>>> futures =
            submitConcurrentRequests(
                executor,
                ready,
                start,
                () -> service.execute(
                    PopularAccommodationPeriod.DAILY,
                    10,
                    () -> {
                        executions.incrementAndGet();
                        return result(1L);
                    }
                )
            );

        assertThat(ready.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        assertThat(executions).hasValue(REQUEST_COUNT);
    }

    @Test
    void 대기_요청_timeout은_Map을_제거하지_않고_소유_작업_완료_후_정리한다()
        throws Exception {

        PopularAccommodationSingleFlightService service =
            createService(true, Duration.ofMillis(100));
        CountDownLatch ownerEntered = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        ExecutorService executor = newExecutor(2);

        Future<List<PopularAccommodationResponseDto>> owner =
            executor.submit(() -> service.execute(
                PopularAccommodationPeriod.DAILY,
                10,
                () -> {
                    executions.incrementAndGet();
                    ownerEntered.countDown();
                    await(releaseOwner);
                    return result(1L);
                }
            ));
        assertThat(ownerEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .isTrue();

        Future<?> firstWaiter = executor.submit(() -> service.execute(
            PopularAccommodationPeriod.DAILY,
            10,
            () -> result(99L)
        ));
        assertTimeout(firstWaiter);

        Future<?> secondWaiter = executor.submit(() -> service.execute(
            PopularAccommodationPeriod.DAILY,
            10,
            () -> result(98L)
        ));
        assertTimeout(secondWaiter);
        assertThat(executions).hasValue(1);

        releaseOwner.countDown();
        owner.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(service.execute(
            PopularAccommodationPeriod.DAILY,
            10,
            () -> result(2L)
        )).extracting(PopularAccommodationResponseDto::accommodationId)
            .containsExactly(2L);
    }

    @Test
    void 대기_중_interrupt가_발생하면_interrupt_상태를_복구한다()
        throws Exception {

        PopularAccommodationSingleFlightService service =
            createService(true, Duration.ofSeconds(2));
        CountDownLatch ownerEntered = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        ExecutorService ownerExecutor = newExecutor(1);

        Future<?> owner = ownerExecutor.submit(() -> service.execute(
            PopularAccommodationPeriod.DAILY,
            10,
            () -> {
                ownerEntered.countDown();
                await(releaseOwner);
                return result(1L);
            }
        ));
        assertThat(ownerEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            .isTrue();

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        Thread waiter = new Thread(() -> {
            try {
                service.execute(
                    PopularAccommodationPeriod.DAILY,
                    10,
                    () -> result(99L)
                );
            } catch (Throwable exception) {
                thrown.set(exception);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        awaitThreadWaiting(waiter);
        waiter.interrupt();
        waiter.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));

        assertThat(thrown.get())
            .isInstanceOf(
                PopularAccommodationSingleFlightInterruptedException.class
            );
        assertThat(interrupted.get()).isTrue();

        releaseOwner.countDown();
        owner.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private PopularAccommodationSingleFlightService createService(
        boolean cacheEnabled,
        Duration waitTimeout
    ) {
        PopularAccommodationKeyGenerator keyGenerator =
            mock(PopularAccommodationKeyGenerator.class);
        PopularAccommodationCacheCondition cacheCondition =
            mock(PopularAccommodationCacheCondition.class);

        given(cacheCondition.isEnabled()).willReturn(cacheEnabled);
        given(keyGenerator.generateCurrentKey(
            PopularAccommodationPeriod.DAILY
        )).willReturn("daily");
        given(keyGenerator.generateCurrentKey(
            PopularAccommodationPeriod.WEEKLY
        )).willReturn("weekly");

        return new PopularAccommodationSingleFlightService(
            keyGenerator,
            cacheCondition,
            new PopularAccommodationSingleFlightProperties(waitTimeout)
        );
    }

    private List<Future<List<PopularAccommodationResponseDto>>>
    submitConcurrentRequests(
        ExecutorService executor,
        CountDownLatch ready,
        CountDownLatch start,
        Supplier<List<PopularAccommodationResponseDto>> request
    ) {
        List<Future<List<PopularAccommodationResponseDto>>> futures =
            new ArrayList<>();

        for (int index = 0; index < REQUEST_COUNT; index++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                return request.get();
            }));
        }

        return futures;
    }

    private ExecutorService newExecutor(int threadCount) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        executors.add(executor);
        return executor;
    }

    private void awaitWaitingRequestCount(
        PopularAccommodationSingleFlightService service,
        int expectedCount
    ) {
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);

        while (service.getWaitingRequestCount() < expectedCount) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                    "대기 요청이 제한 시간 안에 Single Flight에 진입하지 않았습니다."
                );
            }
            Thread.onSpinWait();
        }
    }

    private void awaitThreadWaiting(Thread thread) {
        long deadline = System.nanoTime()
            + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);

        while (
            thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.TIMED_WAITING
        ) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("대기 스레드가 제한 시간 안에 대기하지 않았습니다.");
            }
            Thread.onSpinWait();
        }
    }

    private void assertTimeout(Future<?> future) {
        assertThatThrownBy(
            () -> future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        ).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(
                PopularAccommodationSingleFlightTimeoutException.class
            );
    }

    private static void await(CountDownLatch latch) {
        try {
            boolean completed = latch.await(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
            if (!completed) {
                throw new AssertionError("테스트 동기화 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("테스트 동기화 대기가 중단되었습니다.", exception);
        }
    }

    private static List<PopularAccommodationResponseDto> result(long id) {
        return List.of(
            PopularAccommodationResponseDto.from(
                1,
                new AccommodationListResponseDto(
                    id,
                    "숙소 " + id,
                    "서울"
                )
            )
        );
    }
}
