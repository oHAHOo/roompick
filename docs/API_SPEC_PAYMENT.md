# RoomPick API 명세서 — 조민재 담당

- 문서 버전: `v0.2`
- 작성일: 2026-07-27
- 담당자: minjae123123
- 담당 기능: 결제 준비, Mock 결제 성공 처리, Mock 결제 실패 처리
- 협업 도메인: 예약, 회원·인증·보안

이 문서는 RoomPick MVP의 결제 준비, Mock 결제 성공 및 실패 API와 결제·예약 상태 변경 규칙을 정의한다.

실제 PG사 연동은 MVP 범위에서 제외하며, 서버 내부에서 결제 성공과 실패를 모의 처리한다.

---

## 1. 설계 전제

1. API 기본 경로는 `/api/v1`을 사용한다.
2. 결제 API는 인증된 회원만 호출할 수 있다.
3. 회원은 본인의 예약에 대한 결제만 처리할 수 있다.
4. MVP에서는 하나의 예약에 하나의 결제만 생성할 수 있다.
5. 결제 금액은 예약에 저장된 `totalAmount`를 기준으로 한다.
6. 클라이언트가 전달한 금액은 서버에 저장된 결제 금액과 비교하는 용도로만 사용한다.
7. 결제 준비 시 결제 상태는 `READY`가 된다.
8. 결제 성공 시 결제 상태는 `PAID`, 예약 상태는 `CONFIRMED`가 된다.
9. 결제 실패 시 결제 상태는 `FAILED`, 예약 상태는 `CANCELED`가 된다.
10. 결제와 예약의 상태 변경은 하나의 트랜잭션에서 처리한다.
11. 결제 실패 시 예약을 취소하여 해당 객실의 날짜 점유를 해제한다.
12. Controller는 `PaymentFacade`만 호출한다.
13. `PaymentFacade`가 `PaymentService`와 `ReservationService`를 조율한다.
14. `PaymentService`와 `ReservationService`는 서로 직접 호출하지 않는다.
15. 결제 성공과 실패 처리는 `READY` 상태의 결제에만 수행할 수 있다.

---

## 2. 공통 요청·인증 규칙

### Request Header

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

| 상황 | HTTP | ErrorCode 상수 | 응답 코드 |
| --- | --- | --- | --- |
| 인증 정보 없음 | `401 Unauthorized` | `UNAUTHORIZED` | `COMMON_003` |
| 다른 회원의 예약에 접근 | `403 Forbidden` | `RESERVATION_ACCESS_DENIED` | `RESERVATION_ACCESS_DENIED` |
| 인증된 본인 요청 | API 처리 계속 | - | - |

### 성공 응답 형식

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

### 실패 응답 형식

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "오류 메시지"
}
```

---

## 3. API 목록

| 번호 | Method | URL | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/v1/reservations/{reservationId}/payments` | 결제 준비 | `USER` 필요 |
| 2 | `POST` | `/api/v1/payments/{paymentId}/approve` | Mock 결제 성공 처리 | `USER` 필요 |
| 3 | `POST` | `/api/v1/payments/{paymentId}/fail` | Mock 결제 실패 처리 | `USER` 필요 |

---

# 결제 API

## 4. 결제 준비

인증된 회원의 예약을 기준으로 `READY` 상태의 결제를 생성한다.

### Request

```http
POST /api/v1/reservations/{reservationId}/payments
Authorization: Bearer {accessToken}
```

요청 Body는 사용하지 않는다.

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reservationId` | `Long` | O | 결제를 준비할 예약 ID |

### Response — 201 Created

```json
{
  "success": true,
  "message": "결제가 준비되었습니다.",
  "data": {
    "paymentId": 1,
    "reservationId": 10,
    "amount": 200000,
    "status": "READY"
  }
}
```

### Response Field

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | `Long` | 생성된 결제 ID |
| `reservationId` | `Long` | 결제 대상 예약 ID |
| `amount` | `long` | 예약에 저장된 최종 결제 금액 |
| `status` | `PaymentStatus` | 결제 상태, `READY` 반환 |

### 처리 규칙

- 예약이 존재해야 한다.
- 인증된 회원 본인의 예약이어야 한다.
- 예약 상태가 `PENDING_PAYMENT`여야 한다.
- 예약의 결제 대기 시간이 만료되지 않아야 한다.
- 결제 금액은 예약에 저장된 `totalAmount`를 사용한다.
- 같은 예약에 결제가 이미 존재하면 새로운 결제를 생성하지 않는다.
- MVP에서는 예약과 결제가 일대일 관계이다.

### 상태 변화

```text
Payment: 결제 없음 → READY
Reservation: PENDING_PAYMENT 유지
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `COMMON_001` | 예약 ID 등 요청값이 올바르지 않음 |
| `401` | `COMMON_003` | 인증되지 않은 요청 |
| `403` | `RESERVATION_ACCESS_DENIED` | 다른 회원의 예약에 결제 준비 요청 |
| `404` | `RESERVATION_NOT_FOUND` | 예약이 존재하지 않음 |
| `409` | `RESERVATION_NOT_PAYABLE` | 결제 가능한 예약 상태가 아님 |
| `409` | `RESERVATION_PAYMENT_EXPIRED` | 결제 대기 시간이 만료됨 |
| `409` | `PAYMENT_ALREADY_EXISTS` | 해당 예약의 결제가 이미 생성됨 |

