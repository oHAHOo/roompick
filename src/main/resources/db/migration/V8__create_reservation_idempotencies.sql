-- 예약 생성 요청의 멱등성 처리 정보를 저장합니다.
--
-- 동일 회원이 같은 Idempotency-Key를 사용한 요청은
-- 하나의 멱등성 처리 정보만 가질 수 있습니다.
--
-- 최초 예약 생성과 멱등성 처리 정보 저장은
-- 같은 트랜잭션에서 처리합니다.
-- 예약 생성이 실패해 트랜잭션이 롤백되면
-- 멱등성 처리 정보도 함께 롤백되어 재시도할 수 있습니다.
CREATE TABLE reservation_idempotencies
(
    reservation_idempotency_id BIGINT       NOT NULL AUTO_INCREMENT,
    member_id                  BIGINT       NOT NULL,
    idempotency_key            VARCHAR(100) CHARACTER SET utf8mb4
                                   COLLATE utf8mb4_bin NOT NULL,
    request_hash               CHAR(64)     CHARACTER SET ascii
                                   COLLATE ascii_bin NOT NULL,
    status                     VARCHAR(20)  NOT NULL,
    reservation_id             BIGINT       NULL,
    created_at                 DATETIME(6)  NOT NULL,
    updated_at                 DATETIME(6)  NOT NULL,

    CONSTRAINT pk_reservation_idempotencies
        PRIMARY KEY (reservation_idempotency_id),

    /*
     * 멱등성 키의 처리 범위는 회원 단위입니다.
     * 서로 다른 회원은 같은 키 문자열을 독립적으로 사용할 수 있습니다.
     */
    CONSTRAINT uk_reservation_idempotencies_member_key
        UNIQUE (member_id, idempotency_key),

    /*
     * 하나의 예약 생성 결과가 여러 멱등성 처리 정보에
     * 연결되지 않도록 제한합니다.
     *
     * MySQL의 UNIQUE 제약조건은 NULL을 여러 개 허용하므로
     * PROCESSING 상태의 행은 reservation_id가 없어도 됩니다.
     */
    CONSTRAINT uk_reservation_idempotencies_reservation
        UNIQUE (reservation_id),

    CONSTRAINT fk_reservation_idempotencies_member
        FOREIGN KEY (member_id)
            REFERENCES members (member_id)
            ON DELETE RESTRICT
            ON UPDATE CASCADE,

    /*
     * reservation_id는 아래 CHECK 제약조건에도 사용됩니다.
     *
     * MySQL은 CHECK에 사용된 컬럼에 ON DELETE 또는
     * ON UPDATE 참조 동작을 함께 지정하는 것을 허용하지 않습니다.
     *
     * 참조 동작을 생략하면 예약이 멱등성 처리 정보에서
     * 참조되는 동안 해당 예약의 삭제와 ID 변경이 제한됩니다.
     */
    CONSTRAINT fk_reservation_idempotencies_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservations (reservation_id),

    /*
     * 최초 요청 처리 중에는 예약 ID가 없고,
     * 처리가 완료된 후에만 생성된 예약 ID를 저장합니다.
     */
    CONSTRAINT chk_reservation_idempotencies_status
        CHECK (
            (
                status = 'PROCESSING'
                    AND reservation_id IS NULL
                )
                OR
            (
                status = 'COMPLETED'
                    AND reservation_id IS NOT NULL
                )
            )
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
