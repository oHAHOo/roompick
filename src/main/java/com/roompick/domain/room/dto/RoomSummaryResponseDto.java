package com.roompick.domain.room.dto;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;

/**
 * 숙소 상세 화면에 포함되는 객실 요약 정보입니다.
 */
public record RoomSummaryResponseDto(
    Long roomId,
    String name,
    long pricePerNight,
    int standardCapacity,
    int maxCapacity,
    RoomStatus status
) {

    /**
     * Room 엔티티를 객실 요약 응답 DTO로 변환합니다.
     */
    public static RoomSummaryResponseDto from(Room room) {
        return new RoomSummaryResponseDto(
            room.getId(),
            room.getName(),
            room.getPricePerNight(),
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            room.getStatus()
        );
    }
}
