package com.roompick.domain.accommodation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.accommodation.entity.AccommodationImage;

public interface AccommodationImageRepository
    extends JpaRepository<AccommodationImage, Long> {

    /**
     * 전달받은 숙소 ID들의 대표(0번) 이미지를 한 번에 조회합니다.
     *
     * 목록·인기 숙소 카드에 필요한 썸네일만 채우기 위한 배치 조회입니다.
     */
    @Query("""
        SELECT image
        FROM AccommodationImage image
        WHERE image.accommodation.id IN :accommodationIds
          AND image.sortOrder = 0
        """)
    List<AccommodationImage> findThumbnailsByAccommodationIdIn(
        @Param("accommodationIds") List<Long> accommodationIds
    );
}
