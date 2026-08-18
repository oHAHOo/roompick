package com.roompick.domain.specialOffers.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;

public interface SpecialOfferRepository extends JpaRepository<SpecialOffer, Long> {

    /**
     * 요청한 객실·기간이 ACTIVE 상태 특가의 숙박 기간과 겹치는지 확인합니다.
     *
     * 일반 예약 API가 특가 대기열(Kafka)을 우회해 같은 객실·기간을
     * 직접 예약하는 것을 막기 위한 용도입니다.
     */
    @Query("""
        SELECT CASE WHEN COUNT(specialOffer) > 0 THEN true ELSE false END
        FROM SpecialOffer specialOffer
        WHERE specialOffer.room.id = :roomId
          AND specialOffer.status = :status
          AND specialOffer.checkInDate < :checkOutDate
          AND specialOffer.checkOutDate > :checkInDate
        """)
    boolean existsActiveOverlapping(
        @Param("roomId") Long roomId,
        @Param("status") SpecialOfferStatus status,
        @Param("checkInDate") LocalDate checkInDate,
        @Param("checkOutDate") LocalDate checkOutDate
    );

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
