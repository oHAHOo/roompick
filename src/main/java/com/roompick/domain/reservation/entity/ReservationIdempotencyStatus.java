package com.roompick.domain.reservation.entity;

/**
 * 예약 생성 요청의 멱등성 처리 상태입니다.
 */
public enum ReservationIdempotencyStatus {

    /**
     * 최초 요청의 예약 생성 처리가 진행 중인 상태입니다.
     */
    PROCESSING,

    /**
     * 예약 생성이 완료되어 기존 성공 결과를
     * 재사용할 수 있는 상태입니다.
     */
    COMPLETED
}
