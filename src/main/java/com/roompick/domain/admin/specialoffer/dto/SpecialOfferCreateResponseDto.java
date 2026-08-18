package com.roompick.domain.admin.specialoffer.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;

public record SpecialOfferCreateResponseDto(
    Long specialOfferId,
    Long roomId,
    long price,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    SpecialOfferStatus status
) {
    public static SpecialOfferCreateResponseDto from(SpecialOffer specialOffer) {
        return new SpecialOfferCreateResponseDto(
            specialOffer.getId(),
            specialOffer.getRoom().getId(),
            specialOffer.getPrice(),
            specialOffer.getStartsAt(),
            specialOffer.getEndsAt(),
            specialOffer.getCheckInDate(),
            specialOffer.getCheckOutDate(),
            specialOffer.getStatus()
        );
    }
}
