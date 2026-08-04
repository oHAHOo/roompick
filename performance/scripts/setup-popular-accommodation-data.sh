#!/usr/bin/env bash

# RoomPick 로컬 성능 테스트 전용 데이터 설정 스크립트입니다.
# 정규식으로 식별되는 "성능 테스트 숙소 NN" 데이터만 교체하며
# 일반 숙소 데이터는 삭제하지 않습니다.

set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-roompick-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-roompick-redis}"
DATABASE_NAME="${DATABASE_NAME:-roompick}"

echo "[1/4] 컨테이너 상태 확인"

if ! docker inspect "$MYSQL_CONTAINER" > /dev/null 2>&1; then
  echo "MySQL 컨테이너를 찾을 수 없습니다: $MYSQL_CONTAINER"
  exit 1
fi

if ! docker inspect "$REDIS_CONTAINER" > /dev/null 2>&1; then
  echo "Redis 컨테이너를 찾을 수 없습니다: $REDIS_CONTAINER"
  exit 1
fi

if [ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER")" != "true" ]; then
  echo "MySQL 컨테이너가 실행 중이 아닙니다."
  exit 1
fi

if [ "$(docker inspect -f '{{.State.Running}}' "$REDIS_CONTAINER")" != "true" ]; then
  echo "Redis 컨테이너가 실행 중이 아닙니다."
  exit 1
fi

echo "[2/4] 성능 테스트 숙소 20개 생성"

docker exec \
  -i \
  -e PERFORMANCE_DATABASE="$DATABASE_NAME" \
  "$MYSQL_CONTAINER" \
  sh -c '
    mysql \
      -u"$MYSQL_USER" \
      -p"$MYSQL_PASSWORD" \
      "$PERFORMANCE_DATABASE"
  ' <<'SQL'
SET NAMES utf8mb4;

START TRANSACTION;

DELETE FROM accommodations
WHERE name REGEXP '^성능 테스트 숙소 [0-9]{2}$';

INSERT INTO accommodations (
    name,
    address,
    description,
    check_in_time,
    check_out_time,
    status,
    created_at,
    updated_at
) VALUES
    ('성능 테스트 숙소 01', '서울 성능로 01', '인기 숙소 성능 테스트 데이터 01', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 02', '서울 성능로 02', '인기 숙소 성능 테스트 데이터 02', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 03', '서울 성능로 03', '인기 숙소 성능 테스트 데이터 03', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 04', '서울 성능로 04', '인기 숙소 성능 테스트 데이터 04', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 05', '서울 성능로 05', '인기 숙소 성능 테스트 데이터 05', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 06', '서울 성능로 06', '인기 숙소 성능 테스트 데이터 06', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 07', '서울 성능로 07', '인기 숙소 성능 테스트 데이터 07', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 08', '서울 성능로 08', '인기 숙소 성능 테스트 데이터 08', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 09', '서울 성능로 09', '인기 숙소 성능 테스트 데이터 09', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 10', '서울 성능로 10', '인기 숙소 성능 테스트 데이터 10', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 11', '서울 성능로 11', '인기 숙소 성능 테스트 데이터 11', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 12', '서울 성능로 12', '인기 숙소 성능 테스트 데이터 12', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 13', '서울 성능로 13', '인기 숙소 성능 테스트 데이터 13', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 14', '서울 성능로 14', '인기 숙소 성능 테스트 데이터 14', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 15', '서울 성능로 15', '인기 숙소 성능 테스트 데이터 15', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 16', '서울 성능로 16', '인기 숙소 성능 테스트 데이터 16', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 17', '서울 성능로 17', '인기 숙소 성능 테스트 데이터 17', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 18', '서울 성능로 18', '인기 숙소 성능 테스트 데이터 18', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 19', '서울 성능로 19', '인기 숙소 성능 테스트 데이터 19', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW()),
    ('성능 테스트 숙소 20', '서울 성능로 20', '인기 숙소 성능 테스트 데이터 20', '15:00:00', '11:00:00', 'ACTIVE', NOW(), NOW());

COMMIT;
SQL

ACCOMMODATION_IDS=$(
  docker exec \
    -i \
    -e PERFORMANCE_DATABASE="$DATABASE_NAME" \
    "$MYSQL_CONTAINER" \
    sh -c '
      mysql \
        -u"$MYSQL_USER" \
        -p"$MYSQL_PASSWORD" \
        "$PERFORMANCE_DATABASE" \
        --default-character-set=utf8mb4 \
        -N
    ' <<'SQL'
SELECT accommodation_id
FROM accommodations
WHERE name REGEXP '^성능 테스트 숙소 [0-9]{2}$'
ORDER BY name;
SQL

)

ACCOMMODATION_COUNT=$(
  printf '%s\n' "$ACCOMMODATION_IDS" |
    sed '/^[[:space:]]*$/d' |
    wc -l |
    tr -d ' '
)

if [ "$ACCOMMODATION_COUNT" -ne 20 ]; then
  echo "숙소 데이터가 20개 생성되지 않았습니다: $ACCOMMODATION_COUNT"
  exit 1
fi

echo "[3/4] DAILY·WEEKLY Redis 랭킹 생성"

DATE_VALUES=$(
  python3 - <<'PY'
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

today = datetime.now(ZoneInfo("Asia/Seoul")).date()
monday = today - timedelta(days=today.weekday())

print(today.isoformat(), monday.isoformat())
PY
)

DAILY_DATE="${DATE_VALUES%% *}"
WEEKLY_DATE="${DATE_VALUES##* }"

DAILY_KEY="roompick:popular:accommodations:daily:${DAILY_DATE}"
WEEKLY_KEY="roompick:popular:accommodations:weekly:${WEEKLY_DATE}"

docker exec "$REDIS_CONTAINER" redis-cli DEL \
  "$DAILY_KEY" \
  "$WEEKLY_KEY" \
  > /dev/null

docker exec "$REDIS_CONTAINER" sh -c '
  redis-cli --scan --pattern "popularAccommodations::*" |
  while IFS= read -r key; do
    redis-cli DEL "$key" > /dev/null
  done
'

RANK=1

while IFS= read -r ACCOMMODATION_ID; do
  if [ -z "$ACCOMMODATION_ID" ]; then
    continue
  fi

  DAILY_SCORE=$((210 - RANK * 10))
  WEEKLY_SCORE=$((RANK * 10))

  docker exec "$REDIS_CONTAINER" redis-cli ZADD \
    "$DAILY_KEY" \
    "$DAILY_SCORE" \
    "$ACCOMMODATION_ID" \
    > /dev/null

  docker exec "$REDIS_CONTAINER" redis-cli ZADD \
    "$WEEKLY_KEY" \
    "$WEEKLY_SCORE" \
    "$ACCOMMODATION_ID" \
    > /dev/null

  RANK=$((RANK + 1))
done <<IDS
$ACCOMMODATION_IDS
IDS

echo "[4/4] 설정 결과 확인"

echo
echo "생성된 숙소 수:"
echo "$ACCOMMODATION_COUNT"

echo
echo "DAILY 랭킹 키:"
echo "$DAILY_KEY"

docker exec "$REDIS_CONTAINER" redis-cli ZREVRANGE \
  "$DAILY_KEY" \
  0 \
  4 \
  WITHSCORES

echo
echo "WEEKLY 랭킹 키:"
echo "$WEEKLY_KEY"

docker exec "$REDIS_CONTAINER" redis-cli ZREVRANGE \
  "$WEEKLY_KEY" \
  0 \
  4 \
  WITHSCORES

echo
echo "성능 테스트 데이터 설정 완료"
