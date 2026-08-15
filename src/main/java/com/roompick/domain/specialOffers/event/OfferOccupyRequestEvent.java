package com.roompick.domain.specialOffers.event;

import java.time.LocalDateTime;

public record OfferOccupyRequestEvent(
    Long offerId,
    Long memberId,
    LocalDateTime requestedAt
    ) {
}
