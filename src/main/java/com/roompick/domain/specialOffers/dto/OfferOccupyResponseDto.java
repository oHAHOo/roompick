package com.roompick.domain.specialOffers.dto;

import java.time.LocalDateTime;

public record OfferOccupyResponseDto(
    Long offerId,
    LocalDateTime requestedAt
) {
}
