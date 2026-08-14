package com.roompick.domain.timesale.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.entity.TimeSaleStatus;

public interface TimeSaleRepository
    extends JpaRepository<TimeSale, Long> {

    /**
     * 시작 시각에 도달했지만 아직 SCHEDULED 상태인
     * 타임세일을 조회합니다.
     */
    @Query("""
        SELECT timeSale
        FROM TimeSale timeSale
        WHERE timeSale.status = :status
          AND timeSale.startAt <= :now
          AND timeSale.endAt > :now
        """)
    List<TimeSale> findStartTargets(
        @Param("status") TimeSaleStatus status,
        @Param("now") LocalDateTime now
    );

    /**
     * 종료 시각에 도달했지만 아직 ENDED 상태로
     * 변경되지 않은 타임세일을 조회합니다.
     */
    @Query("""
        SELECT timeSale
        FROM TimeSale timeSale
        WHERE timeSale.status IN :statuses
          AND timeSale.endAt <= :now
        """)
    List<TimeSale> findEndTargets(
        @Param("statuses")
        List<TimeSaleStatus> statuses,
        @Param("now") LocalDateTime now
    );

    /**
     * 현재 시각에 적용 가능한 객실 전용 타임세일을
     * 조회합니다.
     *
     * 여러 건이 존재한다면 할인율이 가장 높은
     * 타임세일을 우선합니다.
     */
    @Query("""
        SELECT timeSale
        FROM TimeSale timeSale
        WHERE timeSale.room.id = :roomId
          AND timeSale.status
              <> com.roompick.domain.timesale.entity.TimeSaleStatus.ENDED
          AND timeSale.startAt <= :now
          AND timeSale.endAt > :now
        ORDER BY
            timeSale.discountRate DESC,
            timeSale.id ASC
        """)
    List<TimeSale> findApplicableRoomSales(
        @Param("roomId") Long roomId,
        @Param("now") LocalDateTime now
    );

    /**
     * 객실 목록에 포함된 모든 객실의 현재 적용 가능한
     * 객실 전용 타임세일을 한 번에 조회합니다.
     */
    @Query("""
        SELECT timeSale
        FROM TimeSale timeSale
        JOIN FETCH timeSale.room room
        WHERE room.id IN :roomIds
          AND timeSale.status
              <> com.roompick.domain.timesale.entity.TimeSaleStatus.ENDED
          AND timeSale.startAt <= :now
          AND timeSale.endAt > :now
        ORDER BY
            room.id ASC,
            timeSale.discountRate DESC,
            timeSale.id ASC
        """)
    List<TimeSale> findApplicableRoomSalesByRoomIds(
        @Param("roomIds") List<Long> roomIds,
        @Param("now") LocalDateTime now
    );

    /**
     * 현재 시각에 적용 가능한 숙소 전체 타임세일을
     * 조회합니다.
     *
     * room이 null인 타임세일만 숙소 전체 할인으로
     * 처리합니다.
     */
    @Query("""
        SELECT timeSale
        FROM TimeSale timeSale
        WHERE timeSale.accommodation.id
              = :accommodationId
          AND timeSale.room IS NULL
          AND timeSale.status
              <> com.roompick.domain.timesale.entity.TimeSaleStatus.ENDED
          AND timeSale.startAt <= :now
          AND timeSale.endAt > :now
        ORDER BY
            timeSale.discountRate DESC,
            timeSale.id ASC
        """)
    List<TimeSale> findApplicableAccommodationSales(
        @Param("accommodationId")
        Long accommodationId,
        @Param("now") LocalDateTime now
    );

    /**
     * 같은 숙소를 대상으로 기간이 겹치는
     * 숙소 전체 타임세일이 존재하는지 확인합니다.
     */
    @Query("""
        SELECT CASE
            WHEN COUNT(timeSale) > 0 THEN true
            ELSE false
        END
        FROM TimeSale timeSale
        WHERE timeSale.accommodation.id
              = :accommodationId
          AND timeSale.room IS NULL
          AND timeSale.status
              <> com.roompick.domain.timesale.entity.TimeSaleStatus.ENDED
          AND timeSale.startAt < :endAt
          AND timeSale.endAt > :startAt
        """)
    boolean existsOverlappingAccommodationSale(
        @Param("accommodationId")
        Long accommodationId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );

    /**
     * 같은 객실을 대상으로 기간이 겹치는
     * 객실 타임세일이 존재하는지 확인합니다.
     */
    @Query("""
        SELECT CASE
            WHEN COUNT(timeSale) > 0 THEN true
            ELSE false
        END
        FROM TimeSale timeSale
        WHERE timeSale.room.id = :roomId
          AND timeSale.status
              <> com.roompick.domain.timesale.entity.TimeSaleStatus.ENDED
          AND timeSale.startAt < :endAt
          AND timeSale.endAt > :startAt
        """)
    boolean existsOverlappingRoomSale(
        @Param("roomId") Long roomId,
        @Param("startAt") LocalDateTime startAt,
        @Param("endAt") LocalDateTime endAt
    );
}
