#!/usr/bin/env bash
# 로컬 통합 테스트용 백엔드 기동 스크립트.
#
# 프로젝트 루트의 .env를 먼저 읽고, 거기에 없는 값만 더미로 채운다.
# 실제 Kakao/PortOne/S3 키를 쓰려면 .env에 넣으면 된다(.env는 gitignore됨).
#   KAKAO_REST_API_KEY=<본인 키>
cd "D:/Study/roompick-backend" || exit 1

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
  echo "[run-backend] .env 로드됨"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export ACCOMMODATION_LOCATION_SEARCH_ENGINE="${ACCOMMODATION_LOCATION_SEARCH_ENGINE:-MYSQL}"
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3307}"
export DB_NAME="${DB_NAME:-roompick}"
export DB_USERNAME="${DB_USERNAME:-roompick}"
export DB_PASSWORD="${DB_PASSWORD:-roompick}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-6379}"

# 아래 값들은 .env에 실제 키가 없을 때만 더미로 채운다.
# 더미 상태에서는 해당 기능이 실패한다:
#   KAKAO  -> 장소 검색 502 PLACE_API_AUTHENTICATION_FAILED
#   S3     -> 이미지 첨부 등록 502 IMAGE_004
export PORTONE_API_SECRET="${PORTONE_API_SECRET:-local-dummy-not-used}"
export PORTONE_STORE_ID="${PORTONE_STORE_ID:-local-dummy-not-used}"
export PORTONE_CHANNEL_KEY="${PORTONE_CHANNEL_KEY:-local-dummy-not-used}"
export AWS_S3_ACCESS_KEY="${AWS_S3_ACCESS_KEY:-local-dummy-not-used}"
export AWS_S3_SECRET_KEY="${AWS_S3_SECRET_KEY:-local-dummy-not-used}"
export KAKAO_REST_API_KEY="${KAKAO_REST_API_KEY:-local-dummy-not-used}"

if [ "$KAKAO_REST_API_KEY" = "local-dummy-not-used" ]; then
  echo "[run-backend] 경고: KAKAO_REST_API_KEY가 더미입니다. 장소 검색은 502로 실패합니다."
else
  echo "[run-backend] KAKAO_REST_API_KEY 설정됨 (장소 검색 사용 가능)"
fi

exec ./gradlew bootRun --console=plain
