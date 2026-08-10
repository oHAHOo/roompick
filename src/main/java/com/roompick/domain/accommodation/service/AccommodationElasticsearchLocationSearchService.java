package com.roompick.domain.accommodation.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;
import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.repository.AccommodationElasticsearchLocationSearchRepository;
import com.roompick.domain.accommodation.repository.AccommodationElasticsearchLocationSearchResult;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * Elasticsearch를 이용한 위치 기반 숙소 검색 Service입니다.
 *
 * 검색 요청값을 검증하고,
 * Elasticsearch Repository의 검색 결과를
 * API 응답 DTO로 변환합니다.
 */
@Service
@RequiredArgsConstructor
public class AccommodationElasticsearchLocationSearchService {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;

    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private static final double MAX_RADIUS_KM = 100.0;

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final AccommodationElasticsearchLocationSearchRepository
        accommodationElasticsearchLocationSearchRepository;

    /**
     * 검색 중심 좌표를 기준으로 주변 숙소를 조회합니다.
     *
     * 잘못된 요청은 Elasticsearch를 호출하기 전에 차단하여
     * 불필요한 검색 엔진 리소스 사용을 방지합니다.
     */
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

        List<AccommodationElasticsearchLocationSearchResult> searchResults =
            accommodationElasticsearchLocationSearchRepository.searchNearby(
                normalizedKeyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );

        return searchResults
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Elasticsearch 검색 결과를 API 응답 DTO로 변환합니다.
     */
    private AccommodationLocationSearchResponseDto toResponse(
        AccommodationElasticsearchLocationSearchResult searchResult
    ) {
        AccommodationSearchDocument document =
            searchResult.document();

        return new AccommodationLocationSearchResponseDto(
            document.getAccommodationId(),
            document.getName(),
            document.getAddress(),
            document.getLocation().getLat(),
            document.getLocation().getLon(),
            searchResult.distanceKm()
        );
    }

    /**
     * 검색 요청값을 검증합니다.
     *
     * MySQL 기준 검색과 동일한 범위를 사용하여
     * 이후 성능 비교에서도 검색 조건 차이가 발생하지 않게 합니다.
     */
    private void validateSearchCondition(
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        validateLatitude(latitude);
        validateLongitude(longitude);
        validateRadius(radiusKm);
        validateLimit(limit);
    }

    /**
     * 위도가 유효한 범위인지 확인합니다.
     */
    private void validateLatitude(
        double latitude
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
    }

    /**
     * 경도가 유효한 범위인지 확인합니다.
     */
    private void validateLongitude(
        double longitude
    ) {
        if (
            !Double.isFinite(longitude)
                || longitude < MIN_LONGITUDE
                || longitude > MAX_LONGITUDE
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_LONGITUDE_OUT_OF_RANGE
            );
        }
    }

    /**
     * 검색 반경이 허용 범위인지 확인합니다.
     */
    private void validateRadius(
        double radiusKm
    ) {
        if (
            !Double.isFinite(radiusKm)
                || radiusKm <= 0
                || radiusKm > MAX_RADIUS_KM
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    /**
     * 검색 결과 제한 개수가 허용 범위인지 확인합니다.
     */
    private void validateLimit(
        int limit
    ) {
        if (
            limit < MIN_LIMIT
                || limit > MAX_LIMIT
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    /**
     * 빈 검색어는 검색 조건에서 제외할 수 있도록 null로 정규화합니다.
     */
    private String normalizeKeyword(
        String keyword
    ) {
        if (
            keyword == null
                || keyword.isBlank()
        ) {
            return null;
        }

        return keyword.trim();
    }
}
