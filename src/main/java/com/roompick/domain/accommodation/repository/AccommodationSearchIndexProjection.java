package com.roompick.domain.accommodation.repository;

import java.math.BigDecimal;

import com.roompick.domain.accommodation.entity.AccommodationStatus;

/**
 * Elasticsearch 숙소 검색 인덱스 재생성에 필요한
 * MySQL 숙소 데이터만 조회하는 Projection입니다.
 *
 * Accommodation Entity 전체를 로딩하지 않고
 * 검색 Document 생성에 필요한 필드만 조회합니다.
 */
public interface AccommodationSearchIndexProjection {

    Long getAccommodationId();

    String getName();

    String getAddress();

    AccommodationStatus getStatus();

    BigDecimal getLatitude();

    BigDecimal getLongitude();
}
