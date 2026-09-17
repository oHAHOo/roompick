# RoomPick 여행 계획 API 명세

LLM으로 여행 일정을 생성하고 등록된 숙소 중에서 그 일정에 맞는 숙소를 추천하는 API를 정의한다.

- 담당: 여행 계획 도메인(`com.roompick.domain.travelplan`)
- 전제: 이 기능은 초기 MVP 제외 목록(`docs/MVP_CONTEXT.md` 6절)에 있던 AI 추천을 팀 합의로 추가한 것이다.
- 공통 응답 형식과 오류 형식은 `docs/API_SPEC_OWNER.md`와 동일하다.

---

## 1. 여행 계획 생성

좌표와 숙박 기간을 받아 일정과 추천 숙소를 생성한다. 인증 없이 호출할 수 있다.

좌표는 장소 후보 검색(`GET /api/v1/places/search`)으로 미리 확보한 값을 전달한다.
이 API는 Kakao Local API를 다시 호출하지 않는다.

### Request

```http
POST /api/v1/travel-plans
Content-Type: application/json

{
  "latitude": 37.5665,
  "longitude": 126.9780,
  "checkInDate": "2026-10-01",
  "checkOutDate": "2026-10-03",
  "guestCount": 2
}
```

### Body Parameter

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | `Double` | O | 여행 중심 위도, -90 이상 90 이하 |
| `longitude` | `Double` | O | 여행 중심 경도, -180 이상 180 이하 |
| `checkInDate` | `LocalDate` | O | 체크인 날짜, 과거 날짜 불가 |
| `checkOutDate` | `LocalDate` | O | 체크아웃 날짜, 체크인 날짜보다 이후이며 최대 14박 |
| `guestCount` | `Integer` | O | 여행 인원, 1명 이상 |

### Response — 201 Created

```json
{
  "success": true,
  "message": "여행 계획 생성에 성공했습니다.",
  "data": {
    "travelPlanId": 1,
    "checkInDate": "2026-10-01",
    "checkOutDate": "2026-10-03",
    "guestCount": 2,
    "itinerary": [
      {
        "day": 1,
        "summary": "도심 고궁과 한옥 마을 산책",
        "activities": ["경복궁 관람", "북촌 한옥마을 산책"]
      },
      {
        "day": 2,
        "summary": "남산과 전통시장",
        "activities": ["남산서울타워", "광장시장 야시장"]
      }
    ],
    "recommendedAccommodations": [
      {
        "accommodationId": 1,
        "name": "룸픽 호텔",
        "address": "서울특별시",
        "distanceKm": 1.2,
        "imageUrl": "https://cdn.example.com/accommodations/1.jpg",
        "reason": "일정의 이동 동선 중심에 있어 이동 시간이 짧다."
      }
    ]
  }
}
```

- 반경 10km 안에 `ACTIVE` 숙소가 없으면 오류가 아닌 빈 `recommendedAccommodations`와 일정만 반환한다.
- 숙소의 `accommodationId`, `name`, `address`, `distanceKm`, `imageUrl`은 DB 조회 결과이며 LLM이 생성한
  값이 아니다. LLM이 생성하는 값은 `itinerary`와 `reason`뿐이다.

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_INPUT_VALUE` | 좌표 범위 초과, 체크아웃이 체크인 이후가 아님, 과거 체크인, 14박 초과, 인원 1명 미만, 필수 값 누락 |
| `504` | `TRAVEL_PLAN_LLM_TIMEOUT` | LLM 호출 timeout |
| `503` | `TRAVEL_PLAN_LLM_RATE_LIMITED` | LLM 요청 제한 |
| `503` | `TRAVEL_PLAN_LLM_UNAVAILABLE` | LLM 연결 실패 또는 외부 오류 |
| `502` | `TRAVEL_PLAN_LLM_INVALID_RESPONSE` | LLM 응답이 JSON 형식이 아니거나 `itinerary`가 없음 |

### 구현 메모

- 호출 순서는 `요청 검증 → 반경 내 ACTIVE 숙소 조회 → LLM 호출 → 실제 숙소와 매핑 → 이력 저장`이다.
- 숙소 후보 조회는 기존 `AccommodationLocationSearchService.searchNearby()`를 재사용하며
  반경은 10km, 후보는 최대 5개다.
- LLM에는 후보 숙소의 이름·주소·거리만 전달하고, 추천은 후보 목록의 `candidateIndex`로만 받는다.
  후보 범위를 벗어난 인덱스는 버려지므로 LLM이 등록되지 않은 숙소를 만들어내도 응답에 반영되지 않는다.
- LLM 호출 중에는 DB 트랜잭션을 시작하지 않는다. 이력 저장은 호출이 끝난 뒤
  `TravelPlanHistoryService`의 트랜잭션에서 수행한다.
- 생성 이력은 `ai_recommendations`와 `ai_recommendation_items`에 저장한다. 공개 API이므로 회원과
  연결하지 않는다.
- 모델과 API Key는 `ANTHROPIC_MODEL`, `ANTHROPIC_API_KEY` 환경변수로 관리하며 응답이나 로그에
  노출하지 않는다.
- 현재는 요청 인원이나 날짜 기준 객실 예약 가능 여부를 반영하지 않는다. 즉 추천된 숙소가 해당 날짜에
  만실일 수 있다. 예약 가능 여부 필터링은 후속 과제다.
- 장소 검색과 마찬가지로 애플리케이션 rate limiting은 추가하지 않았다. 다만 이 API는 호출마다 유료
  LLM 요청이 발생하므로, 공개 노출 전에 reverse proxy/API Gateway 계층의 요청 제한을 우선 검토한다.
