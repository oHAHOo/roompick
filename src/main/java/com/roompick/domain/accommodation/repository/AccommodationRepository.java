package com.roompick.domain.accommodation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
