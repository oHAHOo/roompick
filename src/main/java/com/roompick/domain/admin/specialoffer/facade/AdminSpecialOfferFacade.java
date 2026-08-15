package com.roompick.domain.admin.specialoffer.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.admin.specialoffer.dto.SpecialOfferCreateRequestDto;
import com.roompick.domain.admin.specialoffer.dto.SpecialOfferCreateResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.service.SpecialOfferService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminSpecialOfferFacade {

    private final RoomService roomService;
    private final SpecialOfferService specialOfferService;

    public SpecialOfferCreateResponseDto create(Long accommodationId, Long roomId,
        SpecialOfferCreateRequestDto request) {
        Room room =roomService.findByIdAndAccommodationIdForSpecialOfferUpdate(
            accommodationId, roomId
        );

        SpecialOffer specialOffer = specialOfferService.create(
            room,
            request.price(),
            request.startsAt(),
            request.endsAt(),
            request.checkInDate(),
            request.checkOutDate()
        );
        return SpecialOfferCreateResponseDto.from(specialOffer);
    }


}