### 처리 순서

```text
인증 회원 확인
→ 예약 조회
→ 예약 소유자 확인
→ 예약 상태 확인
→ 결제 만료 시각 확인
→ 기존 결제 중복 확인
→ 예약 총액으로 READY 결제 생성
→ 결제 저장
→ 생성 결과 반환
```

### cURL 요청 예시

```bash
curl -X POST "http://localhost:8080/api/v1/reservations/10/payments" \
  -H "Authorization: Bearer {accessToken}"
```

---

## 5. Mock 결제 성공 처리

`READY` 상태의 결제를 승인하여 `PAID` 상태로 변경하고, 결제 대상 예약을 `CONFIRMED` 상태로 변경한다.

실제 PG사 승인 요청은 수행하지 않는다.

### Request

```http
POST /api/v1/payments/{paymentId}/approve
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "amount": 200000
}
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `paymentId` | `Long` | O | 승인할 결제 ID |

### Request Field

| 이름 | 타입 | 필수 | 제약 조건 | 설명 |
| --- | --- | --- | --- | --- |
| `amount` | `Long` | O | 0원 이상 | 클라이언트가 결제를 요청한 금액 |

요청 금액은 새로운 결제 금액으로 저장하지 않는다. 서버에 저장된 `Payment.amount`와 일치하는지를 검증하는 데 사용한다.

### Response — 200 OK

```json
{
  "success": true,
  "message": "결제가 완료되었습니다.",
  "data": {
    "paymentId": 1,
    "reservationId": 10,
    "amount": 200000,
    "paymentStatus": "PAID",
    "reservationStatus": "CONFIRMED",
    "approvedAt": "2026-07-27T15:30:00"
  }
}
```

### Response Field

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | `Long` | 승인된 결제 ID |
| `reservationId` | `Long` | 결제 대상 예약 ID |
| `amount` | `long` | 서버에 저장된 결제 금액 |
| `paymentStatus` | `PaymentStatus` | 승인 후 결제 상태, `PAID` |
| `reservationStatus` | `ReservationStatus` | 승인 후 예약 상태, `CONFIRMED` |
| `approvedAt` | `LocalDateTime` | 결제가 승인된 시각 |

### 처리 규칙

- 결제가 존재해야 한다.
- 인증된 회원 본인의 예약에 연결된 결제여야 한다.
- 결제 상태가 `READY`여야 한다.
- 예약 상태가 `PENDING_PAYMENT`여야 한다.
- 예약의 결제 대기 시간이 만료되지 않아야 한다.
- 요청 금액과 서버에 저장된 결제 금액이 일치해야 한다.
- 이미 처리된 결제는 다시 승인할 수 없다.
- 결제와 예약 상태 변경은 같은 트랜잭션에서 처리한다.
- 처리 중 예외가 발생하면 결제와 예약의 변경 사항을 모두 롤백한다.
- 승인 시각은 DB의 `DATETIME(6)` 정밀도에 맞게 마이크로초 단위로 저장한다.

### 상태 변화

```text
Payment: READY → PAID
Reservation: PENDING_PAYMENT → CONFIRMED
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `COMMON_001` | 금액이 누락되거나 음수임 |
| `400` | `PAYMENT_AMOUNT_MISMATCH` | 요청 금액과 저장된 결제 금액이 다름 |
| `401` | `COMMON_003` | 인증되지 않은 요청 |
| `403` | `RESERVATION_ACCESS_DENIED` | 다른 회원 예약의 결제 승인 요청 |
| `404` | `PAYMENT_NOT_FOUND` | 결제가 존재하지 않음 |
| `409` | `INVALID_PAYMENT_STATUS` | 결제 상태가 `READY`가 아님 |
| `409` | `RESERVATION_NOT_PAYABLE` | 예약 상태가 `PENDING_PAYMENT`가 아님 |
| `409` | `RESERVATION_PAYMENT_EXPIRED` | 예약의 결제 대기 시간이 만료됨 |

