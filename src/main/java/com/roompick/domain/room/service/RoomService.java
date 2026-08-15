package com.roompick.domain.room.service;

import java.util.List;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
                new BusinessException(
                    ErrorCode.ROOM_NOT_FOUND
                )
            );
    }

    /**
     * 특정 숙소에 소속된 운영 중인
     * 객실 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<RoomListResponseDto>
    findAllActiveSummaryByAccommodationId(
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
     */
    @Transactional(readOnly = true)
    public Room findReservableRoom(
        Long roomId,
        int guestCount
    ) {
        Room room =
            findRoomWithAccommodation(roomId);

        validateRoomAndAccommodationStatus(room);
        validateGuestCount(room, guestCount);

        return room;
    }

    /**
     * 예약 생성 전에 객실 행에 비관적 쓰기 락을 획득하고
     * 객실·숙소 상태와 예약 인원을 검증합니다.
     *
     * ReservationFacade에서 시작한 트랜잭션에 참여하므로
     * 예약 중복 검사와 저장이 끝날 때까지 락이 유지됩니다.
     * 기존 트랜잭션이 없으면 호출할 수 없습니다.
     *
     * 같은 객실의 다른 예약 요청은 현재 트랜잭션이
     * 커밋되거나 롤백될 때까지 대기하며, 락 대기 시간이
     * 초과되면 예약 전용 충돌 예외로 변환합니다.
     */
    @Transactional(
        propagation = Propagation.MANDATORY
    )
    public Room findReservableRoomForUpdate(
        Long roomId,
        int guestCount
    ) {
        Room room;

        try {
            room =
                roomRepository
                    .findByIdForUpdate(roomId)
                    .orElseThrow(() ->
                        new BusinessException(
                            ErrorCode.ROOM_NOT_FOUND
                        )
                    );
        } catch (
            PessimisticLockingFailureException exception
        ) {
            throw new BusinessException(
                ErrorCode.RESERVATION_LOCK_TIMEOUT
            );
        }

        /*
         * 숙소 상태를 조회하면서 LAZY 연관관계가 초기화됩니다.
         * Facade 트랜잭션 안이므로 정상적으로 조회할 수 있습니다.
         */
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
        int maxCapacity,
        List<String> imageUrls
    ) {
        validateAccommodationActive(accommodation);

        validateRoomNumberNotDuplicated(
            accommodation.getId(),
            roomNumber
        );

        Room room =
            Room.create(
                accommodation,
                roomNumber,
                name,
                description,
                pricePerNight,
                standardCapacity,
                maxCapacity
            );

        room.addImages(imageUrls);

        return roomRepository.save(room);
    }

    /**
     * 지정한 숙소에 실제로 소속된 객실을
     * 사용자에게 공개합니다.
     */
    @Transactional
    public Room activateRoom(
        Long accommodationId,
        Long roomId
    ) {
        Room room =
            findByIdAndAccommodationIdWithAccommodation(
                accommodationId,
                roomId
            );

        validateAccommodationActive(
            room.getAccommodation()
        );

        room.activate();

        return room;
    }

    /**
     * 지정한 숙소에 실제로 소속된 객실을
     * 사용자에게 비공개합니다.
     */
    @Transactional
    public Room deactivateRoom(
        Long accommodationId,
        Long roomId
    ) {
        Room room =
            findByIdAndAccommodationId(
                accommodationId,
                roomId
            );

        room.deactivate();

        return room;
    }

    /**
     * 관리자 기능에서 지정한 숙소에 실제로 소속된
     * 객실과 숙소 정보를 함께 조회합니다.
     *
     * 존재하지 않거나 다른 숙소에 소속된 객실이면
     * ROOM_NOT_FOUND 예외를 반환합니다.
     */
    @Transactional(readOnly = true)
    public Room findByIdAndAccommodationIdForAdmin(
        Long accommodationId,
        Long roomId
    ) {
        return findByIdAndAccommodationIdWithAccommodation(
            accommodationId,
            roomId
        );
    }

    /**
     * 객실 전용 타임세일 등록 트랜잭션에서
     * 대상 객실 행에 비관적 쓰기 락을 획득합니다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Room findByIdAndAccommodationIdForTimeSaleUpdate(
        Long accommodationId,
        Long roomId
    ) {
        return roomRepository
            .findByIdAndAccommodationIdForUpdate(
                roomId,
                accommodationId
            )
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.ROOM_NOT_FOUND
                )
            );
    }

    /**
     * 특가 상품 등록 트랜젝션에서
     * 대상 객실 행에 비관적 쓰기 락을 획득합니다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Room findByIdAndAccommodationIdForSpecialOfferUpdate(
        Long accommodationId,
        Long roomId
    ) {
        return roomRepository.findByIdAndAccommodationIdForUpdate(
            roomId,
            accommodationId
        ).orElseThrow(() ->
            new BusinessException(
                ErrorCode.ROOM_NOT_FOUND
            ));
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
                new BusinessException(
                    ErrorCode.ROOM_NOT_FOUND
                )
            );
    }

    private Room
    findByIdAndAccommodationIdWithAccommodation(
        Long accommodationId,
        Long roomId
    ) {
        return roomRepository
            .findByIdAndAccommodationIdWithAccommodation(
                roomId,
                accommodationId
            )
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.ROOM_NOT_FOUND
                )
            );
    }

    private Room findRoomWithAccommodation(
        Long roomId
    ) {
        return roomRepository
            .findByIdWithAccommodation(roomId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.ROOM_NOT_FOUND
                )
            );
    }

    /**
     * 객실과 소속 숙소가 모두 운영 중인지 확인합니다.
     */
    private void validateRoomAndAccommodationStatus(
        Room room
    ) {
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
        if (guestCount < 1) {
            throw new BusinessException(
                ErrorCode.INVALID_GUEST_COUNT
            );
        }

        int maxCapacity =
            room.getMaxCapacity();

        if (guestCount > maxCapacity) {
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
     * 같은 숙소에 동일한 객실 번호가
     * 존재하는지 확인합니다.
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
