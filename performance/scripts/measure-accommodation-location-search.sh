#!/usr/bin/env bash

# RoomPick 위치 기반 숙소 검색 성능 비교 전용 스크립트입니다.
#
# 동일한 API와 동일한 검색 조건으로 MySQL / Elasticsearch를
# 최소 3회 반복 측정하고 원본 결과와 요약 결과를 모두 보존합니다.
#
# 기존 성능 결과를 덮어쓰지 않도록
# 실행할 때마다 새로운 timestamp 디렉터리를 생성합니다.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

MYSQL_CONTAINER="${MYSQL_CONTAINER:-roompick-mysql}"
ELASTICSEARCH_CONTAINER="${ELASTICSEARCH_CONTAINER:-roompick-elasticsearch}"
DATABASE_NAME="${DATABASE_NAME:-roompick}"

SEARCH_ENGINE="${SEARCH_ENGINE:-mysql-bounding-box}"
KEYWORD="${KEYWORD:-}"

LATITUDE="${LATITUDE:-37.5665}"
LONGITUDE="${LONGITUDE:-126.9780}"
RADIUS_KM="${RADIUS_KM:-5}"
LIMIT="${LIMIT:-20}"

VUS="${VUS:-10}"
DURATION="${DURATION:-30s}"
THINK_TIME="${THINK_TIME:-0}"

RUNS="${RUNS:-3}"

OUTPUT_ROOT="${OUTPUT_ROOT:-performance/results/location-search}"

# 성능 결과에 사용할 검색 엔진 구분값을 검증하고,
# 실제 애플리케이션이 사용해야 할 검색 엔진을 결정합니다.
case "$SEARCH_ENGINE" in
    mysql-bounding-box)
        EXPECTED_LOCATION_ENGINE="MYSQL"
        ;;
    elasticsearch)
        EXPECTED_LOCATION_ENGINE="ELASTICSEARCH"
        ;;
    mysql)
        echo "현재 코드에서는 MySQL Baseline을 새로 측정할 수 없습니다."
        echo "기존 공식 Baseline 결과를 사용하거나 Bounding Box 적용 전 커밋에서 측정하세요."
        exit 1
        ;;
    *)
        echo "SEARCH_ENGINE은 mysql-bounding-box 또는 elasticsearch여야 합니다: $SEARCH_ENGINE"
        exit 1
        ;;
esac

# 정수형 측정 조건을 검증합니다.
for value_name in LIMIT VUS RUNS; do
    value="${!value_name}"

    if ! [[ "$value" =~ ^[0-9]+$ ]] \
        || [ "$value" -lt 1 ]; then
        echo "$value_name 값은 1 이상의 정수여야 합니다: $value"
        exit 1
    fi
done

if [ "$LIMIT" -gt 100 ]; then
    echo "LIMIT 값은 100 이하여야 합니다: $LIMIT"
    exit 1
fi

# k6 설치 여부를 확인합니다.
if ! command -v k6 > /dev/null 2>&1; then
    echo "k6를 찾을 수 없습니다."
    exit 1
fi

echo "[1/6] 애플리케이션 상태 및 위치 검색 엔진 확인"

if ! curl -fsS \
    "${BASE_URL}/actuator/health" \
    > /dev/null; then

    echo "RoomPick 애플리케이션 상태를 확인할 수 없습니다: $BASE_URL"
    exit 1
fi

# 성능 결과에 기록할 SEARCH_ENGINE과
# 실제 실행 중인 Spring 애플리케이션의 검색 엔진이
# 일치하는지 측정 전에 검증합니다.
APPLICATION_LOCATION_ENGINE=$(
    curl -fsS \
        "${BASE_URL}/actuator/info" \
    | python3 -c '
import json
import sys

data = json.load(sys.stdin)

print(
    data
    .get("roompick", {})
    .get("search", {})
    .get("location-engine", "")
)
'
)

if [ -z "$APPLICATION_LOCATION_ENGINE" ]; then
    echo "현재 애플리케이션의 위치 검색 엔진을 확인할 수 없습니다."
    echo "확인 경로: ${BASE_URL}/actuator/info"
    exit 1
fi

if [ "$APPLICATION_LOCATION_ENGINE" != "$EXPECTED_LOCATION_ENGINE" ]; then
    echo "성능 측정 검색 엔진과 실제 애플리케이션 검색 엔진이 일치하지 않습니다."
    echo "SEARCH_ENGINE=$SEARCH_ENGINE"
    echo "expected_application_engine=$EXPECTED_LOCATION_ENGINE"
    echo "actual_application_engine=$APPLICATION_LOCATION_ENGINE"
    exit 1