### 결제 금액 불일치 응답 — 400 Bad Request

```json
{
  "success": false,
  "code": "PAYMENT_AMOUNT_MISMATCH",
  "message": "결제 요청 금액이 저장된 결제 금액과 일치하지 않습니다."
}
```

### 중복 승인 응답 — 409 Conflict

```json
{
  "success": false,
  "code": "INVALID_PAYMENT_STATUS",
  "message": "현재 결제 상태에서는 요청을 처리할 수 없습니다."
}
```

### 미인증 요청 응답 — 401 Unauthorized

```json
{
  "success": false,
  "code": "COMMON_003",
  "message": "인증이 필요합니다."
}
```

### 처리 순서

```text
인증 회원 확인
→ 결제 조회
→ 결제 상태 확인
→ 요청 금액과 저장된 결제 금액 비교
→ Payment 상태를 PAID로 변경
→ approvedAt 기록
→ 예약 소유자 확인
→ 예약 상태 및 만료 여부 확인
→ Reservation 상태를 CONFIRMED로 변경
→ 결과 반환
```

결제 상태와 금액을 예약 검증보다 먼저 확인한다.

이미 처리된 결제를 다시 승인하면 예약 상태와 관계없이 `INVALID_PAYMENT_STATUS`를 반환한다.

예약 소유자, 예약 상태 또는 만료 여부 검증에 실패하면 먼저 변경된 `Payment`의 상태도 트랜잭션 롤백에 의해 `READY`로 복구된다.

### cURL 요청 예시

```bash
curl -X POST "http://localhost:8080/api/v1/payments/1/approve" \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 200000
  }'
```

---

## 6. Mock 결제 실패 처리

`READY` 상태의 결제를 `FAILED` 상태로 변경하고, 결제 대상 예약을 `CANCELED` 상태로 변경한다.

실제 PG사 실패 응답은 받지 않으며, 서버 내부에서 결제 실패를 모의 처리한다.

### Request

```http
POST /api/v1/payments/{paymentId}/fail
Authorization: Bearer {accessToken}
```

요청 Body는 사용하지 않는다.

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `paymentId` | `Long` | O | 실패 처리할 결제 ID |

### Response — 200 OK

```json
{
  "success": true,
  "message": "결제 실패 처리가 완료되었습니다.",
  "data": {
    "paymentId": 1,
    "reservationId": 10,
    "amount": 200000,
    "paymentStatus": "FAILED",
    "reservationStatus": "CANCELED",
    "failedAt": "2026-07-27T16:00:00",
    "canceledAt": "2026-07-27T16:00:00"
  }
}
```

### Response Field

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `paymentId` | `Long` | 실패 처리된 결제 ID |
| `reservationId` | `Long` | 결제 대상 예약 ID |
| `amount` | `long` | 서버에 저장된 결제 금액 |
| `paymentStatus` | `PaymentStatus` | 실패 처리 후 결제 상태, `FAILED` |
| `reservationStatus` | `ReservationStatus` | 실패 처리 후 예약 상태, `CANCELED` |
| `failedAt` | `LocalDateTime` | 결제가 실패 처리된 시각 |
| `canceledAt` | `LocalDateTime` | 예약이 취소된 시각 |

### 처리 규칙

- 결제가 존재해야 한다.
- 인증된 회원 본인의 예약에 연결된 결제여야 한다.
- 결제 상태가 `READY`여야 한다.
- 예약 상태가 `PENDING_PAYMENT`여야 한다.
- 이미 `PAID` 또는 `FAILED` 등으로 처리된 결제는 실패 처리할 수 없다.
- 결제 실패와 예약 취소는 같은 트랜잭션에서 처리한다.
- 처리 중 예외가 발생하면 결제와 예약의 변경 사항을 모두 롤백한다.
- `failedAt`과 `canceledAt`에는 동일한 처리 시각을 사용한다.
- 처리 시각은 DB의 `DATETIME(6)` 정밀도에 맞게 마이크로초 단위로 저장한다.
- 결제 대기 시간이 만료된 예약도 실패 처리할 수 있다.
- 만료된 예약도 실패 처리하여 예약 상태를 `CANCELED`로 변경하고 객실 점유를 해제한다.
- 결제 실패 처리에서는 예약의 결제 대기 만료 여부를 검증하지 않는다.

### 상태 변화

```text
Payment: READY → FAILED
Reservation: PENDING_PAYMENT → CANCELED
```

