package com.roompick.domain.reservation.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roompick.domain.reservation.entity.ReservationIdempotency;

import jakarta.persistence.LockModeType;

/**
 * 예약 생성 요청의 멱등성 처리 정보를 저장하고 조회합니다.
 */
public interface ReservationIdempotencyRepository
    extends JpaRepository<
    ReservationIdempotency,
    Long
    > {

    /**
     * 회원 ID와 멱등성 키 조합이 존재하지 않을 때만
     * PROCESSING 상태의 처리 정보를 생성합니다.
     *
     * 동일 키의 동시 요청이 전달되면 MySQL이
     * Unique Constraint를 기준으로 요청을 대기시킵니다.
     *
     * 먼저 처리한 트랜잭션이 커밋되면 후속 요청은
     * 기존 행을 유지하고, 먼저 처리한 트랜잭션이
     * 롤백되면 후속 요청이 새로운 행을 생성합니다.
     *
     * 예약 생성 트랜잭션에서 이미 관리 중인 Entity가
     * 분리되지 않도록 영속성 컨텍스트는 초기화하지 않습니다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO reservation_idempotencies
            (
                member_id,
                idempotency_key,
                request_hash,
                status,
                reservation_id,
                created_at,
                updated_at
            )
            VALUES
            (
                :memberId,
                :idempotencyKey,
                :requestHash,
                'PROCESSING',
                NULL,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                reservation_idempotency_id =
                    reservation_idempotency_id
            """,
        nativeQuery = true
    )
    int insertProcessingIfAbsent(
        @Param("memberId")
        Long memberId,

        @Param("idempotencyKey")
        String idempotencyKey,

        @Param("requestHash")
        String requestHash
    );

    /**
     * 회원 ID와 멱등성 키로 처리 정보를 조회하면서
     * 비관적 쓰기 락을 획득합니다.
     *
     * 동일 멱등성 키를 사용하는 요청은 이 행 잠금을
     * 기준으로 순차적으로 처리됩니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT idempotency
        FROM ReservationIdempotency idempotency
        WHERE idempotency.member.id = :memberId
          AND idempotency.idempotencyKey =
              :idempotencyKey
        """
    )
    Optional<ReservationIdempotency>
    findByMemberIdAndIdempotencyKeyForUpdate(
        @Param("memberId")
        Long memberId,

        @Param("idempotencyKey")
        String idempotencyKey
    );
}
