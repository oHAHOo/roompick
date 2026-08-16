-- HOLD 상태의 대기열 항목이 생성한 예약을 추적하기 위한 컬럼입니다.
-- HOLD가 만료되면 이 예약을 명시적으로 취소해 다음 대기자에게
-- 승계할 때 겹침 검증이 막히지 않도록 합니다.
ALTER TABLE waitlists
    ADD COLUMN reservation_id BIGINT NULL AFTER member_id;

ALTER TABLE waitlists
    ADD CONSTRAINT fk_waitlists_reservation
        FOREIGN KEY (reservation_id)
            REFERENCES reservations (reservation_id);
