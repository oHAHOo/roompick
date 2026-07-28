package com.roompick.domain.room.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    /**
     * 객실 상세 조회에 필요한 객실만 조회합니다.
     *
     * 숙소 정보는 객실 상세 응답에 사용하지 않으므로
     * fetch join 없이 객실만 조회합니다.
     */
    @Transactional(readOnly = true)
    public Room findById(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.ROOM_NOT_FOUND)
            );
    }

    /**
     * 특정 숙소에 소속된 운영 중인 객실 목록을 조회합니다.
     *
     * 객실 목록 화면에 필요한 필드만
     * Repository에서 DTO로 직접 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<RoomListResponseDto> findAllActiveSummaryByAccommodationId(
        Long accommodationId
    ) {
        return roomRepository
            .findAllActiveSummaryByAccommodationId(
                accommodationId
            );
    }

    /**
     * 예약 가능 여부 확인에 필요한 객실을 조회하고
     * 객실 상태와 요청 인원을 검증합니다.
     *
     * 숙소 정보는 사용하지 않으므로
     * fetch join 없이 객실만 조회합니다.
     */
    @Transactional(readOnly = true)
    public Room findReservableRoom(
        Long roomId,
        int guestCount
    ) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.ROOM_NOT_FOUND)
            );

        validateRoomStatus(room);
        validateGuestCount(room, guestCount);

        return room;
    }

    /**
     * 예약 생성에 필요한 객실과 소속 숙소를 함께 조회하고
     * 객실 상태와 요청 인원을 검증합니다.
     *
     * 예약 생성 응답에 숙소 정보가 포함되므로
     * fetch join을 사용해 추가 조회가 발생하지 않도록 합니다.
     */
    @Transactional(readOnly = true)
    public Room findReservableRoomWithAccommodation(
        Long roomId,
        int guestCount
    ) {
        Room room = roomRepository
            .findByIdWithAccommodation(roomId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.ROOM_NOT_FOUND)
            );

        validateRoomStatus(room);
        validateGuestCount(room, guestCount);

        return room;
    }

    /**
     * 운영 중인 숙소에 새로운 객실을 등록합니다.
     */
    @Transactional
    public Room createRoom(
        Accommodation accommodation,
        String roomNumber,
        String name,
        String description,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity
    ) {
        validateAccommodationActive(accommodation);
        validateRoomNumberNotDuplicated(
            accommodation.getId(),
            roomNumber
        );

        Room room = Room.create(
            accommodation,
            roomNumber,
            name,
            description,
            pricePerNight,
            standardCapacity,
            maxCapacity
        );

        return roomRepository.save(room);
    }

    /**
     * 운영 중인 객실인지 확인합니다.
     */
    private void validateRoomStatus(Room room) {
        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new BusinessException(
                ErrorCode.ROOM_INACTIVE
            );
        }
    }

    /**
     * 예약 인원이 1명 이상이고
     * 객실 최대 인원 이하인지 확인합니다.
     */
    private void validateGuestCount(
        Room room,
        int guestCount
    ) {
        if (guestCount < 1) {
            throw new BusinessException(
                ErrorCode.INVALID_GUEST_COUNT
            );
        }

        if (guestCount > room.getMaxCapacity()) {
            throw new BusinessException(
                ErrorCode.ROOM_CAPACITY_EXCEEDED
            );
        }
    }

    /**
     * 객실을 등록할 숙소가 운영 중인지 확인합니다.
     */
    private void validateAccommodationActive(
        Accommodation accommodation
    ) {
        if (
            accommodation.getStatus()
                == AccommodationStatus.INACTIVE
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_INACTIVE
            );
        }
    }

    /**
     * 같은 숙소에 동일한 객실 번호가 존재하는지 확인합니다.
     */
    private void validateRoomNumberNotDuplicated(
        Long accommodationId,
        String roomNumber
    ) {
        boolean duplicated =
            roomRepository
                .existsByAccommodationIdAndRoomNumber(
                    accommodationId,
                    roomNumber
                );

        if (duplicated) {
            throw new BusinessException(
                ErrorCode.ROOM_NUMBER_DUPLICATED
            );
        }
    }
}
