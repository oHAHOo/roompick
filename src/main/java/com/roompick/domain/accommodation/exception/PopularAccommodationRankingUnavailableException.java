package com.roompick.domain.accommodation.exception;

/**
 * Redis 인기 숙소 랭킹을 사용할 수 없는 상황을 나타내는 예외입니다.
 *
 * 숙소 DB 조회 실패와 구분하여 Redis 랭킹 장애일 때만
 * 최신 ACTIVE 숙소 fallback을 실행하는 데 사용합니다.
 */
public class PopularAccommodationRankingUnavailableException
    extends RuntimeException {

    public PopularAccommodationRankingUnavailableException(
        Throwable cause
    ) {
        super(cause);
    }
}
