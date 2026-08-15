package com.roompick.domain.specialOffers.facade;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.dto.OfferOccupyResponseDto;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.domain.specialOffers.producer.OfferOccupyEventProducer;
import com.roompick.domain.specialOffers.service.SpecialOfferService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OfferOccupyFacade {

    private final SpecialOfferService specialOfferService;
    private final OfferOccupyEventProducer offerOccupyEventProducer;

    public OfferOccupyResponseDto requestOccupy(
        Long memberId,
        Long offerId
    ) {
        SpecialOffer specialOffer = specialOfferService.findActiveById(offerId);

        LocalDateTime requestedAt = LocalDateTime.now();

        offerOccupyEventProducer.send(
            new OfferOccupyRequestEvent(
                offerId, memberId, requestedAt
            )
        );

        return new OfferOccupyResponseDto(
            specialOffer.getId(),
            requestedAt
        );
    }
}
