package com.roompick.domain.accommodation.dto;

import java.time.LocalTime;
import java.util.List;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.room.dto.RoomSummaryResponseDto;
import com.roompick.domain.room.entity.Room;

/**
 * 숙소 상세 조회 결과를 전달하는 응답 DTO입니다.
 */
public record AccommodationDetailResponseDto(
    Long accommodationId,
    String name,
    String address,
    String description,
    LocalTime checkInTime,
    LocalTime checkOutTime,
    AccommodationStatus status,
    List<RoomSummaryResponseDto> rooms
) {

    /**
     * 숙소와 객실 목록을 하나의 상세 조회 응답으로 변환합니다.
     */
    public static AccommodationDetailResponseDto of(
        Accommodation accommodation,
        List<Room> rooms
    ) {
        List<RoomSummaryResponseDto> roomResponses = rooms.stream()
            .map(RoomSummaryResponseDto::from)
            .toList();

        return new AccommodationDetailResponseDto(
            accommodation.getId(),
            accommodation.getName(),
            accommodation.getAddress(),
            accommodation.getDescription(),
            accommodation.getCheckInTime(),
            accommodation.getCheckOutTime(),
            accommodation.getStatus(),
            roomResponses
        );
    }
}
