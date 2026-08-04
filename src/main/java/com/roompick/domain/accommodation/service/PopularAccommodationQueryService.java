package com.roompick.domain.accommodation.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

import lombok.RequiredArgsConstructor;

/**
 * 인기 숙소 조회 결과를 조합하고 캐싱하는 Service입니다.
 *
 * Redis 랭킹에 포함된 숙소 ID를 조회한 뒤,
 * 운영 중인 숙소의 공개 정보만 DB에서 한 번에 조회합니다.
 *
 * 캐시에 데이터가 존재하면 이 메서드 내부 로직은 실행되지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class PopularAccommodationQueryService {

    private static final int RANKING_BATCH_MULTIPLIER = 5;

    private final PopularAccommodationRankingService
        popularAccommodationRankingService;

    private final AccommodationService accommodationService;

    /**
     * 요청 기간의 인기 숙소 목록을 조회합니다.
     *
     * 캐시 키에는 조회 기간, 기간별 기준 날짜와 요청 limit을 모두 포함합니다.
     * Redis 랭킹 조회에 실패해 예외가 발생하면 결과가 캐시에 저장되지 않으며,
     * DB fallback은 Facade에서 별도로 처리합니다.
     */
    @Cacheable(
        cacheNames = "popularAccommodations",
        key = "@popularAccommodationKeyGenerator.generateCurrentKey("
            + "#root.args[0]) + ':' + #root.args[1]",
        condition = "@popularAccommodationCacheCondition.isEnabled()"
    )
    public List<PopularAccommodationResponseDto>
    getPopularAccommodations(
        PopularAccommodationPeriod period,
        int limit
    ) {
        List<PopularAccommodationResponseDto> result =
            new ArrayList<>();

        Set<Long> processedAccommodationIds =
            new HashSet<>();

        long batchSize = (long) limit * RANKING_BATCH_MULTIPLIER;
        long start = 0L;

        while (result.size() < limit) {
            long end = start + batchSize - 1L;

            List<Long> rankedAccommodationIds =
                popularAccommodationRankingService
                    .findRankedAccommodationIds(
                        period,
                        limit,
                        start,
                        end
                    );

            if (rankedAccommodationIds.isEmpty()) {
                break;
            }

            List<Long> unprocessedAccommodationIds =
                rankedAccommodationIds.stream()
                    .filter(processedAccommodationIds::add)
                    .toList();

            appendActiveAccommodations(
                unprocessedAccommodationIds,
                result,
                limit
            );

            if (
                result.size() == limit
                    || rankedAccommodationIds.size() < batchSize
            ) {
                break;
            }

            start += batchSize;
        }

        return result;
    }

    private void appendActiveAccommodations(
        List<Long> rankedAccommodationIds,
        List<PopularAccommodationResponseDto> result,
        int limit
    ) {
        if (rankedAccommodationIds.isEmpty()) {
            return;
        }

        List<AccommodationListResponseDto> activeAccommodations =
            accommodationService.findAllActiveSummaryByIds(
                rankedAccommodationIds
            );

        Map<Long, AccommodationListResponseDto> accommodationById =
            new HashMap<>();

        for (
            AccommodationListResponseDto accommodation
            : activeAccommodations
        ) {
            accommodationById.put(
                accommodation.accommodationId(),
                accommodation
            );
        }

        for (Long accommodationId : rankedAccommodationIds) {
            AccommodationListResponseDto accommodation =
                accommodationById.get(accommodationId);

            if (accommodation == null) {
                continue;
            }

            result.add(
                PopularAccommodationResponseDto.from(
                    result.size() + 1,
                    accommodation
                )
            );

            if (result.size() == limit) {
                return;
            }
        }
    }
}
