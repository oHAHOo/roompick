package com.roompick.domain.waitlist.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.service.MemberService;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.service.SpecialOfferService;
import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;
import com.roompick.domain.waitlist.repository.WaitlistRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private static final int HOLD_DURATION_MINUTES = 5;

    private final WaitlistRepository waitlistRepository;
    private final SpecialOfferService specialOfferService;
    private final RoomService roomService;
    private final ReservationService reservationService;
    private final MemberService memberService;

    @Transactional
    public void occupy(Long specialOfferId, Long memberId, LocalDateTime requestedAt) {
        boolean alreadyRequested = waitlistRepository
            .findBySpecialOfferIdAndMemberId(specialOfferId, memberId).isPresent();

        if (alreadyRequested) {
            log.info(
                "이미 처리된 점유 요청이라 재처리를 건너뜁니다. offerId={}, memberId={}", specialOfferId, memberId
            );
            return;
        }

        boolean alreadyOccupied = waitlistRepository
            .existsBySpecialOfferIdAndStatusIn(
                specialOfferId,
                List.of(WaitlistStatus.HOLD, WaitlistStatus.CONFIRMED)
            );

        SpecialOffer specialOffer = specialOfferService.findById(specialOfferId);
        Member member = memberService.findById(memberId);

        if (alreadyOccupied) {
            waitlistRepository.save(
                Waitlist.createWait(specialOffer, member, requestedAt)
            );
            return;
        }

        LocalDateTime holdExpiresAt = requestedAt.plusMinutes(HOLD_DURATION_MINUTES);
        Waitlist waitlist = Waitlist.createHold(
            specialOffer, member, requestedAt, holdExpiresAt
        );
        waitlistRepository.save(waitlist);

        createReservationForHold(specialOffer, memberId);
    }

    @Transactional(readOnly = true)
    public Waitlist findMyWaitlist(Long specialOfferId, Long memberId) {
        return waitlistRepository.findBySpecialOfferIdAndMemberId(specialOfferId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
    }

    @Transactional
    public int expireAndPromote(LocalDateTime now) {
        List<Waitlist> expiredHolds = waitlistRepository.findAllExpiredHolds(WaitlistStatus.HOLD, now);

        for (Waitlist exprired : expiredHolds) {
            exprired.expire();

            Long specialOfferId = exprired.getSpecialOffer().getId();

            waitlistRepository.findFirstBySpecialOfferIdAndStatusOrderByRequestedAtAsc(
                specialOfferId, WaitlistStatus.WAIT
            ).ifPresent(next -> promote(next, now));
        }

        return expiredHolds.size();
    }

    private void promote(Waitlist waitlist, LocalDateTime now) {
        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
        waitlist.promoteToHold(holdExpiresAt);

        createReservationForHold(
            waitlist.getSpecialOffer(), waitlist.getMember().getId()
        );
    }

    private void createReservationForHold(SpecialOffer specialOffer, Long memberId) {
        Room room = roomService.findReservableRoomForUpdate(
            specialOffer.getRoom().getId(),
            specialOffer.getRoom().getMaxCapacity()
        );

        reservationService.createReservation(
            memberId,
            room,
            specialOffer.getCheckInDate(),
            specialOffer.getCheckOutDate(),
            room.getMaxCapacity()
        );
    }
}