fi

echo "위치 검색 엔진 확인 완료: $APPLICATION_LOCATION_ENGINE"

echo "[2/6] 필수 컨테이너 상태 확인"

# MySQL 컨테이너 존재 여부를 확인합니다.
if ! docker inspect "$MYSQL_CONTAINER" > /dev/null 2>&1; then
    echo "MySQL 컨테이너를 찾을 수 없습니다: $MYSQL_CONTAINER"
    exit 1
fi

# MySQL 컨테이너 실행 여부를 확인합니다.
if [ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER")" != "true" ]; then
    echo "MySQL 컨테이너가 실행 중이 아닙니다."
    exit 1
fi

# MySQL 성능 측정용 계정 환경변수를 확인합니다.
if ! docker exec "$MYSQL_CONTAINER" sh -c '
test -n "${MYSQL_USER:-}" && test -n "${MYSQL_PASSWORD:-}"
'; then
    echo "MySQL 컨테이너에 MYSQL_USER와 MYSQL_PASSWORD가 필요합니다."
    exit 1
fi

# Elasticsearch 측정일 때만 Elasticsearch 컨테이너를
# 추가로 검사합니다.
if [ "$SEARCH_ENGINE" = "elasticsearch" ]; then
    if ! docker inspect "$ELASTICSEARCH_CONTAINER" > /dev/null 2>&1; then
        echo "Elasticsearch 컨테이너를 찾을 수 없습니다: $ELASTICSEARCH_CONTAINER"
        exit 1
    fi

    if [ "$(docker inspect -f '{{.State.Running}}' "$ELASTICSEARCH_CONTAINER")" != "true" ]; then
        echo "Elasticsearch 컨테이너가 실행 중이 아닙니다."
        exit 1
    fi
fi

echo "[3/6] 측정 결과 디렉터리 생성"

# keyword 유무에 따라 측정 시나리오를 구분합니다.
if [ -n "$(printf '%s' "$KEYWORD" | tr -d '[:space:]')" ]; then
    SCENARIO="geo-keyword"
else
    SCENARIO="geo-only"
fi

TIMESTAMP=$(date '+%Y%m%d-%H%M%S')

OUTPUT_DIR="${OUTPUT_ROOT}/${TIMESTAMP}-${SEARCH_ENGINE}-${SCENARIO}"

# 기존 결과를 절대 덮어쓰지 않습니다.
if [ -e "$OUTPUT_DIR" ]; then
    echo "결과 경로가 이미 존재합니다: $OUTPUT_DIR"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

# 실제 측정 조건을 결과와 함께 보관합니다.
cat > "${OUTPUT_DIR}/conditions.txt" <<EOF
search_engine=${SEARCH_ENGINE}
application_location_engine=${APPLICATION_LOCATION_ENGINE}
scenario=${SCENARIO}
keyword=${KEYWORD}
latitude=${LATITUDE}
longitude=${LONGITUDE}
radius_km=${RADIUS_KM}
limit=${LIMIT}
vus=${VUS}
duration=${DURATION}
think_time=${THINK_TIME}
runs=${RUNS}
EOF

# MySQL 서버의 누적 SELECT 실행 횟수를 읽습니다.
read_mysql_selects() {
    docker exec \
        -e PERFORMANCE_DATABASE="$DATABASE_NAME" \
        "$MYSQL_CONTAINER" \
        sh -c '
mysql \
    -N \
    -u"$MYSQL_USER" \
    -p"$MYSQL_PASSWORD" \
    "$PERFORMANCE_DATABASE" \
    -e "SHOW GLOBAL STATUS LIKE '\''Com_select'\'';" \
    2>/dev/null
' | awk '{print $2}'
}

echo "[4/6] 측정 전 워밍업"

# JIT, Connection Pool 등 최초 요청 비용의 영향을 줄이기 위해
# 본 측정 전에 짧은 워밍업을 수행합니다.
BASE_URL="$BASE_URL" \
SEARCH_ENGINE="$SEARCH_ENGINE" \
KEYWORD="$KEYWORD" \
LATITUDE="$LATITUDE" \
LONGITUDE="$LONGITUDE" \
RADIUS_KM="$RADIUS_KM" \
LIMIT="$LIMIT" \
VUS=1 \
DURATION=5s \
THINK_TIME=0 \
k6 run \
    performance/k6/accommodation-location-search.js \
    > "${OUTPUT_DIR}/warmup.txt" \
    2>&1

