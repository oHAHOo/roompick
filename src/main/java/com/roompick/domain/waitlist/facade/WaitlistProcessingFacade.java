package com.roompick.domain.waitlist.facade;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
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
import com.roompick.domain.waitlist.service.WaitlistService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 특가 점유 요청 처리(Kafka Consumer), HOLD 만료·승계(Scheduler),
 * 결제 성공·실패에 따른 waitlist 상태 동기화(PaymentFacade,
 * ReservationFacade)를 조율합니다.
 *
 * WaitlistService, SpecialOfferService, RoomService, ReservationService,
 * MemberService 등 여러 도메인의 조율이 필요한 유스케이스를 이 Facade가
 * 담당하고, WaitlistService는 waitlist 데이터 접근에만 집중합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistProcessingFacade {

    private static final int HOLD_DURATION_MINUTES = 5;

    private final WaitlistService waitlistService;
    private final SpecialOfferService specialOfferService;
    private final RoomService roomService;
    private final ReservationService reservationService;
    private final MemberService memberService;
    private final Clock clock;

    /**
     * Kafka 컨슈머가 점유 요청 이벤트를 처리할 때 호출합니다.
     *
     * 이미 처리된 요청(재전달)이면 건너뛰고, 이미 HOLD/CONFIRMED가
     * 있으면 WAIT로, 없으면 HOLD로 등록한 뒤 예약을 생성합니다.
     */
    @Transactional
    public void occupy(Long specialOfferId, Long memberId, LocalDateTime requestedAt) {
        if (waitlistService.existsRequested(specialOfferId, memberId)) {
            log.info(
                "이미 처리된 점유 요청이라 재처리를 건너뜁니다. offerId={}, memberId={}",
                specialOfferId, memberId
            );
            return;
        }

        boolean alreadyOccupied = waitlistService.existsOccupied(specialOfferId);

        SpecialOffer specialOffer = specialOfferService.findById(specialOfferId);
        Member member = memberService.findById(memberId);

        if (alreadyOccupied) {
            waitlistService.saveWait(specialOffer, member, requestedAt);
            return;
        }

        /*
         * requestedAt은 HTTP 요청이 접수된 시각(기록·표시용)이고,
         * holdExpiresAt은 컨슈머가 실제로 HOLD를 부여하는 이 시점부터
         * HOLD_DURATION_MINUTES를 계산해야 한다. Kafka 컨슈머 랙이 크면
         * requestedAt 기준으로는 사용자가 실제로 받는 결제 대기 시간이
         * 부당하게 줄어든다.
         */
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime holdExpiresAt = now.plusMinutes(HOLD_DURATION_MINUTES);
        Waitlist waitlist = waitlistService.saveHold(specialOffer, member, requestedAt, holdExpiresAt);

        createReservationForHold(waitlist);
    }

    /**
     * 스케줄러가 주기적으로 호출합니다.
     *
     * TTL이 지난 HOLD를 EXPIRED로 전환하고, 만들어뒀던 예약을 취소한
     * 뒤 같은 특가의 다음 대기자를 승격합니다.
     */
    @Transactional
    public int expireAndPromote(LocalDateTime now) {
        List<Waitlist> expiredHolds = waitlistService.findAllExpiredHolds(now);

        for (Waitlist expired : expiredHolds) {
            expired.expire();
            cancelHoldReservation(expired, now);
            promoteNextWaiter(expired.getSpecialOffer().getId(), now);
        }

        return expiredHolds.size();
    }

    /**
     * 결제 성공 시 HOLD 상태의 waitlist를 CONFIRMED로 전환합니다.
     *
     * 해당 예약이 특가 대기열을 거치지 않은 일반 예약이면
     * 연결된 waitlist가 없으므로 아무 일도 하지 않습니다.
     */
    @Transactional
    public void confirmByReservationId(Long reservationId) {
        waitlistService.findByReservationId(reservationId)
            .ifPresent(Waitlist::confirm);
    }

    /**
     * 결제 실패(또는 사용자 취소)로 예약이 취소됐을 때
     * HOLD 상태의 waitlist를 EXPIRED로 전환하고 다음 대기자를 승격합니다.
     *
     * 예약 취소 자체는 호출부(PaymentFacade/ReservationFacade)가 이미
     * 처리했으므로 여기서는 waitlist 상태 동기화와 승계만 담당합니다.
     */
    @Transactional
    public void expireByReservationIdAndPromoteNext(Long reservationId, LocalDateTime now) {
        waitlistService.findByReservationId(reservationId).ifPresent(waitlist -> {
            waitlist.expire();
            promoteNextWaiter(waitlist.getSpecialOffer().getId(), now);
        });
    }

    private void promoteNextWaiter(Long specialOfferId, LocalDateTime now) {
        waitlistService.findNextWaiter(specialOfferId)
            .ifPresent(next -> promote(next, now));
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
            room.getMaxCapacity(),
            specialOffer.getPrice()
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
