package com.roompick.domain.room.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;

import jakarta.persistence.LockModeType;

/**
 * 객실 데이터를 저장하고 조회하는 Repository입니다.
 */
public interface RoomRepository
    extends JpaRepository<Room, Long> {

    /**
     * 예약 생성 시 동일 객실에 대한 요청을 직렬화하기 위해
     * 객실을 비관적 쓰기 락과 함께 조회합니다.
     *
     * 객실 행만 먼저 잠그고 숙소는 트랜잭션 안에서
     * 지연 로딩하여 같은 숙소의 다른 객실 예약까지
     * 불필요하게 대기하지 않도록 합니다.
     * MySQL 락 대기 한도는 DataSource Connection 초기화 SQL의
     * innodb_lock_wait_timeout 설정으로 제어합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT room
        FROM Room room
        WHERE room.id = :roomId
        """)
    Optional<Room> findByIdForUpdate(
        @Param("roomId") Long roomId
    );

    /**
     * 객실 전용 타임세일 등록 요청을 직렬화하기 위해
     * 지정한 숙소에 소속된 객실 행을 비관적 쓰기 락으로 조회합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT room
        FROM Room room
        WHERE room.id = :roomId
          AND room.accommodation.id = :accommodationId
        """)
    Optional<Room> findByIdAndAccommodationIdForUpdate(
        @Param("roomId") Long roomId,
        @Param("accommodationId") Long accommodationId
    );

    /**
     * 예약 생성에 필요한 객실과 소속 숙소를
     * fetch join으로 한 번에 조회합니다.
     *
     * 일반 조회에서 LAZY 연관관계의
     * 추가 쿼리가 발생하지 않도록 합니다.
     */
    @Query("""
        SELECT room
        FROM Room room
        JOIN FETCH room.accommodation
        WHERE room.id = :roomId
        """)
    Optional<Room> findByIdWithAccommodation(
        @Param("roomId") Long roomId
    );

    /**
     * 관리자 객실 활성화 요청에서 객실 소속과 숙소 상태를
     * 한 번의 조회로 확인할 수 있도록 숙소를 fetch join합니다.
     */
    @Query("""
        SELECT room
        FROM Room room
        JOIN FETCH room.accommodation accommodation
        WHERE room.id = :roomId
          AND accommodation.id = :accommodationId
        """)
    Optional<Room> findByIdAndAccommodationIdWithAccommodation(
        @Param("roomId") Long roomId,
        @Param("accommodationId") Long accommodationId
    );

    /**
     * 사용자 상세 조회에서 공개 중인 객실만 조회합니다.
     *
     * 상세 응답에 이미지 전체 목록이 필요하므로 fetch join으로 함께 가져옵니다.
     */
    @Query("""
        SELECT room
        FROM Room room
        JOIN room.accommodation accommodation
        LEFT JOIN FETCH room.images
        WHERE room.id = :roomId
          AND room.status = :roomStatus
          AND accommodation.status = :accommodationStatus
        """)
    Optional<Room> findPublicById(
        @Param("roomId") Long roomId,
        @Param("roomStatus") RoomStatus roomStatus,
        @Param("accommodationStatus")
        AccommodationStatus accommodationStatus
    );

    /**
     * 관리자 요청의 숙소 ID와 객실 소속을
     * 한 번의 조회로 검증합니다.
     */
    Optional<Room> findByIdAndAccommodationId(
        Long roomId,
        Long accommodationId
    );

    /**
     * 관리자 상세 조회에서 운영 상태와 무관하게 객실을 조회합니다.
     *
     * 상세 응답에 이미지 전체 목록이 필요하므로 fetch join으로 함께 가져옵니다.
     */
    @Query("""
        SELECT room
        FROM Room room
        LEFT JOIN FETCH room.images
        WHERE room.id = :roomId
        """)
    Optional<Room> findAnyByIdForAdmin(
        @Param("roomId") Long roomId
    );

    /**
     * 관리자 목록 조회에서 운영 상태와 무관하게
     * 특정 숙소에 소속된 모든 객실을 목록 DTO로 조회합니다.
     */
    @Query("""
        SELECT new com.roompick.domain.room.dto.RoomListResponseDto(
            room.id,
            room.name,
            room.pricePerNight,
            room.standardCapacity,
            room.maxCapacity,
            image.imageUrl,
            room.status
        )
        FROM Room room
        LEFT JOIN room.images image
            ON image.sortOrder = 0
        WHERE room.accommodation.id = :accommodationId
        ORDER BY room.roomNumber ASC, room.id ASC
        """)
    List<RoomListResponseDto>
    findAllSummaryByAccommodationIdForAdmin(
        @Param("accommodationId")
        Long accommodationId
    );

    /**
     * 숙소 논리 삭제 시 소속 객실을 한 번의 벌크 UPDATE로
     * 모두 비활성화합니다.
     *
     * 벌크 연산은 JPA Auditing을 거치지 않으므로
     * updatedAt도 쿼리에서 함께 갱신합니다.
     */
    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true
    )
    @Query("""
        UPDATE Room room
        SET room.status =
                com.roompick.domain.room.entity.RoomStatus.INACTIVE,
            room.updatedAt = CURRENT_TIMESTAMP
        WHERE room.accommodation.id = :accommodationId
          AND room.status <>
                com.roompick.domain.room.entity.RoomStatus.INACTIVE
        """)
    int deactivateAllByAccommodationId(
        @Param("accommodationId")
        Long accommodationId
    );

    /**
     * 특정 숙소의 운영 중인 객실을 목록 DTO로 직접 조회합니다.
     *
     * 대표(썸네일) 이미지는 room_images의 (room_id, sort_order) UNIQUE 제약으로
     * sort_order = 0인 행이 최대 1건만 존재함이 보장되므로,
     * LEFT JOIN으로 조회해도 행이 늘어나지 않습니다.
     */
    @Query("""
        SELECT new com.roompick.domain.room.dto.RoomListResponseDto(
            room.id,
            room.name,
            room.pricePerNight,
            room.standardCapacity,
            room.maxCapacity,
            image.imageUrl
        )
        FROM Room room
        LEFT JOIN room.images image
            ON image.sortOrder = 0
        WHERE room.accommodation.id = :accommodationId
          AND room.status =
              com.roompick.domain.room.entity.RoomStatus.ACTIVE
        ORDER BY room.roomNumber ASC, room.id ASC
        """)
    List<RoomListResponseDto>
    findAllActiveSummaryByAccommodationId(
        @Param("accommodationId")
        Long accommodationId
    );

    /**
     * 객실 번호 중복을 조회합니다.
     */
    boolean existsByAccommodationIdAndRoomNumber(
        Long accommodationId,
        String roomNumber
    );

}
