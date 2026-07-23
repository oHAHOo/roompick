package com.roompick.domain.room.service;

import java.util.List;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    /**
     * 객실 ID로 객실과 소속 숙소를 함께 조회합니다.
     *
     * fetch join을 사용해 객실 상세 응답을 만들 때
     * 숙소 조회 쿼리가 추가로 발생하지 않도록 합니다.
     */
    @Transactional(readOnly = true)
    public Room findById(Long roomId) {
        return roomRepository.findByIdWithAccommodation(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
    }

    /**
     * 특정 숙소에 소속된 객실 목록을 조회합니다.
     *
     * 숙소의 존재 여부는 AccommodationService에서 먼저 확인하므로
     * 객실이 없으면 빈 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<Room> findAllByAccommodationId(Long accommodationId) {
        return roomRepository.findAllByAccommodationId(accommodationId);
    }

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
