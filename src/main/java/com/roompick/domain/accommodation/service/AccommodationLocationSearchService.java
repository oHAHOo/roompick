package com.roompick.domain.accommodation.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.repository.AccommodationLocationSearchProjection;
import com.roompick.domain.accommodation.repository.AccommodationLocationSearchRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * MySQL을 사용한 위치 기반 숙소 검색을 담당하는 Service입니다.
 *
 * Elasticsearch 도입 전 기준 검색 경로로 사용하며,
 * 검색 조건 검증과 Repository 결과의 응답 DTO 변환을 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class AccommodationLocationSearchService {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    /**
     * 위치 검색이 지나치게 넓은 범위의 DB 연산으로 이어지는 것을 막기 위한
     * 초기 검색 반경 상한입니다.
     */
    private static final double MAX_RADIUS_KM = 100.0;

    /**
     * 한 요청에서 반환할 수 있는 숙소 수의 최대값입니다.
     */
    private static final int MAX_LIMIT = 100;

    private final AccommodationLocationSearchRepository
        accommodationLocationSearchRepository;

    /**
     * 사용자 위치를 기준으로 반경 안의 ACTIVE 숙소를 검색합니다.
     *
     * keyword가 null이거나 공백인 경우 위치 조건만 사용하고,
     * 값이 존재하면 숙소명 또는 주소 조건을 함께 적용합니다.
     *
     * 거리 계산과 정렬은 MySQL에서 수행하며,
     * Entity 전체를 조회하지 않고 Projection 결과만 응답 DTO로 변환합니다.
     */
    @Transactional(readOnly = true)
    public List<AccommodationLocationSearchResponseDto> searchNearby(
        String keyword,
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        validateSearchCondition(
            latitude,
            longitude,
            radiusKm,
            limit
        );

        String normalizedKeyword =
            normalizeKeyword(keyword);

        List<AccommodationLocationSearchProjection> results =
            accommodationLocationSearchRepository.searchNearby(
                normalizedKeyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        return results.stream()
            .map(this::toResponseDto)
            .toList();
    }

    /**
     * Native Query 결과 Projection을 API 응답 DTO로 변환합니다.
     *
     * MySQL은 거리를 미터 단위로 반환하므로
     * 응답에서는 킬로미터 단위로 변환합니다.
     */
    private AccommodationLocationSearchResponseDto toResponseDto(
        AccommodationLocationSearchProjection projection
    ) {
        return new AccommodationLocationSearchResponseDto(
            projection.getAccommodationId(),
            projection.getName(),
            projection.getAddress(),
            projection.getLatitude(),
            projection.getLongitude(),
            projection.getDistanceMeters() / 1000.0
        );
    }

    /**
     * 검색어 앞뒤 공백을 제거합니다.
     *
     * null 또는 공백 문자열은 검색어가 없는 것으로 처리하여
     * Repository에는 null을 전달합니다.
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    /**
     * 위치 검색 요청값이 허용 범위인지 검증합니다.
     *
     * NaN과 무한대 값도 사전에 차단하여
     * 잘못된 값이 거리 계산 쿼리까지 전달되지 않도록 합니다.
     */
    private void validateSearchCondition(
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        if (
            !Double.isFinite(latitude)
                || latitude < MIN_LATITUDE
                || latitude > MAX_LATITUDE
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_LATITUDE_OUT_OF_RANGE
            );
        }

        if (
            !Double.isFinite(longitude)
                || longitude < MIN_LONGITUDE
                || longitude > MAX_LONGITUDE
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_LONGITUDE_OUT_OF_RANGE
            );
        }

        if (
            !Double.isFinite(radiusKm)
                || radiusKm <= 0
                || radiusKm > MAX_RADIUS_KM
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }

        if (
            limit < 1
                || limit > MAX_LIMIT
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}
