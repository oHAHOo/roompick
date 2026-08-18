package com.roompick.domain.waitlist.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;
import com.roompick.domain.waitlist.repository.WaitlistRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * waitlist 데이터 접근에만 집중합니다.
 *
 * 여러 도메인(SpecialOffer/Room/Reservation/Member)을 조율해야 하는
 * 유스케이스(점유 요청 처리, 만료·승계, 결제 성공·실패 동기화)는
 * {@link com.roompick.domain.waitlist.facade.WaitlistProcessingFacade}가
 * 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    @Transactional(readOnly = true)
    public boolean existsRequested(Long specialOfferId, Long memberId) {
        return waitlistRepository
            .findBySpecialOfferIdAndMemberId(specialOfferId, memberId)
            .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean existsOccupied(Long specialOfferId) {
        return waitlistRepository.existsBySpecialOfferIdAndStatusIn(
            specialOfferId,
            List.of(WaitlistStatus.HOLD, WaitlistStatus.CONFIRMED)
        );
    }

    @Transactional
    public Waitlist saveWait(SpecialOffer specialOffer, Member member, LocalDateTime requestedAt) {
        return waitlistRepository.save(
            Waitlist.createWait(specialOffer, member, requestedAt)
        );
    }

    @Transactional
    public Waitlist saveHold(
        SpecialOffer specialOffer,
        Member member,
        LocalDateTime requestedAt,
        LocalDateTime holdExpiresAt
    ) {
        return waitlistRepository.save(
            Waitlist.createHold(specialOffer, member, requestedAt, holdExpiresAt)
        );
    }

    @Transactional(readOnly = true)
    public Waitlist findMyWaitlist(Long specialOfferId, Long memberId) {
        return waitlistRepository.findBySpecialOfferIdAndMemberId(specialOfferId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<Waitlist> findByReservationId(Long reservationId) {
        return waitlistRepository.findByReservationId(reservationId);
    }

    @Transactional(readOnly = true)
    public List<Waitlist> findAllExpiredHolds(LocalDateTime now) {
        return waitlistRepository.findAllExpiredHolds(WaitlistStatus.HOLD, now);
    }

    @Transactional(readOnly = true)
    public Optional<Waitlist> findNextWaiter(Long specialOfferId) {
        return waitlistRepository.findFirstBySpecialOfferIdAndStatusOrderByIdAsc(
            specialOfferId, WaitlistStatus.WAIT
        );
    }
}
