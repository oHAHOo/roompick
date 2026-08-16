package com.roompick.domain.specialOffers.facade;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.dto.OfferOccupyResponseDto;
import com.roompick.domain.specialOffers.dto.OfferOccupyStatusResponseDto;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.domain.specialOffers.producer.OfferOccupyEventProducer;
import com.roompick.domain.specialOffers.service.SpecialOfferService;
import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.service.WaitlistService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OfferOccupyFacade {

    private final SpecialOfferService specialOfferService;
    private final OfferOccupyEventProducer offerOccupyEventProducer;
    private final WaitlistService waitlistService;

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

    public OfferOccupyStatusResponseDto getMyOccupyStatus(Long memberId, Long offerId) {
        Waitlist waitlist = waitlistService.findMyWaitlist(offerId, memberId);
        return OfferOccupyStatusResponseDto.from(waitlist);
    }
}
