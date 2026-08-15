package com.roompick.domain.waitlist.repository;

import java.util.List;

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

    @Query("""
        SELECT waitlist
        FROM Waitlist waitlist
        WHERE waitlist.specialOffer.id = :specialOfferId
        AND waitlist.status = :status
        AND waitlist.holdExpiresAt <= :now
""")
    List<Waitlist> findExpiredHolds(
        @Param("specialOfferId") Long specialOfferId,
        @Param("status") WaitlistStatus status,
        @Param("now") java.time.LocalDateTime now
    );
}
