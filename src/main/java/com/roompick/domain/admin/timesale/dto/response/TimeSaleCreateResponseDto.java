package com.roompick.domain.admin.timesale.dto.response;

import java.time.LocalDateTime;

import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.entity.TimeSaleStatus;

/**
 * 관리자 타임세일 등록 결과입니다.
 */
public record TimeSaleCreateResponseDto(

    Long timeSaleId,

    Long accommodationId,

    Long roomId,

    int discountRate,

    LocalDateTime startAt,

    LocalDateTime endAt,

    TimeSaleStatus status

) {

    /**
     * 저장된 TimeSale 엔티티를 등록 응답으로 변환합니다.
     */
    public static TimeSaleCreateResponseDto from(
        TimeSale timeSale
    ) {
        Long roomId = timeSale.getRoom() == null
            ? null
            : timeSale.getRoom().getId();

        return new TimeSaleCreateResponseDto(
            timeSale.getId(),
            timeSale.getAccommodation().getId(),
            roomId,
            timeSale.getDiscountRate(),
            timeSale.getStartAt(),
            timeSale.getEndAt(),
            timeSale.getStatus()
        );
    }
}
