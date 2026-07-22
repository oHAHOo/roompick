# RoomPick API 명세서 — 임선구 담당

- 문서 버전: `v0.2`
- 작성일: 2026-07-21
- 최종 수정일: 2026-07-22
- 담당자: 임선구
- 담당 도메인: 숙소, 객실, 예약
- MVP 기준: 관리자가 등록한 숙소·객실 사용, 검색 기능 없음

이 문서는 RoomPick MVP에서 임선구 담당 API만 정의한다. 회원·인증 API와 결제 API는 각 담당자의 별도 명세에서 관리한다.

---

## 1. 설계 전제

1. API 기본 경로는 코드 컨벤션 초안에 따라 `/api/v1`을 사용한다.
2. 관리자 숙소·객실 등록 API는 minjae123123 담당의 `docs/API_SPEC_ADMIN.md`에서 정의한다.
3. 이 문서는 관리자가 등록한 숙소와 실제 객실을 조회·예약하는 API를 정의한다.
4. 숙소 목록, 객실 목록 검색, 필터, 정렬 API는 MVP에서 제외한다.
5. `ROOM` 한 행은 실제로 예약되는 물리적 객실 한 개를 의미한다.
6. 예약 생성 시 상태는 `PENDING_PAYMENT`가 된다.
7. 결제 성공·실패 API는 minjae123123 담당의 결제 API 명세에서 정의한다.
8. 결제 성공 시 예약은 `CONFIRMED`, 결제 실패 시 `CANCELED`로 변경한다.
9. 예약 취소 API는 예약 도메인 소유이므로 이 문서에 포함한다.
10. 인증 방식과 `AuthMember` 구현은 oHAHOo 담당의 회원·인증 설계를 따른다.
11. 아래 값 중 `결제 대기 10분`은 초안이며 팀 회의에서 최종 확정한다.

---

## 2. 공통 요청 규칙

### Content-Type

```http
Content-Type: application/json
```

### 인증 헤더

예약 API는 인증이 필요하다.

```http
Authorization: Bearer {accessToken}
```

숙소·객실 조회와 예약 가능 여부 확인은 비로그인 사용자도 호출할 수 있다.

### 날짜 형식

```text
yyyy-MM-dd
```

예시:

```text
2026-08-10
```

### 시간 형식

```text
HH:mm:ss
```

### 날짜 정책

- 체크인 날짜는 체크아웃 날짜보다 이전이어야 한다.
- 체크인 날짜는 현재 날짜보다 이전일 수 없다.
- 체크아웃 날짜는 숙박일에 포함하지 않는다.
- 예: 8월 10일 체크인, 8월 12일 체크아웃은 2박이다.

---

## 3. 공통 응답 형식

### 성공 응답

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

### 실패 응답

```json
{
  "success": false,
  "code": "ROOM_NOT_AVAILABLE",
  "message": "선택한 날짜에는 객실을 예약할 수 없습니다."
}
```

### 주요 HTTP 상태 코드

| 상태 코드 | 사용 상황 |
| --- | --- |
| `200 OK` | 조회·취소 성공 |
| `201 Created` | 예약 생성 성공 |
| `400 Bad Request` | 날짜·인원 등 요청 값 오류 |
| `401 Unauthorized` | 인증되지 않은 요청 |
| `403 Forbidden` | 다른 회원의 예약 접근 |
| `404 Not Found` | 숙소·객실·예약을 찾지 못함 |
| `409 Conflict` | 객실 예약 불가 또는 예약 상태 충돌 |
| `500 Internal Server Error` | 예상하지 못한 서버 오류 |

---

## 4. API 목록

| 번호 | Method | URL | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| 1 | `GET` | `/api/v1/accommodations/{accommodationId}` | 숙소 상세 조회 | 불필요 |
| 2 | `GET` | `/api/v1/rooms/{roomId}` | 객실 상세 조회 | 불필요 |
| 3 | `GET` | `/api/v1/rooms/{roomId}/availability` | 객실 예약 가능 여부 확인 | 불필요 |
| 4 | `POST` | `/api/v1/reservations` | 예약 생성 | 필요 |
| 5 | `GET` | `/api/v1/reservations` | 내 예약 목록 조회 | 필요 |
| 6 | `GET` | `/api/v1/reservations/{reservationId}` | 내 예약 상세 조회 | 필요 |
| 7 | `PATCH` | `/api/v1/reservations/{reservationId}/cancel` | 예약 취소 | 필요 |

