package com.roompick.domain.timesale.entity;

/**
 * 타임세일의 처리 상태입니다.
 *
 * 실제 할인 적용 여부는 상태만으로 판단하지 않고
 * 시작·종료 시각도 함께 확인합니다.
 */
public enum TimeSaleStatus {

    /**
     * 타임세일 시작 전 상태입니다.
     */
    SCHEDULED,

    /**
     * 타임세일 진행 중 상태입니다.
     */
    ACTIVE,

    /**
     * 타임세일이 종료된 상태입니다.
     */
    ENDED
}
