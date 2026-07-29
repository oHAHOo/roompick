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
     * 객실과 숙소가 모두 운영 중인 경우에만 공개합니다.
     * 두 상태는 Repository의 단일 조회 조건으로 확인합니다.
     */
    @Transactional(readOnly = true)
    public Room findActiveById(Long roomId) {
        return roomRepository
            .findPublicById(
                roomId,
                RoomStatus.ACTIVE,
                AccommodationStatus.ACTIVE
            )
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
     * 숙소 상태 검증에 추가 조회가 발생하지 않도록
     * 객실과 숙소를 fetch join으로 함께 조회합니다.
     */
    @Transactional(readOnly = true)
    public Room findReservableRoom(
        Long roomId,
        int guestCount
    ) {
        Room room = findRoomWithAccommodation(roomId);

        validateRoomAndAccommodationStatus(room);
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
        Room room = findRoomWithAccommodation(roomId);

        validateRoomAndAccommodationStatus(room);
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
     * 지정한 숙소에 실제로 소속된 객실을 사용자에게 공개합니다.
     *
     * 영속 상태의 Entity를 변경하므로 별도의 save 호출은 필요하지 않습니다.
     */
    @Transactional
    public Room activateRoom(
        Long accommodationId,
        Long roomId
    ) {
        Room room = findByIdAndAccommodationId(
            accommodationId,
            roomId
        );

        room.activate();

        return room;
    }

    /**
     * 지정한 숙소에 실제로 소속된 객실을 사용자에게 비공개합니다.
     */
    @Transactional
    public Room deactivateRoom(
        Long accommodationId,
        Long roomId
    ) {
        Room room = findByIdAndAccommodationId(
            accommodationId,
            roomId
        );

        room.deactivate();

        return room;
    }

    private Room findByIdAndAccommodationId(
        Long accommodationId,
        Long roomId
    ) {
        return roomRepository
            .findByIdAndAccommodationId(
                roomId,
                accommodationId
            )
            .orElseThrow(() ->
                new BusinessException(ErrorCode.ROOM_NOT_FOUND)
            );
    }

    private Room findRoomWithAccommodation(Long roomId) {
        return roomRepository
            .findByIdWithAccommodation(roomId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.ROOM_NOT_FOUND)
            );
    }

    /**
     * 객실과 소속 숙소가 모두 운영 중인지 확인합니다.
     *
     * 호출 전에 숙소가 fetch join으로 로딩되어 있으므로
     * 상태 확인을 위한 추가 조회가 발생하지 않습니다.
     */
    private void validateRoomAndAccommodationStatus(Room room) {
        if (
            room.getStatus() != RoomStatus.ACTIVE
                || room.getAccommodation().getStatus()
                    != AccommodationStatus.ACTIVE
        ) {
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
        /*
         * DTO 검증을 거치지 않고 Service가 직접 호출되는 경우를 대비한
         * 방어 검증입니다.
         */
        if (guestCount < 1) {
            throw new BusinessException(
                ErrorCode.INVALID_GUEST_COUNT
            );
        }

        int maxCapacity =
            room.getMaxCapacity();

        if (guestCount > maxCapacity) {
            /*
             * 상단 메시지보다 구체적인 최대 허용 인원을 제공하므로
             * guestCount 필드 상세 오류를 함께 전달합니다.
             */
            throw new BusinessException(
                ErrorCode.ROOM_CAPACITY_EXCEEDED,
                List.of(
                    new BusinessException.BusinessFieldError(
                        "guestCount",
                        "선택한 객실은 최대 "
                            + maxCapacity
                            + "명까지 예약할 수 있습니다."
                    )
                )
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
