#!/usr/bin/env bash

# RoomPick 위치 기반 숙소 검색 성능 테스트용 데이터 설정 스크립트입니다.
#
# "위치 성능 숙소"로 식별되는 데이터만 교체하며
# 기존 일반 숙소와 이전 성능 테스트 데이터는 삭제하지 않습니다.
#
# MySQL과 Elasticsearch 성능 비교에서 동일한 데이터 집합을
# 사용할 수 있도록 좌표를 결정적인 규칙으로 생성합니다.

set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-roompick-mysql}"
DATABASE_NAME="${DATABASE_NAME:-roompick}"
DATA_COUNT="${DATA_COUNT:-50000}"

echo "[1/3] MySQL 컨테이너 상태 확인"

if ! docker inspect "$MYSQL_CONTAINER" > /dev/null 2>&1; then
    echo "MySQL 컨테이너를 찾을 수 없습니다: $MYSQL_CONTAINER"
    exit 1
fi

if [ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER")" != "true" ]; then
    echo "MySQL 컨테이너가 실행 중이 아닙니다."
    exit 1
fi

if ! docker exec "$MYSQL_CONTAINER" sh -c '
if [ -z "${MYSQL_USER:-}" ] || [ -z "${MYSQL_PASSWORD:-}" ]; then
    echo "MySQL 컨테이너에 MYSQL_USER와 MYSQL_PASSWORD가 모두 설정되어 있어야 합니다." >&2
    exit 1
fi
'; then
    exit 1
fi

echo "[2/3] 위치 검색 성능 테스트 숙소 ${DATA_COUNT}개 생성"

docker exec \
    -i \
    -e PERFORMANCE_DATABASE="$DATABASE_NAME" \
    -e PERFORMANCE_DATA_COUNT="$DATA_COUNT" \
    "$MYSQL_CONTAINER" \
    sh -c '
mysql \
    -u"$MYSQL_USER" \
    -p"$MYSQL_PASSWORD" \
    "$PERFORMANCE_DATABASE" \
    --default-character-set=utf8mb4 \
    --init-command="SET @performance_data_count = ${PERFORMANCE_DATA_COUNT};"
' <<'SQL'
SET NAMES utf8mb4;

SET SESSION cte_max_recursion_depth = 60000;

START TRANSACTION;

DELETE FROM accommodations
WHERE name REGEXP '^(룸픽 )?위치 성능 숙소 [0-9]{5}$';

INSERT INTO accommodations (
    name,
    address,
    description,
    check_in_time,
    check_out_time,
    status,
    latitude,
    longitude,
    created_at,
    updated_at
)
WITH RECURSIVE sequence AS (
    SELECT 1 AS number

    UNION ALL

    SELECT number + 1
    FROM sequence
    WHERE number < @performance_data_count
)
SELECT
    CASE
        WHEN MOD(number, 5) = 0
            THEN CONCAT(
                '룸픽 위치 성능 숙소 ',
                LPAD(number, 5, '0')
            )
        ELSE CONCAT(
            '위치 성능 숙소 ',
            LPAD(number, 5, '0')
        )
    END,
    CONCAT(
        '서울특별시 성능구 위치로 ',
        number
    ),
    CONCAT(
        '위치 기반 숙소 검색 성능 테스트 데이터 ',
        number
    ),
    '15:00:00',
    '11:00:00',
    'ACTIVE',

    /*
     * 위도:
     * 37.450000 ~ 약 37.649200 범위에 분포합니다.
     *
     * 250개의 서로 다른 위도 위치를 사용합니다.
     */
    CAST(
        37.450000
        + MOD(number - 1, 250) * 0.000800
        AS DECIMAL(9, 6)
    ),

    /*
     * 경도:
     * 126.800000 ~ 약 127.198000 범위에 분포합니다.
     *
     * 200개의 서로 다른 경도 위치를 사용합니다.
     *
     * 위도 250개 × 경도 200개로
     * 50,000개의 서로 다른 좌표가 생성됩니다.
     */
    CAST(
        126.800000
        + FLOOR((number - 1) / 250) * 0.002000
        AS DECIMAL(10, 6)
    ),

    NOW(),
    NOW()
FROM sequence;

COMMIT;

ANALYZE TABLE accommodations;
SQL

echo "[3/3] 생성 결과 확인"

docker exec \
    -i \
    -e PERFORMANCE_DATABASE="$DATABASE_NAME" \
    "$MYSQL_CONTAINER" \
    sh -c '
mysql \
    -u"$MYSQL_USER" \
    -p"$MYSQL_PASSWORD" \
    "$PERFORMANCE_DATABASE" \
    --default-character-set=utf8mb4
' <<'SQL'
SELECT
    COUNT(*) AS total_count,
    SUM(name LIKE '룸픽 위치 성능 숙소 %') AS keyword_match_count,
    MIN(latitude) AS min_latitude,
    MAX(latitude) AS max_latitude,
    MIN(longitude) AS min_longitude,
    MAX(longitude) AS max_longitude
FROM accommodations
WHERE name REGEXP '^(룸픽 )?위치 성능 숙소 [0-9]{5}$';
SQL

echo
echo "위치 검색 성능 테스트 데이터 설정 완료"
