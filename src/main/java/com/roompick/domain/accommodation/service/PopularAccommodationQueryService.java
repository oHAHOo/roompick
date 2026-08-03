package com.roompick.domain.accommodation.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;

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

    private final PopularAccommodationService
        popularAccommodationService;

    private final AccommodationService accommodationService;

    /**
     * 오늘 날짜의 인기 숙소 목록을 조회합니다.
     *
     * 캐시 키에는 일간 랭킹 날짜와 요청 limit을 모두 포함합니다.
     * Redis 랭킹 조회에 실패해 예외가 발생하면 결과가 캐시에 저장되지 않으며,
     * DB fallback은 Facade에서 별도로 처리합니다.
     */
    @Cacheable(
        cacheNames = "popularAccommodations",
        key = "@popularAccommodationKeyGenerator.generateTodayKey()"
            + " + ':' + #root.args[0]"
    )
    public List<PopularAccommodationResponseDto>
    getPopularAccommodations(
        int limit
    ) {
        List<Long> rankedAccommodationIds =
            popularAccommodationService.findRankedAccommodationIds(
                limit
            );

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

        List<PopularAccommodationResponseDto> result =
            new ArrayList<>();

        for (Long accommodationId : rankedAccommodationIds) {
            AccommodationListResponseDto accommodation =
                accommodationById.get(
                    accommodationId
                );

            if (accommodation == null) {
                continue;
            }

            int rank = result.size() + 1;

            result.add(
                PopularAccommodationResponseDto.from(
                    rank,
                    accommodation
                )
            );

            if (result.size() == limit) {
                break;
            }
        }

        return result;
    }
}