echo "[5/6] 본 측정 ${RUNS}회 실행"

printf '%s\n' \
    "search_engine,scenario,run,mysql_selects" \
    > "${OUTPUT_DIR}/query-counts.csv"

for run in $(seq 1 "$RUNS"); do
    echo "측정 실행 중: ${run}/${RUNS}"

    # 본 측정 시작 전 MySQL 누적 SELECT 횟수를 기록합니다.
    mysql_before=$(read_mysql_selects)

    BASE_URL="$BASE_URL" \
    SEARCH_ENGINE="$SEARCH_ENGINE" \
    KEYWORD="$KEYWORD" \
    LATITUDE="$LATITUDE" \
    LONGITUDE="$LONGITUDE" \
    RADIUS_KM="$RADIUS_KM" \
    LIMIT="$LIMIT" \
    VUS="$VUS" \
    DURATION="$DURATION" \
    THINK_TIME="$THINK_TIME" \
    k6 run \
        --summary-export \
        "${OUTPUT_DIR}/run-${run}-summary.json" \
        performance/k6/accommodation-location-search.js \
        > "${OUTPUT_DIR}/run-${run}.txt" \
        2>&1

    # 본 측정 종료 후 MySQL 누적 SELECT 횟수를 기록합니다.
    mysql_after=$(read_mysql_selects)

    # 두 번째 상태 확인 과정에서 포함될 수 있는 조회 1회를 제외하여
    # 실제 측정 구간의 SELECT 증가량을 계산합니다.
    measured_mysql_selects=$((mysql_after - mysql_before - 1))

    if [ "$measured_mysql_selects" -lt 0 ]; then
        measured_mysql_selects=0
    fi

    printf '%s,%s,%s,%s\n' \
        "$SEARCH_ENGINE" \
        "$SCENARIO" \
        "$run" \
        "$measured_mysql_selects" \
        >> "${OUTPUT_DIR}/query-counts.csv"
done

echo "[6/6] 요약 CSV 생성"

python3 \
    - "$OUTPUT_DIR" \
    "$SEARCH_ENGINE" \
    "$SCENARIO" \
    "$KEYWORD" \
    "$LATITUDE" \
    "$LONGITUDE" \
    "$RADIUS_KM" \
    "$LIMIT" \
    "$VUS" \
    "$DURATION" \
    <<'PY'
import csv
import json
import pathlib
import sys

output_dir = pathlib.Path(sys.argv[1])

search_engine = sys.argv[2]
scenario = sys.argv[3]
keyword = sys.argv[4]
latitude = sys.argv[5]
longitude = sys.argv[6]
radius_km = sys.argv[7]
limit = sys.argv[8]
vus = sys.argv[9]
duration = sys.argv[10]

with (
    output_dir / "query-counts.csv"
).open(
    encoding="utf-8"
) as source:
    query_counts = list(
        csv.DictReader(source)
    )

rows = []

for index, query_count in enumerate(
    query_counts,
    start=1
):
    summary_path = (
        output_dir
        / f"run-{index}-summary.json"
    )

    with summary_path.open(
        encoding="utf-8"
    ) as source:
        summary = json.load(source)

    duration_metric = (
        summary["metrics"]["http_req_duration"]
    )

    request_metric = (
        summary["metrics"]["http_reqs"]
    )

    failure_metric = (
        summary["metrics"]["http_req_failed"]
    )

    rows.append(
        {
            "search_engine": search_engine,
            "scenario": scenario,
            "run": index,
            "keyword": keyword,
            "latitude": latitude,
            "longitude": longitude,
            "radius_km": radius_km,
            "limit": limit,
            "vus": vus,
            "duration": duration,
            "mysql_selects": int(
                query_count["mysql_selects"]
            ),
            "request_count": int(
                request_metric["count"]
            ),
            "avg_ms": duration_metric["avg"],
            "p50_ms": duration_metric["med"],
            "p95_ms": duration_metric["p(95)"],
            "p99_ms": duration_metric["p(99)"],
            "throughput_rps": request_metric["rate"],
            "error_rate": failure_metric["value"],
        }
    )

columns = list(rows[0])

summary_path = (
    output_dir
    / "summary.csv"
)

with summary_path.open(
    "w",
    newline="",
    encoding="utf-8"
) as target:
    writer = csv.DictWriter(
        target,
        fieldnames=columns
    )

    writer.writeheader()
    writer.writerows(rows)

print(summary_path)
PY

echo
echo "위치 검색 성능 측정 완료"
echo "결과 디렉터리: $OUTPUT_DIR"
