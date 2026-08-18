package com.roompick.domain.specialOffers.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;

import jakarta.persistence.LockModeType;

public interface SpecialOfferRepository extends JpaRepository<SpecialOffer, Long> {

    /**
     * 같은 특가에 대한 점유 요청 처리·만료·승계를 직렬화하기 위해
     * 특가 행을 비관적 쓰기 락과 함께 조회합니다.
     *
     * Kafka 컨슈머(occupy)와 만료 스케줄러(expireAndPromote), 결제
     * 성공·실패에 따른 승계가 서로 겹쳐 실행되면 락 없이는 "이미
     * 점유됐는지" 판단이 같은 트랜잭션 안에서만 유효해 경쟁 상태가
     * 생길 수 있습니다. 특가 행 자체를 critical section 기준으로
     * 삼아 이 경쟁을 막습니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT specialOffer
        FROM SpecialOffer specialOffer
        WHERE specialOffer.id = :specialOfferId
        """)
    Optional<SpecialOffer> findByIdForUpdate(
        @Param("specialOfferId") Long specialOfferId
    );

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
