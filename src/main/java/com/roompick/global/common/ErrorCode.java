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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_003", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "접근 권한이 없습니다."),

    // 회원 오류
    DUPLICATED_EMAIL(HttpStatus.BAD_REQUEST, "MEMBER_001", "이미 사용 중인 이메일입니다."),
    INVALID_LOGIN(HttpStatus.UNAUTHORIZED, "MEMBER_003", "이메일 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "MEMBER_004", "유효하지 않은 리프레시 토큰입니다."),

    // 숙소 오류
    ACCOMMODATION_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_NAME_REQUIRED", "숙소 이름은 필수입니다."),
    ACCOMMODATION_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_ADDRESS_REQUIRED", "숙소 주소는 필수입니다."),
    ACCOMMODATION_TIME_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_TIME_REQUIRED", "체크인 및 체크아웃 시간은 필수입니다."),
    ACCOMMODATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOMMODATION_NOT_FOUND", "숙소를 찾을 수 없습니다."),
    ACCOMMODATION_INACTIVE(HttpStatus.CONFLICT, "ACCOMMODATION_INACTIVE", "운영 중지된 숙소에는 객실을 등록할 수 없습니다."),

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
    ROOM_NUMBER_DUPLICATED(HttpStatus.CONFLICT, "ROOM_NUMBER_DUPLICATED", "동일한 숙소에 같은 객실 번호가 이미 존재합니다."),

    // 예약 입력 및 상태 오류
    INVALID_STAY_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_STAY_PERIOD", "숙박 기간이 올바르지 않습니다."),
    INVALID_GUEST_COUNT(HttpStatus.BAD_REQUEST, "INVALID_GUEST_COUNT", "예약 인원은 1명 이상이어야 합니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "RESERVATION_ACCESS_DENIED", "해당 예약에 접근할 권한이 없습니다."),
    RESERVATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELABLE", "현재 상태에서는 예약을 취소할 수 없습니다."),
    RESERVATION_NOT_PAYABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_PAYABLE", "현재 예약 상태에서는 결제를 진행할 수 없습니다."),
    RESERVATION_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "RESERVATION_PAYMENT_EXPIRED", "예약의 결제 대기 시간이 만료되었습니다."),
    RESERVATION_LOCK_TIMEOUT(HttpStatus.CONFLICT, "RESERVATION_LOCK_TIMEOUT", "예약 요청이 많습니다. 잠시 후 다시 시도해주세요."),

    // 결제 오류
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAYMENT_ALREADY_EXISTS", "해당 예약의 결제가 이미 생성되었습니다."),
    INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_AMOUNT", "결제 금액이 올바르지 않습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATUS", "현재 결제 상태에서는 요청을 처리할 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "결제 요청 금액이 저장된 결제 금액과 일치하지 않습니다."),

    // PortOne 연동 오류
    PORTONE_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PORTONE_PAYMENT_NOT_FOUND", "PortOne 결제 정보를 찾을 수 없습니다."),
    PORTONE_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "PORTONE_AUTHENTICATION_FAILED", "PortOne 인증에 실패했습니다."),
    PORTONE_API_ERROR(HttpStatus.BAD_GATEWAY, "PORTONE_API_ERROR", "PortOne 결제 정보를 조회하는 중 오류가 발생했습니다."),
    PORTONE_CONNECTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "PORTONE_CONNECTION_FAILED", "현재 PortOne 결제 서버에 연결할 수 없습니다."),
    PORTONE_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "PORTONE_INVALID_RESPONSE", "PortOne 결제 응답이 올바르지 않습니다."),
    PORTONE_PAYMENT_NOT_PAID(HttpStatus.CONFLICT, "PORTONE_PAYMENT_NOT_PAID", "PortOne에서 결제가 완료되지 않았습니다."),
    PORTONE_PAYMENT_ID_MISMATCH(HttpStatus.BAD_GATEWAY, "PORTONE_PAYMENT_ID_MISMATCH", "PortOne 결제 식별값이 일치하지 않습니다."),
    PORTONE_PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "PORTONE_PAYMENT_AMOUNT_MISMATCH", "PortOne 결제 금액이 저장된 결제 금액과 일치하지 않습니다."),
    PAYMENT_LOCK_TIMEOUT(HttpStatus.CONFLICT,"PAYMENT_LOCK_TIMEOUT","결제 처리 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "PAYMENT_IDEMPOTENCY_CONFLICT", "이미 처리된 결제와 요청 정보가 일치하지 않습니다."),
    PAYMENT_STATE_INCONSISTENCY(HttpStatus.INTERNAL_SERVER_ERROR, "PAYMENT_STATE_INCONSISTENCY", "결제 상태와 예약 상태가 일치하지 않습니다."),
    PAYMENT_CONFLICT(HttpStatus.CONFLICT,"PAYMENT_CONFLICT","이미 완료된 결제와 다른 처리 요청이 충돌합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
