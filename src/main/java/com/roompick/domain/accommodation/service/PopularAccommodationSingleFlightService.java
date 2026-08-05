package com.roompick.domain.accommodation.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.support.PopularAccommodationCacheCondition;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

import lombok.RequiredArgsConstructor;

/**
 * 동일한 인기 숙소 캐시 키의 동시 조회를 하나의 작업으로 묶는 Service입니다.
 *
 * 캐시가 만료된 직후 동일 요청이 동시에 들어오더라도
 * 최초 요청만 실제 인기 숙소 조회를 수행하고,
 * 나머지 요청은 최초 요청의 결과 또는 예외를 공유합니다.
 *
 * 이 Single Flight는 애플리케이션 인스턴스 내부에서만 동작합니다.
 */
@Service
@RequiredArgsConstructor
public class PopularAccommodationSingleFlightService {

    private final PopularAccommodationQueryService
        popularAccommodationQueryService;

    private final PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    private final PopularAccommodationCacheCondition
        popularAccommodationCacheCondition;

    /**
     * 현재 애플리케이션에서 진행 중인 인기 숙소 조회를
     * 캐시 키별로 관리합니다.
     */
    private final ConcurrentMap<
        String,
        CompletableFuture<List<PopularAccommodationResponseDto>>
        > inFlightRequests = new ConcurrentHashMap<>();

    /**
     * 요청 기간과 limit에 해당하는 인기 숙소 목록을 조회합니다.
     *
     * 캐시를 비활성화한 성능 측정에서는 Single Flight도 적용하지 않아
     * 모든 요청이 기존 조회 흐름을 그대로 실행하게 합니다.
     */
    public List<PopularAccommodationResponseDto>
    getPopularAccommodations(
        PopularAccommodationPeriod period,
        int limit
    ) {
        if (!popularAccommodationCacheCondition.isEnabled()) {
            return popularAccommodationQueryService
                .getPopularAccommodations(
                    period,
                    limit
                );
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
            return awaitResult(
                existingRequest
            );
        }

        return executeFirstRequest(
            cacheKey,
            newRequest,
            period,
            limit
        );
    }

    /**
     * 동일 키의 최초 요청만 실제 캐시 조회와 원본 조회를 수행합니다.
     *
     * 성공 결과뿐 아니라 RuntimeException과 Error도 대기 요청에 전달하며,
     * 작업 종료 후에는 반드시 진행 중 요청 Map에서 제거합니다.
     */
    private List<PopularAccommodationResponseDto>
    executeFirstRequest(
        String cacheKey,
        CompletableFuture<List<PopularAccommodationResponseDto>>
            currentRequest,
        PopularAccommodationPeriod period,
        int limit
    ) {
        try {
            List<PopularAccommodationResponseDto> result =
                popularAccommodationQueryService
                    .getPopularAccommodations(
                        period,
                        limit
                    );

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
            inFlightRequests.remove(
                cacheKey,
                currentRequest
            );
        }
    }

    /**
     * 이미 진행 중인 동일 키 요청의 완료를 기다립니다.
     *
     * 최초 요청에서 발생한 RuntimeException과 Error는
     * 종류를 변경하지 않고 호출자에게 다시 전달합니다.
     */
    private List<PopularAccommodationResponseDto>
    awaitResult(
        CompletableFuture<List<PopularAccommodationResponseDto>>
            existingRequest
    ) {
        try {
            return existingRequest.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();

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
    }

    /**
     * 응답 캐시와 동일한 구성으로 Single Flight 식별 키를 만듭니다.
     */
    private String createCacheKey(
        PopularAccommodationPeriod period,
        int limit
    ) {
        return popularAccommodationKeyGenerator.generateCurrentKey(
            period
        ) + ":" + limit;
    }
}
