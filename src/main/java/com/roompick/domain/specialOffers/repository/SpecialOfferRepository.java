package com.roompick.domain.specialOffers.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;

public interface SpecialOfferRepository extends JpaRepository<SpecialOffer, Long> {

    @Query("""
        SELECT specialOffer
        FROM SpecialOffer specialOffer
        WHERE specialOffer.status = :status
        AND specialOffer.startsAt <= :now
        AND specialOffer.endsAt > :now
        """)
    List<SpecialOffer> findStartTargets(
        @Param("status") SpecialOfferStatus status,
        @Param("now") LocalDateTime now
    );

    @Query("""
        SELECT specialOffer
        FROM SpecialOffer specialOffer
        WHERE specialOffer.status IN :statuses
        AND specialOffer.endsAt <= :now
        """)
    List<SpecialOffer> findEndTargets(
        @Param("statuses") List<SpecialOfferStatus> statuses,
        @Param("now") LocalDateTime now
    );

}
