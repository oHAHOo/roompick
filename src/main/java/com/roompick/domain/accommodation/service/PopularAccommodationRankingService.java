package com.roompick.domain.accommodation.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.repository.PopularAccommodationRankingRepository;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 숙소의 인기 점수 집계 흐름을 담당하는 Service입니다.
 *
 * Redis 장애가 발생하더라도 숙소 상세 조회는 정상적으로 응답할 수 있도록
 * 인기 점수 기록 실패를 로그로 남기고 예외를 외부로 전달하지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopularAccommodationRankingService {

    /**
     * 인기 숙소 조회 개수의 허용 범위입니다.
     */
    private static final int MIN_POPULAR_LIMIT = 1;
    private static final int MAX_POPULAR_LIMIT = 20;

    private final PopularAccommodationRankingRepository
        popularAccommodationRankingRepository;

    private final PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    /**
     * 오늘 날짜의 인기 숙소 랭킹에서 해당 숙소의 조회 점수를 증가시킵니다.
     */
    public void recordView(
        Long accommodationId
    ) {
        String key =
            popularAccommodationKeyGenerator.generateTodayKey();

        try {
            popularAccommodationRankingRepository.incrementScore(
                key,
                accommodationId
            );
        } catch (DataAccessException exception) {
            log.warn(
                "인기 숙소 조회 점수 기록에 실패했습니다. accommodationId={}",
                accommodationId,
                exception
            );
        }
    }

    /**
     * 오늘 날짜의 인기 숙소 ID를 지정한 Redis 범위에서 조회합니다.
     *
     * limit은 API 허용 범위를 검증하는 데 사용하고,
     * 범위 반복과 ACTIVE 숙소 조합은 QueryService가 담당합니다.
     */
    public List<Long> findRankedAccommodationIds(
        int limit,
        long start,
        long end
    ) {
        validateLimit(
            limit
        );

        String key =
            popularAccommodationKeyGenerator.generateTodayKey();

        return popularAccommodationRankingRepository
            .findRankedAccommodationIds(
                key,
                start,
                end
            );
    }

    /**
     * 인기 숙소 조회 개수가 허용 범위인지 검증합니다.
     */
    private void validateLimit(
        int limit
    ) {
        if (
            limit < MIN_POPULAR_LIMIT
                || limit > MAX_POPULAR_LIMIT
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}
