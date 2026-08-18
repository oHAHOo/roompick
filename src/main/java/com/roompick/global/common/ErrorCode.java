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
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_005", "회원을 찾을 수 없습니다."),

    // 숙소 오류
    ACCOMMODATION_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_NAME_REQUIRED", "숙소 이름은 필수입니다."),
    ACCOMMODATION_ADDRESS_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_ADDRESS_REQUIRED", "숙소 주소는 필수입니다."),
    ACCOMMODATION_TIME_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_TIME_REQUIRED", "체크인 및 체크아웃 시간은 필수입니다."),
    ACCOMMODATION_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "ACCOMMODATION_LOCATION_REQUIRED", "숙소 위도와 경도는 모두 입력해야 합니다."),
    ACCOMMODATION_LATITUDE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "ACCOMMODATION_LATITUDE_OUT_OF_RANGE", "숙소 위도는 -90 이상 90 이하이어야 합니다."),
    ACCOMMODATION_LONGITUDE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "ACCOMMODATION_LONGITUDE_OUT_OF_RANGE", "숙소 경도는 -180 이상 180 이하이어야 합니다."),
    ACCOMMODATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCOMMODATION_NOT_FOUND", "숙소를 찾을 수 없습니다."),
    ACCOMMODATION_INACTIVE(HttpStatus.CONFLICT, "ACCOMMODATION_INACTIVE", "운영 중지된 숙소에는 객실을 등록할 수 없습니다."),
    POPULAR_ACCOMMODATION_REQUEST_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "POPULAR_ACCOMMODATION_REQUEST_TIMEOUT", "인기 숙소 조회가 지연되고 있습니다. 잠시 후 다시 시도해주세요."),
    POPULAR_ACCOMMODATION_REQUEST_INTERRUPTED(HttpStatus.SERVICE_UNAVAILABLE, "POPULAR_ACCOMMODATION_REQUEST_INTERRUPTED", "인기 숙소 조회를 완료하지 못했습니다. 잠시 후 다시 시도해주세요."),

    // 장소 검색 외부 API 연동 오류
    PLACE_API_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "PLACE_API_AUTHENTICATION_FAILED", "장소 검색 API 인증에 실패했습니다."),
    PLACE_API_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_API_RATE_LIMITED", "장소 검색 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    PLACE_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PLACE_API_TIMEOUT", "장소 검색 API 응답 시간이 초과되었습니다."),
    PLACE_API_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PLACE_API_UNAVAILABLE", "현재 장소 검색 서비스를 사용할 수 없습니다."),
    PLACE_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "PLACE_API_REQUEST_FAILED", "장소 검색 API 요청을 처리하지 못했습니다."),
    PLACE_API_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "PLACE_API_INVALID_RESPONSE", "장소 검색 API 응답이 올바르지 않습니다."),

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

    // 타임세일 오류
    INVALID_TIME_SALE_DISCOUNT_RATE(HttpStatus.BAD_REQUEST, "INVALID_TIME_SALE_DISCOUNT_RATE", "할인율은 1% 이상 99% 이하여야 합니다."),
    INVALID_TIME_SALE_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_TIME_SALE_PERIOD", "타임세일 기간이 올바르지 않습니다."),
    TIME_SALE_TARGET_MISMATCH(HttpStatus.BAD_REQUEST, "TIME_SALE_TARGET_MISMATCH", "객실이 요청한 숙소에 속하지 않습니다."),
    TIME_SALE_PERIOD_OVERLAP(HttpStatus.CONFLICT, "TIME_SALE_PERIOD_OVERLAP", "같은 대상에 겹치는 타임세일이 이미 존재합니다."),

    // 예약 입력 및 상태 오류
    INVALID_STAY_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_STAY_PERIOD", "숙박 기간이 올바르지 않습니다."),
    INVALID_GUEST_COUNT(HttpStatus.BAD_REQUEST, "INVALID_GUEST_COUNT", "예약 인원은 1명 이상이어야 합니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다."),
    RESERVATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "RESERVATION_ACCESS_DENIED", "해당 예약에 접근할 권한이 없습니다."),
    RESERVATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_CANCELABLE", "현재 상태에서는 예약을 취소할 수 없습니다."),
    RESERVATION_NOT_PAYABLE(HttpStatus.CONFLICT, "RESERVATION_NOT_PAYABLE", "현재 예약 상태에서는 결제를 진행할 수 없습니다."),
    RESERVATION_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "RESERVATION_PAYMENT_EXPIRED", "예약의 결제 대기 시간이 만료되었습니다."),
    RESERVATION_LOCK_TIMEOUT(HttpStatus.CONFLICT, "RESERVATION_LOCK_TIMEOUT", "예약 요청이 많습니다. 잠시 후 다시 시도해주세요."),
    RESERVATION_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "RESERVATION_IDEMPOTENCY_CONFLICT", "같은 멱등성 키로 다른 예약 요청을 처리할 수 없습니다."),

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
    PAYMENT_CONFLICT(HttpStatus.CONFLICT,"PAYMENT_CONFLICT","이미 완료된 결제와 다른 처리 요청이 충돌합니다."),

    // 이미지 업로드 오류
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "IMAGE_001", "업로드할 이미지 파일이 없습니다."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "IMAGE_002", "지원하지 않는 이미지 형식입니다. (jpg, png, webp만 가능)"),
    IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "IMAGE_003", "이미지 파일 용량이 허용 범위를 초과했습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "IMAGE_004", "이미지 업로드 중 오류가 발생했습니다."),
    IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "IMAGE_005", "이미지는 최대 10장까지 등록할 수 있습니다."),

    // 특가 상품 오류
    SPECIAL_OFFER_NOT_FOUND(HttpStatus.NOT_FOUND, "SPECIAL_OFFER_NOT_FOUND", "특가 상품을 찾을 수 없습니다."),
    SPECIAL_OFFER_NOT_ACTIVE(HttpStatus.CONFLICT, "SPECIAL_OFFER_NOT_ACTIVE", "현재 판매중인 특가 상품이 아닙니다."),
    OFFER_OCCUPY_PUBLISH_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "OFFER_OCCUPY_PUBLISH_TIMEOUT", "점유 요청 접수가 지연되고 있습니다. 잠시 후 다시 시도해주세요."),
    OFFER_OCCUPY_PUBLISH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "OFFER_OCCUPY_PUBLISH_FAILED", "점유 요청 접수에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // 대기열 오류
    WAITLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "WAITLIST_NOT_FOUND", "점유 요청 내역을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
