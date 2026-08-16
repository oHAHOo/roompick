package com.roompick.domain.waitlist.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.service.MemberService;
import com.roompick.domain.reservation.entity.Reservation;
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

        createReservationForHold(waitlist);
    }

    @Transactional(readOnly = true)
    public Waitlist findMyWaitlist(Long specialOfferId, Long memberId) {
        return waitlistRepository.findBySpecialOfferIdAndMemberId(specialOfferId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
    }

    @Transactional
    public int expireAndPromote(LocalDateTime now) {
        List<Waitlist> expiredHolds = waitlistRepository.findAllExpiredHolds(WaitlistStatus.HOLD, now);

        for (Waitlist expired : expiredHolds) {
            expired.expire();
            cancelHoldReservation(expired, now);

            Long specialOfferId = expired.getSpecialOffer().getId();

            waitlistRepository.findFirstBySpecialOfferIdAndStatusOrderByRequestedAtAsc(
                specialOfferId, WaitlistStatus.WAIT
            ).ifPresent(next -> promote(next, now));
        }

        return expiredHolds.size();
    }

    private void promote(Waitlist waitlist, LocalDateTime now) {
        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
        waitlist.promoteToHold(holdExpiresAt);

        createReservationForHold(waitlist);
    }

    private void createReservationForHold(Waitlist waitlist) {
        SpecialOffer specialOffer = waitlist.getSpecialOffer();

        Room room = roomService.findReservableRoomForUpdate(
            specialOffer.getRoom().getId(),
            specialOffer.getRoom().getMaxCapacity()
        );

        Reservation reservation = reservationService.createReservation(
            waitlist.getMember().getId(),
            room,
            specialOffer.getCheckInDate(),
            specialOffer.getCheckOutDate(),
            room.getMaxCapacity()
        );

        waitlist.attachReservation(reservation.getId());
    }

    /**
     * 만료된 HOLD가 만들어둔 결제 대기 예약을 명시적으로 취소합니다.
     *
     * Reservation.expiresAt은 HOLD TTL과 다른 값으로 독립적으로
     * 계산되므로, 시간이 지나기만 기다리면 다음 대기자의 예약 생성이
     * 겹침 검증에 막힐 수 있습니다. 그래서 승계 전에 명시적으로
     * 취소해 겹침 검증에서 제외합니다.
     */
    private void cancelHoldReservation(Waitlist expired, LocalDateTime now) {
        Long reservationId = expired.getReservationId();

        if (reservationId == null) {
            return;
        }

        Reservation reservation = reservationService.findById(reservationId);
        reservationService.cancelByPaymentFailure(
            reservation, expired.getMember().getId(), now
        );
    }
}
