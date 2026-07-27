package com.roompick.domain.room.dto;

/**
 * 숙소별 객실 목록의 객실 요약 정보를 반환하는 DTO입니다.
 *
 * W1 와이어프레임의 객실 카드에 필요한
 * 최소한의 정보만 반환합니다.
 */
public record RoomListResponseDto(

    Long roomId,

    String name,

    long pricePerNight,

    int standardCapacity,

    int maxCapacity

) {
}
