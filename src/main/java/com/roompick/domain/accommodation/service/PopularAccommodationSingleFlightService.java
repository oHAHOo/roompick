package com.roompick.domain.accommodation.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.exception.PopularAccommodationSingleFlightInterruptedException;
import com.roompick.domain.accommodation.exception.PopularAccommodationSingleFlightTimeoutException;
import com.roompick.domain.accommodation.support.PopularAccommodationCacheCondition;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.global.config.cache.PopularAccommodationSingleFlightProperties;

import lombok.RequiredArgsConstructor;

/**
 * 동일한 인기 숙소 캐시 키의 최종 조회 작업을 하나로 묶는 Service입니다.
 *
 * Facade가 전달한 정상 조회와 Redis 장애 fallback을 포함한 최종 작업을 공유하며,
 * 도메인 조회 방식이나 장애 정책은 알지 않습니다.
 * 이 Single Flight는 애플리케이션 인스턴스 내부에서만 동작합니다.
 */
@Service
@RequiredArgsConstructor
public class PopularAccommodationSingleFlightService {

    private final PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    private final PopularAccommodationCacheCondition
        popularAccommodationCacheCondition;

    private final PopularAccommodationSingleFlightProperties
        properties;

    private final ConcurrentMap<
        String,
        CompletableFuture<List<PopularAccommodationResponseDto>>
        > inFlightRequests = new ConcurrentHashMap<>();

    private final AtomicInteger waitingRequestCount =
        new AtomicInteger();

    /**
     * 요청 기간과 limit에 해당하는 최종 조회 작업을 동일 Key끼리 공유합니다.
     *
     * 캐시를 비활성화한 성능 측정에서는 Single Flight도 우회하여
     * 각 요청이 전달받은 작업을 독립적으로 실행합니다.
     */
    public List<PopularAccommodationResponseDto>
    execute(
        PopularAccommodationPeriod period,
        int limit,
        Supplier<List<PopularAccommodationResponseDto>> operation
    ) {
        if (!popularAccommodationCacheCondition.isEnabled()) {
            return operation.get();
        }

        String cacheKey = createCacheKey(
            period,
            limit
        );

        CompletableFuture<List<PopularAccommodationResponseDto>>
            newRequest = new CompletableFuture<>();

        CompletableFuture<List<PopularAccommodationResponseDto>>
            existingRequest = inFlightRequests.putIfAbsent(
            cacheKey,
            newRequest
        );

        if (existingRequest != null) {
            waitingRequestCount.incrementAndGet();

            try {
                return awaitResult(
                    existingRequest
                );
            } finally {
                waitingRequestCount.decrementAndGet();
            }
        }

        return executeFirstRequest(
            cacheKey,
            newRequest,
            operation
        );
    }

    private List<PopularAccommodationResponseDto>
    executeFirstRequest(
        String cacheKey,
        CompletableFuture<List<PopularAccommodationResponseDto>>
            currentRequest,
        Supplier<List<PopularAccommodationResponseDto>> operation
    ) {
        try {
            List<PopularAccommodationResponseDto> result =
                operation.get();

            currentRequest.complete(
                result
            );

            return result;
        } catch (RuntimeException | Error exception) {
            currentRequest.completeExceptionally(
                exception
            );

            throw exception;
        } finally {
            /*
             * 소유 작업만 자신이 등록한 Future를 제거합니다.
             * 대기 요청의 timeout 또는 interruption은 Map을 변경하지 않습니다.
             */
            inFlightRequests.remove(
                cacheKey,
                currentRequest
            );
        }
    }

    private List<PopularAccommodationResponseDto>
    awaitResult(
        CompletableFuture<List<PopularAccommodationResponseDto>>
            existingRequest
    ) {
        Duration waitTimeout = properties.waitTimeout();

        try {
            return existingRequest.get(
                waitTimeout.toNanos(),
                TimeUnit.NANOSECONDS
            );
        } catch (TimeoutException exception) {
            throw new PopularAccommodationSingleFlightTimeoutException(
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new PopularAccommodationSingleFlightInterruptedException(
                exception
            );
        } catch (ExecutionException exception) {
            throwOriginalCause(
                exception.getCause()
            );

            throw new IllegalStateException(
                "도달할 수 없는 Single Flight 예외 처리 경로입니다."
            );
        }
    }

    private void throwOriginalCause(
        Throwable cause
    ) {
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }

        if (cause instanceof Error error) {
            throw error;
        }

        throw new IllegalStateException(
            "인기 숙소 Single Flight 처리 중 예상하지 못한 예외가 발생했습니다.",
            cause
        );
    }

    private String createCacheKey(
        PopularAccommodationPeriod period,
        int limit
    ) {
        return popularAccommodationKeyGenerator.generateCurrentKey(
            period
        ) + ":" + limit;
    }

    /**
     * 동시성 테스트에서 대기 요청의 진입 시점을 결정적으로 확인합니다.
     */
    int getWaitingRequestCount() {
        return waitingRequestCount.get();
    }
}
