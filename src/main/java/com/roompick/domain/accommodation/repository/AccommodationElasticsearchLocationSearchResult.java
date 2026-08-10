package com.roompick.domain.accommodation.repository;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;

/**
 * Elasticsearch 위치 기반 숙소 검색 결과입니다.
 *
 * 검색된 Document와 검색 중심 좌표로부터의 거리를
 * 함께 전달합니다.
 */
public record AccommodationElasticsearchLocationSearchResult(
    AccommodationSearchDocument document,
    double distanceKm
) {
}
