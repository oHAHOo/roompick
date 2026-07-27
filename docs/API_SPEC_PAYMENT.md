# RoomPick API 명세서 — 조민재 담당

- 문서 버전: `v0.1`
- 작성일: 2026-07-27
- 담당자: minjae123123
- 담당 기능: 결제 준비, Mock 결제 성공 처리
- 협업 도메인: 예약, 회원·인증·보안

이 문서는 RoomPick MVP의 결제 준비 및 Mock 결제 승인 API와 결제·예약 상태 변경 규칙을 정의한다.

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
9. 결제와 예약의 상태 변경은 하나의 트랜잭션에서 처리한다.
10. Controller는 PaymentFacade만 호출한다.
11. PaymentFacade가 PaymentService와 ReservationService를 조율한다.
12. PaymentService와 ReservationService는 서로 직접 호출하지 않는다.

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
→ 결제와 연결된 예약 조회
→ 예약 소유자 확인
→ 예약 상태 및 만료 여부 확인
→ 요청 금액과 저장된 결제 금액 비교
→ Payment 상태를 PAID로 변경
→ approvedAt 기록
→ Reservation 상태를 CONFIRMED로 변경
→ 결과 반환
```

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

## 6. 상태 정의

### PaymentStatus

| 상태 | 설명 |
| --- | --- |
| `READY` | 결제 준비가 완료되어 승인을 기다리는 상태 |
| `PAID` | 결제가 성공한 상태 |
| `FAILED` | 결제가 실패한 상태 |
| `REFUNDED` | 결제 금액이 환불된 상태 |

### ReservationStatus

| 상태 | 설명 |
| --- | --- |
| `PENDING_PAYMENT` | 예약이 생성되고 결제를 기다리는 상태 |
| `CONFIRMED` | 결제가 성공하여 예약이 확정된 상태 |
| `CANCELED` | 예약이 취소된 상태 |

---

## 7. 트랜잭션 규칙

결제 성공 처리는 `PaymentFacade`의 하나의 트랜잭션 안에서 수행한다.

```text
PaymentController
→ PaymentFacade
   ├─ PaymentService
   └─ ReservationService
```

다음 두 상태 변경은 함께 성공하거나 함께 실패해야 한다.

```text
Payment READY → PAID
Reservation PENDING_PAYMENT → CONFIRMED
```

결제 금액 불일치, 만료된 예약, 잘못된 예약 상태 등의 예외가 발생하면 두 Entity의 상태 변경을 모두 롤백한다.

---

## 8. 담당자별 구현 경계

| 담당자 | 구현 범위 |
| --- | --- |
| minjae123123 | Payment Controller·Facade·Service, 결제 Entity 상태 전이, 요청·응답 DTO, 결제 테스트 |
| IMSUN9 | Reservation Entity·Repository·Service와 예약 정책 |
| oHAHOo | JWT 인증, AuthMember, Security 설정 및 접근 제어 |

- PaymentController는 PaymentFacade만 호출한다.
- PaymentFacade는 결제 유스케이스를 조율한다.
- PaymentService는 ReservationService를 직접 호출하지 않는다.
- ReservationService는 PaymentService를 직접 호출하지 않는다.
- 다른 담당자의 공통 파일이나 도메인 코드를 수정하면 PR에 변경 이유와 영향 범위를 작성한다.

---

## 9. 테스트 범위

### 결제 준비

- [x] 결제 준비 성공
- [x] 다른 회원의 예약 접근 거절
- [x] 결제 불가능한 예약 상태 거절
- [x] 결제 만료 예약 거절
- [x] 동일 예약의 결제 중복 생성 거절

### 결제 성공

- [x] `READY` 결제 승인 성공
- [x] 결제 상태가 `PAID`로 변경됨
- [x] 예약 상태가 `CONFIRMED`로 변경됨
- [x] 요청 금액 불일치 거절
- [x] 중복 승인 거절
- [x] 미인증 요청 거절
- [x] 금액 누락 요청 거절
- [x] 음수 금액 요청 거절

---

## 10. 추후 구현 범위

- [ ] Mock 결제 실패 처리
- [ ] 결제 실패 시 예약 취소
- [ ] 결제 실패 시 객실 점유 해제
- [ ] 예약 취소 시 결제 환불
- [ ] 동시 결제 승인 방지
- [ ] 실제 PG사 연동