예약이 `CANCELED` 상태로 변경되면 활성 예약의 날짜 중복 조회 조건에서 제외되어 해당 객실과 기간을 다시 예약할 수 있다.

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `COMMON_001` | 결제 ID 등 요청값이 올바르지 않음 |
| `401` | `COMMON_003` | 인증되지 않은 요청 |
| `403` | `RESERVATION_ACCESS_DENIED` | 다른 회원 예약의 결제 실패 처리 요청 |
| `404` | `PAYMENT_NOT_FOUND` | 결제가 존재하지 않음 |
| `409` | `INVALID_PAYMENT_STATUS` | 결제 상태가 `READY`가 아님 |
| `409` | `RESERVATION_NOT_PAYABLE` | 예약 상태가 `PENDING_PAYMENT`가 아님 |

### 중복 실패 처리 응답 — 409 Conflict

```json
{
  "success": false,
  "code": "INVALID_PAYMENT_STATUS",
  "message": "현재 결제 상태에서는 요청을 처리할 수 없습니다."
}
```

### 다른 회원의 결제 실패 처리 응답 — 403 Forbidden

```json
{
  "success": false,
  "code": "RESERVATION_ACCESS_DENIED",
  "message": "해당 예약에 접근할 권한이 없습니다."
}
```

### 존재하지 않는 결제 응답 — 404 Not Found

```json
{
  "success": false,
  "code": "PAYMENT_NOT_FOUND",
  "message": "결제 정보를 찾을 수 없습니다."
}
```

### 처리 순서

```text
인증 회원 확인
→ 결제 조회
→ 결제 상태 확인
→ Payment 상태를 FAILED로 변경
→ failedAt 기록
→ 예약 소유자 확인
→ 예약 상태 확인
→ Reservation 상태를 CANCELED로 변경
→ canceledAt 기록
→ 결과 반환
```

결제 상태를 예약 검증보다 먼저 확인한다.

이미 처리된 결제를 다시 실패 처리하면 예약 상태와 관계없이 `INVALID_PAYMENT_STATUS`를 반환한다.

예약 소유자 또는 예약 상태 검증에 실패하면 먼저 변경된 `Payment`의 상태도 트랜잭션 롤백에 의해 `READY`로 복구된다.

### cURL 요청 예시

```bash
curl -X POST "http://localhost:8080/api/v1/payments/1/fail" \
  -H "Authorization: Bearer {accessToken}"
```

---

## 7. 상태 정의

### PaymentStatus

| 상태 | 설명 |
| --- | --- |
| `READY` | 결제 준비가 완료되어 성공 또는 실패 처리를 기다리는 상태 |
| `PAID` | 결제가 성공한 상태 |
| `FAILED` | 결제가 실패한 상태 |
| `REFUNDED` | 결제 금액이 환불된 상태 |

### ReservationStatus

| 상태 | 설명 |
| --- | --- |
| `PENDING_PAYMENT` | 예약이 생성되고 결제를 기다리는 상태 |
| `CONFIRMED` | 결제가 성공하여 예약이 확정된 상태 |
| `CANCELED` | 예약 취소 또는 결제 실패로 취소된 상태 |

### 상태 전이

#### 결제 준비

```text
Payment: 결제 없음 → READY
Reservation: PENDING_PAYMENT 유지
```

#### 결제 성공

```text
Payment: READY → PAID
Reservation: PENDING_PAYMENT → CONFIRMED
```

#### 결제 실패

```text
Payment: READY → FAILED
Reservation: PENDING_PAYMENT → CANCELED
```

---

## 8. 트랜잭션 규칙

결제 성공 및 실패 처리는 `PaymentFacade`의 하나의 트랜잭션 안에서 수행한다.

```text
PaymentController
→ PaymentFacade
   ├─ PaymentService
   └─ ReservationService
```

`PaymentController`는 `PaymentFacade`만 호출한다.

`PaymentFacade`는 결제 상태 변경과 예약 상태 변경에 필요한 두 Service를 조율한다.

`PaymentService`와 `ReservationService`는 서로 직접 호출하지 않는다.

### 결제 성공 트랜잭션

다음 두 상태 변경은 함께 성공하거나 함께 실패해야 한다.

```text
Payment READY → PAID
Reservation PENDING_PAYMENT → CONFIRMED
```

결제 금액 불일치, 만료된 예약, 잘못된 예약 상태 등의 예외가 발생하면 두 Entity의 상태 변경을 모두 롤백한다.

### 결제 실패 트랜잭션

다음 두 상태 변경은 함께 성공하거나 함께 실패해야 한다.

```text
Payment READY → FAILED
Reservation PENDING_PAYMENT → CANCELED
```

