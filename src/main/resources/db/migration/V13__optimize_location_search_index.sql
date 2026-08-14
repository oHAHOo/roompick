/*
 * 위치 기반 숙소 검색의 Bounding Box 조회를 최적화합니다.
 *
 * 기존 (status, latitude, longitude) 인덱스는
 * 현재 위치 검색 데이터에서 status의 선택도가 낮아 제거합니다.
 *
 * Bounding Box의 핵심 범위 조건인 latitude / longitude를
 * 직접 사용할 수 있도록 좌표 중심 복합 인덱스로 변경합니다.
 */
DROP INDEX idx_accommodations_status_latitude_longitude
    ON accommodations;

CREATE INDEX idx_accommodations_latitude_longitude
    ON accommodations (
                       latitude,
                       longitude
        );
