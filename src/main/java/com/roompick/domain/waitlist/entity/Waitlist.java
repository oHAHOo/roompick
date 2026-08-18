package com.roompick.domain.waitlist.entity;

import java.time.LocalDateTime;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.global.common.BaseTimeEntity;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "waitlists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waitlist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "waitlist_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "special_offer_id", nullable = false)
    private SpecialOffer specialOffer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitlistStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "reservation_id")
    private Long reservationId;

    private Waitlist(SpecialOffer specialOffer, Member member, WaitlistStatus status, LocalDateTime requestedAt,
        LocalDateTime holdExpiresAt) {
        this.specialOffer = specialOffer;
        this.member = member;
        this.status = status;
        this.requestedAt = requestedAt;
        this.holdExpiresAt = holdExpiresAt;
    }

    /**
     * HOLD 등록 시 실제로 생성된 결제 대기 예약의 ID를 연결합니다.
     *
     * HOLD가 만료될 때 이 예약을 명시적으로 취소해야
     * 다음 대기자에게 승계할 수 있습니다.
     */
    public void attachReservation(Long reservationId) {
        validateHoldStatus();
        this.reservationId = reservationId;
    }

    public static Waitlist createHold(SpecialOffer specialOffer, Member member,
        LocalDateTime requestedAt, LocalDateTime holdExpiresAt) {
        return new Waitlist(specialOffer, member, WaitlistStatus.HOLD, requestedAt, holdExpiresAt);
    }

    public static Waitlist createWait(SpecialOffer specialOffer, Member member, LocalDateTime requestedAt) {
        return new Waitlist(specialOffer, member, WaitlistStatus.WAIT, requestedAt, null);
    }

    public void promoteToHold(LocalDateTime holdExpiresAt) {
        validateWaitStatus();
        this.status = WaitlistStatus.HOLD;
        this.holdExpiresAt = holdExpiresAt;
    }

    public void expire() {
        validateHoldStatus();
        this.status = WaitlistStatus.EXPIRED;
    }

    public void confirm() {
        validateHoldStatus();
        this.status = WaitlistStatus.CONFIRMED;
    }

    private void validateWaitStatus() {
        if (status != WaitlistStatus.WAIT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateHoldStatus() {
        if (status != WaitlistStatus.HOLD) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
