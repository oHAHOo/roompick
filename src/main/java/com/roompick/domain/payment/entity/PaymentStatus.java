package com.roompick.domain.payment.entity;

/**
 * 결제의 처리 상태를 나타냅니다.
 */
public enum PaymentStatus {

    // 결제 정보가 생성되고 승인을 기다리는 상태
    READY,

    // 결제가 정상적으로 승인된 상태
    PAID,

    // 결제 승인이 실패한 상태
    FAILED,

    // 결제 금액이 전액 환불된 상태
    REFUNDED
}