다른 회원의 예약이거나 예약 상태가 `PENDING_PAYMENT`가 아닌 경우, 먼저 변경된 `Payment`의 상태도 롤백한다.

```text
Payment FAILED → 트랜잭션 롤백 → READY 유지
Reservation PENDING_PAYMENT 유지
```

### 동일 처리 시각 사용

결제 성공 시 `approvedAt`을 예약 검증 기준 시각으로 함께 사용한다.

결제 실패 시 하나의 처리 시각을 생성하여 다음 필드에 동일하게 저장한다.

```text
Payment.failedAt
Reservation.canceledAt
```

---

## 9. 담당자별 구현 경계

| 담당자 | 구현 범위 |
| --- | --- |
| minjae123123 | Payment Controller·Facade·Service, 결제 Entity 상태 전이, 요청·응답 DTO, 결제 테스트 |
| IMSUN9 | Reservation Entity·Repository·Service와 예약 정책 |
| oHAHOo | JWT 인증, AuthMember, Security 설정 및 접근 제어 |

- `PaymentController`는 `PaymentFacade`만 호출한다.
- `PaymentFacade`는 결제 유스케이스를 조율한다.
- `PaymentService`는 `ReservationService`를 직접 호출하지 않는다.
- `ReservationService`는 `PaymentService`를 직접 호출하지 않는다.
- 결제 실패 처리에서는 `PaymentFacade`가 결제 실패와 예약 취소를 하나의 트랜잭션으로 조율한다.
- 다른 담당자의 공통 파일이나 도메인 코드를 수정하면 PR에 변경 이유와 영향 범위를 작성한다.

---

## 10. 테스트 범위

### 결제 준비

- [x] 결제 준비 성공
- [x] 다른 회원의 예약 접근 거절
- [x] 결제 불가능한 예약 상태 거절
- [x] 결제 만료 예약 거절
- [x] 동일 예약의 결제 중복 생성 거절
- [x] 미인증 요청 거절

### 결제 성공

- [x] `READY` 결제 승인 성공
- [x] 결제 상태가 `PAID`로 변경됨
- [x] 예약 상태가 `CONFIRMED`로 변경됨
- [x] `approvedAt` 저장 확인
- [x] 요청 금액 불일치 거절
- [x] 중복 승인 거절
- [x] 다른 회원의 결제 승인 거절
- [x] 존재하지 않는 결제 승인 거절
- [x] 만료된 예약 승인 거절
- [x] 미인증 요청 거절
- [x] 금액 누락 요청 거절
- [x] 음수 금액 요청 거절
- [x] 결제 금액 불일치 시 상태 롤백 확인
- [x] 예약 검증 실패 시 결제 상태 롤백 확인
- [x] 실제 H2 DB 기반 커밋 및 롤백 검증

### 결제 실패

- [x] `READY` 결제 실패 처리 성공
- [x] 결제 상태가 `FAILED`로 변경됨
- [x] 예약 상태가 `CANCELED`로 변경됨
- [x] `failedAt` 저장 확인
- [x] `canceledAt` 저장 확인
- [x] `failedAt`과 `canceledAt`에 동일한 시각 사용 확인
- [x] 이미 `PAID`인 결제의 실패 처리 거절
- [x] 이미 `FAILED`인 결제의 중복 실패 처리 거절
- [x] 다른 회원의 결제 실패 처리 거절
- [x] 존재하지 않는 결제 실패 처리 거절
- [x] 결제 대기 상태가 아닌 예약의 실패 처리 거절
- [x] 미인증 요청 거절
- [x] 만료된 예약의 결제 실패 처리 허용
- [x] 예약 소유자 검증 실패 시 결제 상태 롤백 확인
- [x] 결제 실패 후 예약 취소를 통한 객실 점유 해제
- [x] 실제 H2 DB 기반 커밋 및 롤백 검증

### 전체 테스트

```text
./gradlew clean test
BUILD SUCCESSFUL
```

---

## 11. 추후 구현 범위

- [ ] 사용자가 예약을 직접 취소할 때 기존 `READY` 결제의 처리 정책 확정
- [ ] 예약 취소 시 결제 환불
- [ ] `PAID` 결제의 환불 처리 및 `REFUNDED` 상태 전이
- [ ] 결제 승인과 예약 취소의 동시 요청 제어
- [ ] 동일 결제에 대한 동시 성공·실패 처리 방지
- [ ] 비관적 락, 낙관적 락 또는 조건부 상태 변경 방식 검토
- [ ] 실제 PG사 연동
- [ ] PG사 승인·실패 결과 검증
- [ ] 결제 실패 사유 코드 및 메시지 저장
