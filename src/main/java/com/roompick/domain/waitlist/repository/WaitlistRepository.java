package com.roompick.domain.waitlist.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    boolean existsBySpecialOfferIdAndStatusIn(
        Long specialOfferId,
        List<WaitlistStatus> statuses
    );

    Optional<Waitlist> findBySpecialOfferIdAndMemberId(Long specialOfferId, Long memberId);

    Optional<Waitlist> findByReservationId(Long reservationId);

    @Query("""
        SELECT waitlist
        FROM Waitlist  waitlist
        WHERE waitlist.status = :status
        AND waitlist.holdExpiresAt <= :now
        """)
    List<Waitlist> findAllExpiredHolds(
        @Param("status") WaitlistStatus status,
        @Param("now") LocalDateTime now
    );

    /**
     * 같은 특가 상품의 WAIT 중 다음 승계 대상을 조회합니다.
     *
     * requestedAt(HTTP 요청 접수 시각)이 아니라 waitlist_id(자동증가)로
     * 정렬합니다. 같은 offerId의 요청은 항상 같은 Kafka 파티션으로
     * 가고 파티션당 컨슈머가 1개뿐이라, waitlist_id 오름차순이 곧
     * 컨슈머가 실제로 처리한 순서(Kafka 오프셋 순서)와 항상 일치합니다.
     * requestedAt은 여러 요청이 같은 밀리초에 도착하면 동률이 생기고
     * 순서 결정 기준으로 쓰기에 부정확합니다.
     */
    Optional<Waitlist> findFirstBySpecialOfferIdAndStatusOrderByIdAsc(Long specialOfferId,
        WaitlistStatus status);
}
