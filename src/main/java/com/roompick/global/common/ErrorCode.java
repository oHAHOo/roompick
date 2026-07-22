package com.roompick.global.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * RoomPick API에서 사용하는 공통 에러 코드를 관리합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통 오류
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "서버 내부 오류가 발생했습니다."),

    // 숙소 오류
    ACCOMMODATION_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_NAME_REQUIRED", "숙소 이름은 필수입니다."),
    ACCOMMODATION_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_ADDRESS_REQUIRED", "숙소 주소는 필수입니다."),
    ACCOMMODATION_TIME_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_TIME_REQUIRED", "체크인 및 체크아웃 시간은 필수입니다."),
    ACCOMMODATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOMMODATION_NOT_FOUND", "숙소를 찾을 수 없습니다."),

    // 객실 오류
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "객실을 찾을 수 없습니다."),
    ROOM_INACTIVE(HttpStatus.CONFLICT, "ROOM_INACTIVE", "현재 이용할 수 없는 객실입니다."),
    ROOM_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "ROOM_CAPACITY_EXCEEDED", "객실 최대 인원을 초과했습니다."),
    ROOM_NOT_AVAILABLE(HttpStatus.CONFLICT, "ROOM_NOT_AVAILABLE", "선택한 날짜에는 객실을 예약할 수 없습니다."),
    ROOM_ACCOMMODATION_REQUIRED(HttpStatus.BAD_REQUEST, "ROOM_ACCOMMODATION_REQUIRED", "객실이 소속될 숙소는 필수입니다."),
    ROOM_NUMBER_REQUIRED(HttpStatus.BAD_REQUEST, "ROOM_NUMBER_REQUIRED", "객실 번호는 필수입니다."),
    ROOM_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "ROOM_NAME_REQUIRED", "객실 이름은 필수입니다."),
    INVALID_ROOM_PRICE(HttpStatus.BAD_REQUEST, "INVALID_ROOM_PRICE", "객실 가격은 0원 이상이어야 합니다."),
    INVALID_ROOM_CAPACITY(HttpStatus.BAD_REQUEST, "INVALID_ROOM_CAPACITY", "객실 인원 설정이 올바르지 않습니다."),

    // 예약 입력 및 상태 오류
    INVALID_STAY_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_STAY_PERIOD", "숙박 기간이 올바르지 않습니다."),
    INVALID_GUEST_COUNT(HttpStatus.BAD_REQUEST, "INVALID_GUEST_COUNT", "예약 인원은 1명 이상이어야 합니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "RESERVATION_ACCESS_DENIED", "해당 예약에 접근할 권한이 없습니다."),
    RESERVATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELABLE", "현재 상태에서는 예약을 취소할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
