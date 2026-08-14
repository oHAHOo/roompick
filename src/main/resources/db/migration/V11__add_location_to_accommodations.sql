-- 숙소 위치 기반 검색을 위해 위도와 경도를 추가합니다.
--
-- 기존 숙소 데이터에는 좌표가 없을 수 있으므로 nullable로 추가합니다.
-- 위치 검색에서는 latitude, longitude가 모두 존재하는 숙소만 대상으로 합니다.
ALTER TABLE accommodations
    ADD COLUMN latitude DECIMAL(9, 6) NULL COMMENT '숙소 위도 (-90 ~ 90)',
    ADD COLUMN longitude DECIMAL(10, 6) NULL COMMENT '숙소 경도 (-180 ~ 180)';
