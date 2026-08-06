package com.roompick.domain.accommodation.exception;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 인기 숙소 Single Flight 대기 중 요청 스레드가 중단된 경우 발생합니다.
 */
public class PopularAccommodationSingleFlightInterruptedException
    extends BusinessException {

    public PopularAccommodationSingleFlightInterruptedException(
        Throwable cause
    ) {
        super(
            ErrorCode.POPULAR_ACCOMMODATION_REQUEST_INTERRUPTED,
            cause
        );
    }
}
