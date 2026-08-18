package com.roompick.domain.specialOffers.dto;

import java.time.LocalDateTime;

import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;

public record OfferOccupyStatusResponseDto(
    Long offerId,
    WaitlistStatus status,
    LocalDateTime requestedAt,
    LocalDateTime holdExpiresAt
) {
    public static OfferOccupyStatusResponseDto from(Waitlist waitlist) {
        return new OfferOccupyStatusResponseDto(
            waitlist.getSpecialOffer().getId(),
            waitlist.getStatus(),
            waitlist.getRequestedAt(),
            waitlist.getHoldExpiresAt()
        );
    }
}
