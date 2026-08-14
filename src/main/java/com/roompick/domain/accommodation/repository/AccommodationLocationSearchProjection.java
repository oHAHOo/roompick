package com.roompick.domain.accommodation.repository;

/**
 * MySQL 위치 기반 숙소 검색 결과를 전달하는 Projection입니다.
 *
 * Native Query에서 검색에 필요한 필드만 조회하여
 * Accommodation Entity 전체 로딩을 피합니다.
 */
public interface AccommodationLocationSearchProjection {

    Long getAccommodationId();

    String getName();

    String getAddress();

    Double getLatitude();

    Double getLongitude();

    Double getDistanceMeters();
}
