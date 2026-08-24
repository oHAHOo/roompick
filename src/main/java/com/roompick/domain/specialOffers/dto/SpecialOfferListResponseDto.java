package com.roompick.domain.specialOffers.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 메인 화면 등에서 판매 중인 특가를 둘러볼 때 반환하는 요약 정보입니다.
 */
public record SpecialOfferListResponseDto(

    Long specialOfferId,

    Long accommodationId,

    String accommodationName,

    Long roomId,

    String roomName,

    long price,

    LocalDate checkInDate,

    LocalDate checkOutDate,

    LocalDateTime endsAt

) {
}
