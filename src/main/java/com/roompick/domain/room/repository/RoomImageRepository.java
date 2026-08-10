package com.roompick.domain.room.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.room.entity.RoomImage;

public interface RoomImageRepository
    extends JpaRepository<RoomImage, Long> {

    /**
     * 전달받은 객실 ID들의 대표(0번) 이미지를 한 번에 조회합니다.
     *
     * 목록 카드에 필요한 썸네일만 채우기 위한 배치 조회입니다.
     */
    @Query("""
        SELECT image
        FROM RoomImage image
        WHERE image.room.id IN :roomIds
          AND image.sortOrder = 0
        """)
    List<RoomImage> findThumbnailsByRoomIdIn(
        @Param("roomIds") List<Long> roomIds
    );
}
