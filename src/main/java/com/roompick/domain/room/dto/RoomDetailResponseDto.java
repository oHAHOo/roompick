package com.roompick.domain.room.dto;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;

/**
 * 객실과 소속 숙소의 상세 정보를 전달하는 응답 DTO입니다.
 */
public record RoomDetailResponseDto(
    Long roomId,
    AccommodationSummaryResponseDto accommodation,
    String roomNumber,
    String name,
    String description,
    long pricePerNight,
    int standardCapacity,
    int maxCapacity,
    RoomStatus status
) {
    /**
     * Room Entity를 객실 상세 응답 DTO로 변환합니다.
     *
     * RoomRepository의 fetch join으로 숙소를 함께 조회하므로
     * 숙소 정보를 읽을 때 추가 쿼리가 발생하지 않습니다.
     */
    public static RoomDetailResponseDto from(Room room) {
        return new RoomDetailResponseDto(
            room.getId(),
            AccommodationSummaryResponseDto.from(
                room.getAccommodation()
            ),
            room.getRoomNumber(),
            room.getName(),
            room.getDescription(),
            room.getPricePerNight(),
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            room.getStatus()
        );
    }

    /**
     * 객실 상세 응답에 포함되는 숙소 요약 정보입니다.
     */
    public record AccommodationSummaryResponseDto(
        Long accommodationId,
        String name,
        String address
    ) {

        public static AccommodationSummaryResponseDto from(
            Accommodation accommodation
        ) {
            return new AccommodationSummaryResponseDto(
                accommodation.getId(),
                accommodation.getName(),
                accommodation.getAddress()
            );
        }
    }
}
