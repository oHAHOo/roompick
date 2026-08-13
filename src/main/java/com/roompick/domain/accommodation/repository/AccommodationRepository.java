package com.roompick.domain.accommodation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;

/**
 * 숙소 데이터를 저장하고 조회하는 Repository입니다.
 *
 * 단건 조회는 Entity로 반환하고,
 * 목록 조회는 필요한 필드만 DTO로 직접 조회합니다.
 *
 * 목록 조회 DTO의 대표(썸네일) 이미지는 accommodation_images의
 * (accommodation_id, sort_order) UNIQUE 제약으로 sort_order = 0인 행이
 * 최대 1건만 존재함이 보장되므로, LEFT JOIN으로 조회해도 행이 늘어나지 않습니다.
 * 이 덕분에 대표 이미지를 별도 배치 쿼리 없이 원래의 단일 쿼리로 함께 가져올 수 있습니다.
 */
public interface AccommodationRepository
    extends JpaRepository<Accommodation, Long> {

    /**
     * 운영 중인 숙소를 ID 오름차순으로 조회합니다.
     *
     * 목록 화면에 필요하지 않은 설명, 체크인·체크아웃 시간과
     * 객실 연관 데이터는 조회하지 않습니다.
     */
    @Query(
        value = """
            SELECT new com.roompick.domain.accommodation.dto.AccommodationListResponseDto(
                accommodation.id,
                accommodation.name,
                accommodation.address,
                image.imageUrl
            )
            FROM Accommodation accommodation
            LEFT JOIN accommodation.images image
                ON image.sortOrder = 0
            WHERE accommodation.status =
                com.roompick.domain.accommodation.entity.AccommodationStatus.ACTIVE
            ORDER BY accommodation.id ASC
            """,
        countQuery = """
            SELECT COUNT(accommodation)
            FROM Accommodation accommodation
            WHERE accommodation.status =
                com.roompick.domain.accommodation.entity.AccommodationStatus.ACTIVE
            """
    )
    Page<AccommodationListResponseDto> findAllActive(
        Pageable pageable
    );

    /**
     * 전달받은 숙소 ID 중 운영 중인 숙소의 공개 요약 정보만 조회합니다.
     *
     * Redis 랭킹에 포함된 숙소를 IN 조건으로 한 번에 조회하여
     * 숙소별 반복 SELECT가 발생하지 않도록 합니다.
     *
     * 반환 순서는 데이터베이스가 보장하지 않으므로
     * Redis 랭킹 순서에 맞춘 재정렬은 Service에서 담당합니다.
     */
    @Query(
        """
        SELECT new com.roompick.domain.accommodation.dto.AccommodationListResponseDto(
            accommodation.id,
            accommodation.name,
            accommodation.address,
            image.imageUrl
        )
        FROM Accommodation accommodation
        LEFT JOIN accommodation.images image
            ON image.sortOrder = 0
        WHERE accommodation.id IN :accommodationIds
            AND accommodation.status =
                com.roompick.domain.accommodation.entity.AccommodationStatus.ACTIVE
        """
    )
    List<AccommodationListResponseDto> findAllActiveSummaryByIdIn(
        @Param("accommodationIds") List<Long> accommodationIds
    );

    /**
     * Redis 인기 랭킹을 사용할 수 없을 때
     * 임시 fallback으로 제공할 최신 운영 숙소를 조회합니다.
     *
     * 이 결과는 실제 인기 순위가 아니며,
     * 장애 중 API 응답을 유지하기 위한 임시 목록입니다.
     */
    @Query(
        """
        SELECT new com.roompick.domain.accommodation.dto.AccommodationListResponseDto(
            accommodation.id,
            accommodation.name,
            accommodation.address,
            image.imageUrl
        )
        FROM Accommodation accommodation
        LEFT JOIN accommodation.images image
            ON image.sortOrder = 0
        WHERE accommodation.status =
            com.roompick.domain.accommodation.entity.AccommodationStatus.ACTIVE
        ORDER BY accommodation.createdAt DESC,
            accommodation.id DESC
        """
    )
    List<AccommodationListResponseDto> findLatestActive(
        Pageable pageable
    );

    /**
     * 숙소 상세 조회에 필요한 이미지 목록을 fetch join으로 함께 조회합니다.
     *
     * 상세 조회에서만 이미지 전체 목록이 필요하므로,
     * 다른 조회 경로(공개 상태 확인 등)까지 이미지 로딩을 강제하지 않도록
     * 전용 쿼리로 분리합니다.
     */
    @Query("""
        SELECT accommodation
        FROM Accommodation accommodation
        LEFT JOIN FETCH accommodation.images
        WHERE accommodation.id = :accommodationId
        """)
    Optional<Accommodation> findByIdWithImages(
        @Param("accommodationId") Long accommodationId
    );
}
