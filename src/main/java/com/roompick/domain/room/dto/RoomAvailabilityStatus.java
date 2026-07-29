package com.roompick.domain.room.dto;

/**
 * 선택한 숙박 기간을 기준으로 화면에 표시할 객실 상태입니다.
 *
 * 운영 상태를 저장하는 RoomStatus와 달리 DB에 저장하지 않습니다.
 */
public enum RoomAvailabilityStatus {
    ACTIVE,
    SOLD_OUT
}
