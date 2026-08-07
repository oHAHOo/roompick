package com.roompick.domain.accommodation.exception;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 동일한 인기 숙소 조회 작업의 완료를 제한 시간 안에 기다리지 못한 경우 발생합니다.
 */
public class PopularAccommodationSingleFlightTimeoutException
    extends BusinessException {

    public PopularAccommodationSingleFlightTimeoutException(
        Throwable cause
    ) {
        super(
            ErrorCode.POPULAR_ACCOMMODATION_REQUEST_TIMEOUT,
            cause
        );
    }
}
