package com.roompick.domain.accommodation.service;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.repository.PopularAccommodationRankingRepository;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;

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
public class PopularAccommodationService {

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
}
