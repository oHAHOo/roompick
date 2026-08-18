package com.roompick.domain.specialOffers.service;

import static java.time.LocalDateTime.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;
import com.roompick.domain.specialOffers.repository.SpecialOfferRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecialOfferService {

    private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final SpecialOfferRepository specialOfferRepository;
    private final Clock clock;

    @Transactional
    public SpecialOffer create(
        Room room,
        long price,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        SpecialOffer specialOffer = SpecialOffer.create(
            room, price, startsAt, endsAt, checkInDate, checkOutDate
        );
        return specialOfferRepository.save(specialOffer);
    }

    @Transactional
    public int activateDueOffers() {
        LocalDateTime now = now();

        List<SpecialOffer> targets = specialOfferRepository.findStartTargets(
            SpecialOfferStatus.SCHEDULED, now
        );

        targets.forEach(target -> target.activate(now));

        return targets.size();
    }

    @Transactional
    public int endDueOffers() {
        LocalDateTime now = now();

        List<SpecialOffer> targets =
            specialOfferRepository.findEndTargets(
                List.of(SpecialOfferStatus.SCHEDULED,
                    SpecialOfferStatus.ACTIVE),
                now
            );

        targets.forEach(target -> target.end(now));

        return targets.size();
    }

    @Transactional(readOnly = true)
    public SpecialOffer findById(Long specialOfferId) {
        return specialOfferRepository
            .findById(specialOfferId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.SPECIAL_OFFER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public SpecialOffer findActiveById(Long specialOfferId) {
        SpecialOffer specialOffer = specialOfferRepository
            .findById(specialOfferId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.SPECIAL_OFFER_NOT_FOUND));

        if (specialOffer.getStatus() != SpecialOfferStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.SPECIAL_OFFER_NOT_ACTIVE);
        }

        return specialOffer;
    }

    /**
     * 요청한 객실·기간이 ACTIVE 상태 특가와 겹치는지 확인합니다.
     *
     * 일반 예약 API가 특가 대기열(Kafka)을 우회해 같은 객실·기간을
     * 직접 예약하는 것을 막기 위해 ReservationFacade에서 호출됩니다.
     */
    @Transactional(readOnly = true)
    public boolean existsActiveOfferForRoomAndPeriod(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        return specialOfferRepository.existsActiveOverlapping(
            roomId, SpecialOfferStatus.ACTIVE, checkInDate, checkOutDate
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
            clock.instant(),
            SERVICE_ZONE_ID
        );
    }

}
