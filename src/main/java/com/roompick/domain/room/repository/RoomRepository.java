package com.roompick.domain.room.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.room.entity.Room;

/**
 * 객실 데이터를 저장하고 조회하는 Repository입니다.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * 객실 상세 조회에 필요한 숙소까지 한 번의 쿼리로 조회합니다.
     *
     * LAZY 연관관계를 개별 조회하면서 발생할 수 있는 추가 쿼리를 방지합니다.
     */
    @Query("""
            SELECT room
            FROM Room room
            JOIN FETCH room.accommodation
            WHERE room.id = :roomId
            """)
    Optional<Room> findByIdWithAccommodation(@Param("roomId") Long roomId);

    /**
     * 특정 숙소에 포함된 객실을 한 번의 쿼리로 조회합니다.
     */
    @Query("""
            SELECT room
            FROM Room room
            WHERE room.accommodation.id = :accommodationId
            ORDER BY room.id ASC
            """)
    List<Room> findAllByAccommodationId(@Param("accommodationId") Long accommodationId);

    /**
     * 객실 번호 중복을 조회합니다.
     */
    boolean existsByAccommodationIdAndRoomNumber(
        Long accommodationId,
        String roomNumber
    );
}
