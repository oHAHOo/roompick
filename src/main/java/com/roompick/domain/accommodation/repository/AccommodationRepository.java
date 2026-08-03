package com.roompick.domain.accommodation.repository;

import java.util.List;

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
                accommodation.address
            )
            FROM Accommodation accommodation
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
            accommodation.address
        )
        FROM Accommodation accommodation
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
            accommodation.address
        )
        FROM Accommodation accommodation
        WHERE accommodation.status =
            com.roompick.domain.accommodation.entity.AccommodationStatus.ACTIVE
        ORDER BY accommodation.createdAt DESC,
            accommodation.id DESC
        """
    )
    List<AccommodationListResponseDto> findLatestActive(
        Pageable pageable
    );
}
