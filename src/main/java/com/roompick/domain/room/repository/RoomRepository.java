package com.roompick.domain.room.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;

/**
 * 객실 데이터를 저장하고 조회하는 Repository입니다.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * 예약 생성에 필요한 객실과 소속 숙소를
     * fetch join으로 한 번에 조회합니다.
     *
     * 예약 생성 응답에서 숙소 정보를 사용할 때
     * LAZY 연관관계의 추가 조회 쿼리가 발생하지 않도록 합니다.
     */
    @Query("""
            SELECT room
            FROM Room room
            JOIN FETCH room.accommodation
            WHERE room.id = :roomId
            """)
    Optional<Room> findByIdWithAccommodation(@Param("roomId") Long roomId);

    /**
     * 사용자 상세 조회에서 공개 중인 객실만 조회합니다.
     */
    @Query("""
        SELECT room
        FROM Room room
        JOIN room.accommodation accommodation
        WHERE room.id = :roomId
          AND room.status = :roomStatus
          AND accommodation.status = :accommodationStatus
        """)
    Optional<Room> findPublicById(
        @Param("roomId") Long roomId,
        @Param("roomStatus") RoomStatus roomStatus,
        @Param("accommodationStatus") AccommodationStatus accommodationStatus
    );

    /**
     * 관리자 요청의 숙소 ID와 객실 소속을 한 번의 조회로 검증합니다.
     */
    Optional<Room> findByIdAndAccommodationId(
        Long roomId,
        Long accommodationId
    );

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
     * 특정 숙소의 운영 중인 객실을 목록 DTO로 직접 조회합니다.
     *
     * 객실 목록 화면에 필요한 필드만 조회하며,
     * 객실 번호와 객실 ID 기준으로 고정 정렬합니다.
     */
    @Query("""
        SELECT new com.roompick.domain.room.dto.RoomListResponseDto(
            room.id,
            room.name,
            room.pricePerNight,
            room.standardCapacity,
            room.maxCapacity
        )
        FROM Room room
        WHERE room.accommodation.id = :accommodationId
          AND room.status =
              com.roompick.domain.room.entity.RoomStatus.ACTIVE
        ORDER BY room.roomNumber ASC, room.id ASC
        """)
    List<RoomListResponseDto> findAllActiveSummaryByAccommodationId(
        @Param("accommodationId") Long accommodationId
    );

    /**
     * 객실 번호 중복을 조회합니다.
     */
    boolean existsByAccommodationIdAndRoomNumber(
        Long accommodationId,
        String roomNumber
    );
}
