/*
 * 위치 기반 숙소 검색의 Bounding Box 선필터링을 위한 복합 인덱스입니다.
 *
 * 검색 쿼리는 ACTIVE 상태를 먼저 확인하고,
 * 이후 latitude / longitude 범위로 후보 숙소를 줄입니다.
 */
CREATE INDEX idx_accommodations_status_latitude_longitude
    ON accommodations (
                       status,
                       latitude,
                       longitude
        );
