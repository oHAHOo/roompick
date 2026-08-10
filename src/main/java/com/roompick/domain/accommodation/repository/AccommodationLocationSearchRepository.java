package com.roompick.domain.accommodation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.accommodation.entity.Accommodation;

/**
 * MySQL을 사용한 위치 기반 숙소 검색 Repository입니다.
 *
 * Elasticsearch 도입 전 기준 성능을 측정하기 위한 검색 경로이며,
 * 숙소 Entity 전체를 조회하지 않고 필요한 필드와 거리만 반환합니다.
 */
public interface AccommodationLocationSearchRepository
    extends Repository<Accommodation, Long> {

    /**
     * 사용자 위치를 기준으로 지정 반경 안의 ACTIVE 숙소를 검색합니다.
     *
     * MySQL ST_Distance_Sphere()를 사용하여 두 좌표 사이의
     * 구면 거리를 미터 단위로 계산합니다.
     *
     * POINT의 X축에는 경도(longitude),
     * Y축에는 위도(latitude)를 전달해야 합니다.
     *
     * keyword가 없으면 위치 조건만 적용하고,
     * keyword가 있으면 숙소명 또는 주소에 포함된 숙소만 조회합니다.
     */
    @Query(
        value = """
            SELECT
                searched.accommodationId AS accommodationId,
                searched.name AS name,
                searched.address AS address,
                searched.latitude AS latitude,
                searched.longitude AS longitude,
                searched.distanceMeters AS distanceMeters
            FROM (
                SELECT
                    accommodation.accommodation_id AS accommodationId,
                    accommodation.name AS name,
                    accommodation.address AS address,
                    accommodation.latitude AS latitude,
                    accommodation.longitude AS longitude,
                    ST_Distance_Sphere(
                        POINT(accommodation.longitude, accommodation.latitude),
                        POINT(:longitude, :latitude)
                    ) AS distanceMeters
                FROM accommodations accommodation
                WHERE accommodation.status = 'ACTIVE'
                  AND accommodation.latitude IS NOT NULL
                  AND accommodation.longitude IS NOT NULL
                  AND (
                      :keyword IS NULL
                      OR :keyword = ''
                      OR accommodation.name LIKE CONCAT('%', :keyword, '%')
                      OR accommodation.address LIKE CONCAT('%', :keyword, '%')
                  )
            ) searched
            WHERE searched.distanceMeters <= (:radiusKm * 1000)
            ORDER BY searched.distanceMeters ASC,
                     searched.accommodationId ASC
            LIMIT :limit
            """,
        nativeQuery = true
    )
    List<AccommodationLocationSearchProjection> searchNearby(
        @Param("keyword") String keyword,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("radiusKm") double radiusKm,
        @Param("limit") int limit
    );
}
