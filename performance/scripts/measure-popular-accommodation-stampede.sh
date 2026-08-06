#!/usr/bin/env bash

# RoomPick 로컬 인기 숙소 Cache Stampede 재현 전용 스크립트입니다.
# 기존 성능 결과를 보호하기 위해 매 실행마다 새로운 출력 디렉터리를 사용합니다.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-roompick-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-roompick-redis}"
DATABASE_NAME="${DATABASE_NAME:-roompick}"
PERIOD="${PERIOD:-DAILY}"
LIMIT="${LIMIT:-10}"
VUS="${VUS:-10}"
RUNS="${RUNS:-3}"
SCENARIO="${SCENARIO:-single-flight-enabled}"
OUTPUT_ROOT="${OUTPUT_ROOT:-performance/results/stampede}"

if [ "$PERIOD" != "DAILY" ] && [ "$PERIOD" != "WEEKLY" ]; then
  echo "PERIOD는 DAILY 또는 WEEKLY여야 합니다: $PERIOD"
  exit 1
fi

for value_name in LIMIT VUS RUNS; do
  value="${!value_name}"
  if ! [[ "$value" =~ ^[0-9]+$ ]] || [ "$value" -lt 1 ]; then
    echo "$value_name 값은 1 이상의 정수여야 합니다: $value"
    exit 1
  fi
done

if [ "$LIMIT" -gt 20 ]; then
  echo "LIMIT 값은 20 이하여야 합니다: $LIMIT"
  exit 1
fi

if ! curl -fsS "${BASE_URL}/actuator/health" > /dev/null; then
  echo "RoomPick 애플리케이션 상태를 확인할 수 없습니다: $BASE_URL"
  exit 1
fi

for container in "$MYSQL_CONTAINER" "$REDIS_CONTAINER"; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$container")" != "true" ]; then
    echo "필수 컨테이너가 실행 중이 아닙니다: $container"
    exit 1
  fi
done

if ! docker exec "$MYSQL_CONTAINER" sh -c '
  test -n "${MYSQL_USER:-}" && test -n "${MYSQL_PASSWORD:-}"
'; then
  echo "MySQL 컨테이너에 MYSQL_USER와 MYSQL_PASSWORD가 필요합니다."
  exit 1
fi

DATE_VALUE=$(python3 - "$PERIOD" <<'PY'
import sys
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

period = sys.argv[1]
today = datetime.now(ZoneInfo("Asia/Seoul")).date()
base_date = today if period == "DAILY" else today - timedelta(days=today.weekday())
print(base_date.isoformat())
PY
)

PERIOD_LOWER=$(printf '%s' "$PERIOD" | tr '[:upper:]' '[:lower:]')
RANKING_KEY="roompick:popular:accommodations:${PERIOD_LOWER}:${DATE_VALUE}"
CACHE_KEY="popularAccommodations::${RANKING_KEY}:${LIMIT}"
TIMESTAMP=$(date '+%Y%m%d-%H%M%S')
OUTPUT_DIR="${OUTPUT_ROOT}/${TIMESTAMP}-${SCENARIO}"

if [ -e "$OUTPUT_DIR" ]; then
  echo "결과 경로가 이미 존재합니다: $OUTPUT_DIR"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

read_mysql_selects() {
  docker exec \
    -e PERFORMANCE_DATABASE="$DATABASE_NAME" \
    "$MYSQL_CONTAINER" \
    sh -c '
      mysql -N -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
        "$PERFORMANCE_DATABASE" \
        -e "SHOW GLOBAL STATUS LIKE '\''Com_select'\'';" 2>/dev/null
    ' | awk '{print $2}'
}

read_redis_ranking_calls() {
  docker exec "$REDIS_CONTAINER" redis-cli INFO commandstats |
    awk -F '[:,=]' '/^cmdstat_zrevrange:/ {print $3}' |
    tr -d '\r'
}

printf '%s\n' \
  "scenario,run,redis_ranking_calls,mysql_selects" \
  > "${OUTPUT_DIR}/query-counts.csv"

for run in $(seq 1 "$RUNS"); do
  docker exec "$REDIS_CONTAINER" redis-cli DEL "$CACHE_KEY" > /dev/null

  redis_before=$(read_redis_ranking_calls)
  redis_before="${redis_before:-0}"
  mysql_before=$(read_mysql_selects)

  BASE_URL="$BASE_URL" \
  PERIOD="$PERIOD" \
  LIMIT="$LIMIT" \
  VUS="$VUS" \
  k6 run \
    --summary-export "${OUTPUT_DIR}/run-${run}-summary.json" \
    performance/k6/popular-accommodation-stampede.js \
    > "${OUTPUT_DIR}/run-${run}.txt"

  redis_after=$(read_redis_ranking_calls)
  redis_after="${redis_after:-0}"
  mysql_after=$(read_mysql_selects)

  # 두 번째 SHOW GLOBAL STATUS 자체가 증가시킨 SELECT 1회를 제외합니다.
  measured_mysql_selects=$((mysql_after - mysql_before - 1))
  if [ "$measured_mysql_selects" -lt 0 ]; then
    measured_mysql_selects=0
  fi

  printf '%s,%s,%s,%s\n' \
    "$SCENARIO" \
    "$run" \
    "$((redis_after - redis_before))" \
    "$measured_mysql_selects" \
    >> "${OUTPUT_DIR}/query-counts.csv"
done

python3 - "$OUTPUT_DIR" "$SCENARIO" <<'PY'
import csv
import json
import pathlib
import sys

output_dir = pathlib.Path(sys.argv[1])
scenario = sys.argv[2]
rows = []

with (output_dir / "query-counts.csv").open(encoding="utf-8") as source:
    counts = list(csv.DictReader(source))

for index, count in enumerate(counts, start=1):
    with (output_dir / f"run-{index}-summary.json").open(encoding="utf-8") as source:
        summary = json.load(source)
    duration = summary["metrics"]["http_req_duration"]
    requests = summary["metrics"]["http_reqs"]
    failures = summary["metrics"]["http_req_failed"]
    rows.append({
        "scenario": scenario,
        "run": index,
        "redis_ranking_calls": int(count["redis_ranking_calls"]),
        "mysql_selects": int(count["mysql_selects"]),
        "avg_ms": duration["avg"],
        "p95_ms": duration["p(95)"],
        "p99_ms": duration["p(99)"],
        "throughput_rps": requests["rate"],
        "error_rate": failures["value"],
    })

columns = list(rows[0])
with (output_dir / "summary.csv").open("w", newline="", encoding="utf-8") as target:
    writer = csv.DictWriter(target, fieldnames=columns)
    writer.writeheader()
    writer.writerows(rows)

print(output_dir / "summary.csv")
PY

echo "Stampede 측정 결과를 저장했습니다: $OUTPUT_DIR"
