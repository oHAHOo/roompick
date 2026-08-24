package com.roompick.domain.accommodation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.accommodation.entity.Accommodation;

/**
 * MySQL을 사용한 위치 기반 숙소 검색 Repository입니다.
 *
 * Bounding Box로 검색 후보를 먼저 줄이고,
 * 좌표 복합 인덱스를 사용할 수 있는 범위 조건으로 후보를 줄인 뒤
 * ST_Distance_Sphere()로 정확한 반경을 검증합니다.
 *
 * 숙소 Entity 전체를 조회하지 않고
 * 검색 응답에 필요한 필드와 거리만 Projection으로 반환합니다.
 */
public interface AccommodationLocationSearchRepository
    extends Repository<Accommodation, Long> {

    /**
     * 사용자 위치를 기준으로 지정 반경 안의 ACTIVE 숙소를 검색합니다.
     *
     * 검색 과정:
     *
     * 1. latitude / longitude 범위로 검색 후보를 제한합니다.
     * 2. Bounding Box로 검색 후보를 선필터링합니다.
     * 3. ACTIVE 상태 및 선택적인 keyword 조건을 적용합니다.
     * 4. ST_Distance_Sphere()로 정확한 거리를 계산합니다.
     * 5. 실제 검색 반경 안의 숙소만 남깁니다.
     * 6. 거리 오름차순으로 정렬하여 limit만큼 반환합니다.
     *
     * 실행계획은 데이터 분포, 검색 반경과 keyword 선택도에 따라
     * 달라질 수 있으므로 특정 인덱스를 강제하지 않습니다.
     *
     * Bounding Box는 원형 검색 반경을 감싸는 사각형이므로
     * 최종 ST_Distance_Sphere() 검증은 반드시 유지합니다.
     *
     * POINT의 X축에는 경도(longitude),
     * Y축에는 위도(latitude)를 전달합니다.
     */
    @Query(
        value = """
            SELECT
                searched.accommodationId AS accommodationId,
                searched.name AS name,
                searched.address AS address,
                searched.latitude AS latitude,
                searched.longitude AS longitude,
                searched.distanceMeters AS distanceMeters,
                searched.imageUrl AS imageUrl
            FROM (
                SELECT
                    accommodation.accommodation_id AS accommodationId,
                    accommodation.name AS name,
                    accommodation.address AS address,
                    accommodation.latitude AS latitude,
                    accommodation.longitude AS longitude,
                    ST_Distance_Sphere(
                        POINT(
                            accommodation.longitude,
                            accommodation.latitude
                        ),
                        POINT(
                            :longitude,
                            :latitude
                        )
                    ) AS distanceMeters,
                    image.image_url AS imageUrl
                FROM accommodations accommodation
                LEFT JOIN accommodation_images image
                    ON image.accommodation_id = accommodation.accommodation_id
                    AND image.sort_order = 0
                WHERE accommodation.status = 'ACTIVE'
                  AND accommodation.latitude IS NOT NULL
                  AND accommodation.longitude IS NOT NULL

                  AND accommodation.latitude
                      BETWEEN :minLatitude AND :maxLatitude

                  AND (
                      (
                          :minLongitude <= :maxLongitude
                          AND accommodation.longitude
                              BETWEEN :minLongitude AND :maxLongitude
                      )
                      OR
                      (
                          :minLongitude > :maxLongitude
                          AND (
                              accommodation.longitude >= :minLongitude
                              OR accommodation.longitude <= :maxLongitude
                          )
                      )
                  )

                  AND (
                      :keyword IS NULL
                      OR :keyword = ''
                      OR accommodation.name
                          LIKE CONCAT(
                              '%',
                              REPLACE(
                                  REPLACE(
                                      REPLACE(:keyword, '!', '!!'),
                                      '%',
                                      '!%'
                                  ),
                                  '_',
                                  '!_'
                              ),
                              '%'
                          ) ESCAPE '!'
                      OR accommodation.address
                          LIKE CONCAT(
                              '%',
                              REPLACE(
                                  REPLACE(
                                      REPLACE(:keyword, '!', '!!'),
                                      '%',
                                      '!%'
                                  ),
                                  '_',
                                  '!_'
                              ),
                              '%'
                          ) ESCAPE '!'
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
        @Param("minLatitude") double minLatitude,
        @Param("maxLatitude") double maxLatitude,
        @Param("minLongitude") double minLongitude,
        @Param("maxLongitude") double maxLongitude,
        @Param("limit") int limit
    );
}
