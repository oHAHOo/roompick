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

    Optional<Waitlist> findFirstBySpecialOfferIdAndStatusOrderByRequestedAtAsc(Long specialOfferId,
        WaitlistStatus status);
}
