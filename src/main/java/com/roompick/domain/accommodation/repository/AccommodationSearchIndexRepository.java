package com.roompick.domain.accommodation.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;

/**
 * Elasticsearch 숙소 검색 인덱스 재생성을 위한
 * MySQL 조회 전용 Repository입니다.
 *
 * Entity 전체를 조회하지 않고 Projection만 사용하며,
 * OFFSET 대신 accommodationId 기반 Keyset Pagination으로
 * 일정 개수씩 데이터를 가져옵니다.
 */
public interface AccommodationSearchIndexRepository
    extends Repository<Accommodation, Long> {

    /**
     * 마지막으로 처리한 숙소 ID 이후의 데이터를
     * ID 오름차순으로 일정 개수만 조회합니다.
     *
     * 좌표가 없는 숙소는 위치 검색 인덱스에 사용할 수 없으므로
     * 재색인 대상에서 제외합니다.
     *
     * ACTIVE 숙소뿐 아니라 INACTIVE 숙소도 함께 조회하여
     * Elasticsearch의 status 필드까지 MySQL과 동기화합니다.
     */
    @Transactional(readOnly = true)
    @Query(
        """
        SELECT
            accommodation.id AS accommodationId,
            accommodation.name AS name,
            accommodation.address AS address,
            accommodation.status AS status,
            accommodation.latitude AS latitude,
            accommodation.longitude AS longitude
        FROM Accommodation accommodation
        WHERE accommodation.id > :lastAccommodationId
        AND accommodation.latitude IS NOT NULL
        AND accommodation.longitude IS NOT NULL
        ORDER BY accommodation.id ASC
        """
    )
    List<AccommodationSearchIndexProjection>
    findSearchIndexBatchAfterId(
        @Param("lastAccommodationId")
        Long lastAccommodationId,
        Pageable pageable
    );
}
