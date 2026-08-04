#!/usr/bin/env bash

# RoomPick 로컬 인기 숙소 Cold cache 성능 측정 전용 스크립트입니다.
# 매 요청 전에 해당 응답 캐시 키만 삭제하며 Redis 랭킹과 MySQL 데이터는 변경하지 않습니다.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REDIS_CONTAINER="${REDIS_CONTAINER:-roompick-redis}"
PERIOD="${PERIOD:-DAILY}"
LIMIT="${LIMIT:-5}"
ITERATIONS="${ITERATIONS:-100}"
SLEEP_SECONDS="${SLEEP_SECONDS:-0.05}"
OUTPUT_DIR="${OUTPUT_DIR:-performance/results}"

PERIOD="$(printf '%s' "$PERIOD" | tr '[:lower:]' '[:upper:]')"

if [ "$PERIOD" != "DAILY" ] && [ "$PERIOD" != "WEEKLY" ]; then
  echo "PERIOD는 DAILY 또는 WEEKLY여야 합니다: $PERIOD"
  exit 1
fi

if ! [[ "$LIMIT" =~ ^[0-9]+$ ]] || [ "$LIMIT" -lt 1 ] || [ "$LIMIT" -gt 20 ]; then
  echo "LIMIT는 1 이상 20 이하의 정수여야 합니다: $LIMIT"
  exit 1
fi

if ! [[ "$ITERATIONS" =~ ^[0-9]+$ ]] || [ "$ITERATIONS" -lt 1 ]; then
  echo "ITERATIONS는 1 이상의 정수여야 합니다: $ITERATIONS"
  exit 1
fi

DATE_VALUES=$(
  python3 - <<'PY'
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

today = datetime.now(ZoneInfo("Asia/Seoul")).date()
monday = today - timedelta(days=today.weekday())

print(today.isoformat(), monday.isoformat())
PY
)

if [ "$PERIOD" = "DAILY" ]; then
  BASE_DATE="${DATE_VALUES%% *}"
else
  BASE_DATE="${DATE_VALUES##* }"
fi

PERIOD_LOWER="$(printf '%s' "$PERIOD" | tr '[:upper:]' '[:lower:]')"
CACHE_KEY="popularAccommodations::roompick:popular:accommodations:${PERIOD_LOWER}:${BASE_DATE}:${LIMIT}"
CSV_FILE="${OUTPUT_DIR}/cold-cache-${PERIOD_LOWER}-limit${LIMIT}.csv"
SUMMARY_FILE="${OUTPUT_DIR}/cold-cache-${PERIOD_LOWER}-limit${LIMIT}-summary.txt"
API_URL="${BASE_URL}/api/v1/accommodations/popular?period=${PERIOD}&limit=${LIMIT}"

mkdir -p "$OUTPUT_DIR"

# 기존 공식 측정 결과가 덮어써지는 것을 방지합니다.
# 다시 측정해야 한다면 OUTPUT_DIR에 새로운 경로를 명시해야 합니다.
if [ -e "$CSV_FILE" ] || [ -e "$SUMMARY_FILE" ]; then
  echo "기존 결과 파일이 있어 측정을 중단합니다:"
  echo "- $CSV_FILE"
  echo "- $SUMMARY_FILE"
  echo "새로 측정하려면 OUTPUT_DIR에 별도 경로를 지정해주세요."
  exit 1
fi

printf 'iteration,response_time_seconds\n' > "$CSV_FILE"

for ((iteration = 1; iteration <= ITERATIONS; iteration++)); do
  docker exec "$REDIS_CONTAINER" redis-cli DEL "$CACHE_KEY" > /dev/null

  CURL_RESULT=$(
    curl \
      --silent \
      --show-error \
      --output /dev/null \
      --write-out '%{http_code} %{time_total}' \
      "$API_URL"
  )

  HTTP_STATUS="${CURL_RESULT%% *}"
  RESPONSE_TIME_SECONDS="${CURL_RESULT##* }"

  if [ "$HTTP_STATUS" != "200" ]; then
    echo "인기 숙소 API가 HTTP 200을 반환하지 않았습니다: $HTTP_STATUS"
    exit 1
  fi

  printf '%s,%s\n' "$iteration" "$RESPONSE_TIME_SECONDS" >> "$CSV_FILE"
  sleep "$SLEEP_SECONDS"
done

CSV_FILE="$CSV_FILE" SUMMARY_FILE="$SUMMARY_FILE" python3 - <<'PY'
import csv
import math
import os
import statistics

csv_file = os.environ["CSV_FILE"]
summary_file = os.environ["SUMMARY_FILE"]

with open(csv_file, newline="", encoding="utf-8") as source:
    values = sorted(
        float(row["response_time_seconds"]) * 1000
        for row in csv.DictReader(source)
    )


def nearest_rank_percentile(percentile):
    """Nearest-rank 방식: 정렬 후 ceil(p * N)번째 값을 반환합니다."""
    index = max(0, math.ceil(percentile * len(values)) - 1)
    return values[index]


lines = [
    f"count: {len(values)}",
    f"avg: {statistics.fmean(values):.2f}ms",
    f"p50: {nearest_rank_percentile(0.50):.2f}ms",
    f"p95: {nearest_rank_percentile(0.95):.2f}ms",
    f"p99: {nearest_rank_percentile(0.99):.2f}ms",
    f"min: {min(values):.2f}ms",
    f"max: {max(values):.2f}ms",
]

with open(summary_file, "w", encoding="utf-8") as destination:
    destination.write("\n".join(lines) + "\n")

print("\n".join(lines))
print(f"CSV: {csv_file}")
print(f"Summary: {summary_file}")
PY
