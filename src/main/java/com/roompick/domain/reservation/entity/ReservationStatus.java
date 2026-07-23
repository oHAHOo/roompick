package com.roompick.domain.reservation.entity;

/**
 * 예약의 처리 상태를 나타냅니다.
 */
public enum ReservationStatus {

    // 예약 생성 후 결제를 기다리는 상태
    PENDING_PAYMENT,

    // 결제가 완료되어 예약이 확정된 상태
    CONFIRMED,

    // 사용자 취소 또는 결제 실패로 취소된 상태
    CANCELED,

    // 결제 대기 시간이 만료된 상태
    EXPIRED,

    // 체크아웃까지 완료된 상태
    COMPLETED
}
