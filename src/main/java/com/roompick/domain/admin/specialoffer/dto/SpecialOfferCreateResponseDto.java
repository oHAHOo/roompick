package com.roompick.domain.admin.specialoffer.dto;

import java.time.LocalDateTime;

import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;

import lombok.Getter;

public record SpecialOfferCreateResponseDto(
    Long specialOfferId,
    Long roomId,
    long price,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    SpecialOfferStatus status
) {
    public static SpecialOfferCreateResponseDto from(SpecialOffer specialOffer) {
        return new SpecialOfferCreateResponseDto(
            specialOffer.getId(),
            specialOffer.getRoom().getId(),
            specialOffer.getPrice(),
            specialOffer.getStartsAt(),
            specialOffer.getEndsAt(),
            specialOffer.getStatus()

        );
    }
}