관리자 등록 API는 `docs/API_SPEC_ADMIN.md`에서 관리한다. 검색과 관리자용 숙소·객실 수정·삭제·관리 목록 API는 MVP에 포함하지 않는다.

---

# 숙소 API

## 5. 숙소 상세 조회

등록된 숙소의 기본 정보와 소속 객실 요약을 조회한다.

### Request

```http
GET /api/v1/accommodations/1
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `accommodationId` | `Long` | O | 숙소 ID |

### Response — 200 OK

```json
{
  "success": true,
  "message": "숙소 상세 조회에 성공했습니다.",
  "data": {
    "accommodationId": 1,
    "name": "룸픽 호텔",
    "address": "서울특별시 강남구 테헤란로 123",
    "description": "RoomPick MVP 예약 테스트를 위한 숙소입니다.",
    "checkInTime": "15:00:00",
    "checkOutTime": "11:00:00",
    "status": "ACTIVE",
    "rooms": [
      {
        "roomId": 1,
        "name": "디럭스 더블룸",
        "pricePerNight": 100000,
        "standardCapacity": 2,
        "maxCapacity": 2,
        "status": "ACTIVE"
      }
    ]
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `404` | `ACCOMMODATION_NOT_FOUND` | 존재하지 않는 숙소 ID |

### 구현 메모

- 객실 목록은 해당 숙소에 포함된 객실 요약만 반환한다.
- MVP 시연에는 객실을 최소 1개 사용하지만 응답은 여러 객실을 고려해 배열로 반환한다.
- 조회 전용 트랜잭션을 사용한다.
- 숙소와 객실을 따로 반복 조회하지 않도록 DTO 직접 조회 또는 필요한 범위의 fetch join을 검토한다.

---

# 객실 API

## 6. 객실 상세 조회

객실과 소속 숙소의 기본 정보를 조회한다.

### Request

```http
GET /api/v1/rooms/1
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `roomId` | `Long` | O | 객실 ID |

### Response — 200 OK

```json
{
  "success": true,
  "message": "객실 상세 조회에 성공했습니다.",
  "data": {
    "roomId": 1,
    "accommodation": {
      "accommodationId": 1,
      "name": "룸픽 호텔",
      "address": "서울특별시 강남구 테헤란로 123"
    },
    "roomNumber": "101",
    "name": "디럭스 더블룸",
    "description": "2인이 이용할 수 있는 더블룸입니다.",
    "pricePerNight": 100000,
    "standardCapacity": 2,
    "maxCapacity": 2,
    "status": "ACTIVE"
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `404` | `ROOM_NOT_FOUND` | 존재하지 않는 객실 ID |

### 구현 메모

- 숙소 정보를 얻기 위해 추가 쿼리가 반복되지 않도록 필요한 연관 데이터만 조회한다.
- 현재 날짜의 예약 가능 여부는 객실 상세 응답에 포함하지 않는다.
- 사용자가 선택한 날짜의 예약 가능 여부는 별도 API에서 확인한다.

---

## 7. 객실 예약 가능 여부 확인

객실, 숙박 기간, 인원 조건을 기준으로 예약 가능 여부와 예상 금액을 확인한다.

### Request

```http
GET /api/v1/rooms/1/availability?checkInDate=2026-08-10&checkOutDate=2026-08-12&guestCount=2
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `roomId` | `Long` | O | 객실 ID |

### Query Parameter

| 이름 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `checkInDate` | `LocalDate` | O | 현재 날짜 이상 |
| `checkOutDate` | `LocalDate` | O | 체크인보다 이후 |
| `guestCount` | `int` | O | 1명 이상, 최대 인원 이하 |

### Response — 예약 가능, 200 OK

```json
{
  "success": true,
  "message": "객실 예약 가능 여부 확인에 성공했습니다.",
  "data": {
    "roomId": 1,
    "checkInDate": "2026-08-10",
    "checkOutDate": "2026-08-12",
    "guestCount": 2,
    "nightCount": 2,
    "pricePerNight": 100000,
    "totalAmount": 200000,
    "available": true,
    "unavailableReason": null
  }
}
```

### Response — 예약 불가, 200 OK

예약 불가는 정상적인 조회 결과이므로 `200 OK`와 `available=false`를 반환한다.

```json
{
  "success": true,
  "message": "객실 예약 가능 여부 확인에 성공했습니다.",
  "data": {
    "roomId": 1,
    "checkInDate": "2026-08-10",
    "checkOutDate": "2026-08-12",
    "guestCount": 2,
    "nightCount": 2,
    "pricePerNight": 100000,
    "totalAmount": 200000,
    "available": false,
    "unavailableReason": "선택한 날짜에 이미 예약된 객실입니다."
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_STAY_PERIOD` | 숙박 기간이 올바르지 않음 |
| `400` | `INVALID_GUEST_COUNT` | 인원이 1명 미만 |
| `400` | `ROOM_CAPACITY_EXCEEDED` | 객실 최대 인원을 초과함 |
| `404` | `ROOM_NOT_FOUND` | 객실이 존재하지 않음 |
| `409` | `ROOM_INACTIVE` | 운영 중지된 객실 |

### 날짜 겹침 조건

기존 예약과 다음 조건을 만족하면 날짜가 겹친다.

```text
기존 체크인 < 요청 체크아웃
AND
기존 체크아웃 > 요청 체크인
```

겹침 검사 대상 예약 상태:

```text
PENDING_PAYMENT 단, expiresAt이 현재보다 이후인 예약
CONFIRMED
```

`CANCELED`, `EXPIRED`, `COMPLETED` 예약은 현재 예약 가능 여부를 막지 않는다.

---

# 예약 API

## 8. 예약 생성

인증된 회원이 객실을 결제 대기 상태로 예약한다.

### Request

```http
POST /api/v1/reservations
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "roomId": 1,
  "checkInDate": "2026-08-10",
  "checkOutDate": "2026-08-12",
  "guestCount": 2
}
```

### Request Field

| 이름 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `roomId` | `Long` | O | 존재하고 운영 중인 객실 |
| `checkInDate` | `LocalDate` | O | 현재 날짜 이상 |
| `checkOutDate` | `LocalDate` | O | 체크인보다 이후 |
| `guestCount` | `int` | O | 1명 이상, 최대 인원 이하 |

### Response — 201 Created

```json
{
  "success": true,
  "message": "예약이 생성되었습니다. 제한 시간 내에 결제를 완료해 주세요.",
  "data": {
    "reservationId": 1,
    "memberId": 1,
    "accommodation": {
      "accommodationId": 1,
      "name": "룸픽 호텔"
    },
    "room": {
      "roomId": 1,
      "name": "디럭스 더블룸",
      "roomNumber": "101"
    },
    "checkInDate": "2026-08-10",
    "checkOutDate": "2026-08-12",
    "guestCount": 2,
    "nightCount": 2,
    "pricePerNight": 100000,
    "totalAmount": 200000,
    "status": "PENDING_PAYMENT",
    "expiresAt": "2026-08-01T14:10:00"
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_STAY_PERIOD` | 숙박 기간이 올바르지 않음 |
| `400` | `INVALID_GUEST_COUNT` | 인원이 1명 미만 |
| `400` | `ROOM_CAPACITY_EXCEEDED` | 객실 최대 인원을 초과함 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |
| `404` | `ROOM_NOT_FOUND` | 객실이 존재하지 않음 |
| `409` | `ROOM_INACTIVE` | 운영 중지된 객실 |
| `409` | `ROOM_NOT_AVAILABLE` | 동일 기간에 활성 예약이 존재함 |

### 처리 순서

```text
인증 회원 확인
→ 객실 조회
→ 숙박 기간·인원 검증
→ 기존 활성 예약과 날짜 중복 검사
→ 금액 계산 및 스냅샷 저장
→ PENDING_PAYMENT 예약 생성
→ 결제 만료 시각 설정
```

### 가격 스냅샷

예약이 생성될 때 다음 값을 예약 테이블에 저장한다.

- `pricePerNight`: 예약 당시 1박 가격
- `nightCount`: 숙박 일수
- `totalAmount`: 결제 예정 금액

객실 가격이 나중에 변경되어도 기존 예약 금액이 바뀌지 않도록 한다.

### 동시성 메모

- MVP의 기본 중복 검사는 활성 예약 존재 여부 조회로 구현한다.
- 동시 요청 제어 버전에서는 객실 행 잠금 또는 팀에서 선택한 락 전략을 적용한다.
- 락 적용 시 객실 조회, 중복 검사, 예약 생성 구간만 짧게 보호한다.

---

## 9. 내 예약 목록 조회

인증된 회원이 자신이 생성한 예약 목록을 최신순으로 조회한다.

### Request

```http
GET /api/v1/reservations?page=0&size=10
Authorization: Bearer {accessToken}
```

### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `page` | `int` | X | `0` | 0부터 시작하는 페이지 번호 |
| `size` | `int` | X | `10` | 페이지 크기, 최대 100 |

MVP에서는 상태 검색과 사용자 지정 정렬을 제공하지 않는다. 정렬은 `createdAt DESC`로 고정한다.

### Response — 200 OK

```json
{
  "success": true,
  "message": "내 예약 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "reservationId": 1,
        "accommodationName": "룸픽 호텔",
        "roomName": "디럭스 더블룸",
        "checkInDate": "2026-08-10",
        "checkOutDate": "2026-08-12",
        "guestCount": 2,
        "totalAmount": 200000,
        "status": "CONFIRMED",
        "createdAt": "2026-08-01T14:00:00"
      }
    ],
    "pageNumber": 0,
    "pageSize": 10,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |

### 구현 메모

- URL에 `memberId`를 받지 않고 인증 정보의 회원 ID만 사용한다.
- Entity 전체 그래프를 조회하지 않고 목록 응답에 필요한 필드만 조회하는 방식을 우선 검토한다.
- 목록이 비어 있으면 오류가 아닌 빈 `content`를 반환한다.

---

## 10. 내 예약 상세 조회

인증된 회원이 자신의 예약 상세 정보를 조회한다.

### Request

```http
GET /api/v1/reservations/1
Authorization: Bearer {accessToken}
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reservationId` | `Long` | O | 예약 ID |

### Response — 200 OK

```json
{
  "success": true,
  "message": "예약 상세 조회에 성공했습니다.",
  "data": {
    "reservationId": 1,
    "accommodation": {
      "accommodationId": 1,
      "name": "룸픽 호텔",
      "address": "서울특별시 강남구 테헤란로 123"
    },
    "room": {
      "roomId": 1,
      "name": "디럭스 더블룸",
      "roomNumber": "101"
    },
    "checkInDate": "2026-08-10",
    "checkOutDate": "2026-08-12",
    "guestCount": 2,
    "nightCount": 2,
    "pricePerNight": 100000,
    "totalAmount": 200000,
    "status": "CONFIRMED",
    "expiresAt": "2026-08-01T14:10:00",
    "createdAt": "2026-08-01T14:00:00",
    "canceledAt": null
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |
| `403` | `RESERVATION_ACCESS_DENIED` | 다른 회원의 예약에 접근 |
| `404` | `RESERVATION_NOT_FOUND` | 예약이 존재하지 않음 |

### 구현 메모

- 예약을 조회한 후 소유권을 검증하거나, 처음부터 `reservationId + memberId` 조건으로 조회한다.
- DB 조회 수를 줄이기 위해 필요한 숙소·객실 필드를 한 번의 조회로 반환하는 방식을 검토한다.

---

## 11. 예약 취소

인증된 회원이 자신의 결제 대기 또는 확정 예약을 취소한다.

### Request

```http
PATCH /api/v1/reservations/1/cancel
Authorization: Bearer {accessToken}
```

Request Body는 사용하지 않는다.

### Response — 200 OK

```json
{
  "success": true,
  "message": "예약이 취소되었습니다.",
  "data": {
    "reservationId": 1,
    "status": "CANCELED",
    "canceledAt": "2026-08-02T10:00:00"
  }
}
```

### 상태별 처리

| 현재 예약 상태 | 처리 |
| --- | --- |
| `PENDING_PAYMENT` | 결제 없이 예약 취소 |
| `CONFIRMED` | 결제 환불 성공 후 예약 취소 |
| `CANCELED` | 중복 취소 거절 |
| `EXPIRED` | 취소 불가 |
| `COMPLETED` | 취소 불가 |

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |
| `403` | `RESERVATION_ACCESS_DENIED` | 다른 회원의 예약을 취소하려 함 |
| `404` | `RESERVATION_NOT_FOUND` | 예약이 존재하지 않음 |
| `409` | `RESERVATION_NOT_CANCELABLE` | 현재 상태에서 취소할 수 없음 |
| `409` | `PAYMENT_REFUND_FAILED` | 결제 환불에 실패하여 예약을 취소하지 못함 |

### 처리 순서

```text
예약과 소유자 확인
→ 예약 취소 가능 상태 검증
→ 결제 완료 예약이면 결제 담당 서비스에 환불 요청
→ 환불 성공 확인
→ 예약 CANCELED 변경
→ 이후 예약 가능 여부 조회에서 제외
```

객실 테이블의 수량을 직접 증가시키지 않는다. 현재 모델에서 객실 사용 가능 여부는 활성 예약의 날짜 겹침 여부로 판단하므로 예약을 `CANCELED`로 변경하면 해당 날짜가 다시 예약 가능해진다.

---

## 12. 예약 상태 전이

```text
PENDING_PAYMENT
├─ 결제 성공 → CONFIRMED
├─ 결제 실패 → CANCELED
├─ 회원 취소 → CANCELED
└─ 결제 시간 만료 → EXPIRED

CONFIRMED
├─ 환불 성공 후 취소 → CANCELED
└─ 체크아웃 완료 처리 → COMPLETED (추후 버전)
```

허용되지 않은 상태 전이는 `409 Conflict`로 처리한다.

---

## 13. 에러 코드 목록

| Error Code | HTTP | 메시지 초안 |
| --- | --- | --- |
| `ACCOMMODATION_NOT_FOUND` | `404` | 숙소를 찾을 수 없습니다. |
| `ROOM_NOT_FOUND` | `404` | 객실을 찾을 수 없습니다. |
| `ROOM_INACTIVE` | `409` | 현재 이용할 수 없는 객실입니다. |
| `INVALID_STAY_PERIOD` | `400` | 숙박 기간이 올바르지 않습니다. |
| `INVALID_GUEST_COUNT` | `400` | 예약 인원은 1명 이상이어야 합니다. |
| `ROOM_CAPACITY_EXCEEDED` | `400` | 객실 최대 인원을 초과했습니다. |
| `ROOM_NOT_AVAILABLE` | `409` | 선택한 날짜에는 객실을 예약할 수 없습니다. |
| `RESERVATION_NOT_FOUND` | `404` | 예약을 찾을 수 없습니다. |
| `RESERVATION_ACCESS_DENIED` | `403` | 해당 예약에 접근할 권한이 없습니다. |
| `RESERVATION_NOT_CANCELABLE` | `409` | 현재 상태에서는 예약을 취소할 수 없습니다. |
| `PAYMENT_REFUND_FAILED` | `409` | 결제 환불에 실패하여 예약을 취소할 수 없습니다. |
| `UNAUTHORIZED` | `401` | 인증이 필요합니다. |

---

## 14. 담당 도메인 연결 계약

### 회원·인증 도메인에서 필요한 값

```text
AuthMember.memberId()
```

- 예약 API는 Request Body에서 `memberId`를 받지 않는다.
- 인증 컨텍스트의 회원 ID만 사용한다.

### 결제 도메인에 제공해야 하는 값

```text
reservationId
memberId
totalAmount
```

### 결제 도메인에서 받아야 하는 결과

```text
paymentStatus
paymentId
failureReason
```

- 결제 Service가 `ReservationRepository`를 직접 사용하지 않는다.
- 결제 결과에 따른 예약 상태 변경은 Facade에서 조율한다.

---

## 15. 팀 회의에서 최종 확정할 항목

- [ ] API 기본 경로를 `/api/v1`로 사용할지
- [ ] 결제 대기 시간을 10분으로 할지
- [ ] 만료 예약 처리 방식을 스케줄러로 할지 조회 시점 처리로 할지
- [ ] 예약 생성과 결제를 API 두 번으로 나눌지 하나의 유스케이스로 합칠지
- [ ] 확정 예약 취소 시 MVP부터 환불을 호출할지
- [ ] 숙소 상세 API와 객실 상세 API를 모두 유지할지
- [ ] 예약 목록에 페이지네이션을 MVP부터 적용할지
- [ ] 실제 인증 객체 이름을 `AuthMember`로 사용할지

팀 결정이 끝나면 이 문서를 `v0.2`로 갱신하고 ERD와 코드 컨벤션에도 동일하게 반영한다.
