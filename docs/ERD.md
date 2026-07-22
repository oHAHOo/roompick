# RoomPick 전체 ERD 초안

- 문서 버전: `v0.1`
- 작성일: 2026-07-21
- 범위: RoomPick MVP 전체 도메인
- 포함 도메인: 회원·인증, 숙소, 객실, 예약, 결제
- 제외: 검색, 리뷰, 찜, 쿠폰, 포인트, 채팅, 알림, AI, 별도 환불 이력

---

## 1. 모델링 핵심 결정

1. `ROOM`은 객실 유형이나 수량이 아니라 실제 예약되는 물리적 객실 1개를 나타낸다.
2. 객실 예약 가능 여부는 `RESERVATION`의 날짜 겹침과 상태로 판단한다.
3. 현재 MVP에는 별도의 재고 테이블을 만들지 않는다.
4. 예약 당시 가격을 `RESERVATION`에 스냅샷으로 저장한다.
5. 하나의 예약에서 결제를 재시도할 수 있도록 `RESERVATION : PAYMENT`를 1:N으로 설계한다.
6. 회원 인증은 JWT 기반을 가정하여 토큰 테이블을 두지 않는다. 인증 방식 확정 후 변경할 수 있다.
7. 결제 취소는 MVP에서 `PAYMENT.status=REFUNDED`로 관리하고 별도 `REFUND` 테이블은 이후 버전에서 검토한다.
8. 예약·결제 데이터는 거래 이력이므로 숙소나 회원 삭제 시 함께 삭제하지 않는다.

---

## 2. ERD

```mermaid
erDiagram
    MEMBERS ||--o{ RESERVATIONS : creates
    ACCOMMODATIONS ||--o{ ROOMS : contains
    ROOMS ||--o{ RESERVATIONS : booked_for
    RESERVATIONS ||--o{ PAYMENTS : attempts

    MEMBERS {
        BIGINT member_id PK
        VARCHAR email UK
        VARCHAR password
        VARCHAR name
        VARCHAR role
        DATETIME created_at
        DATETIME updated_at
    }

    ACCOMMODATIONS {
        BIGINT accommodation_id PK
        VARCHAR name
        VARCHAR address
        TEXT description
        TIME check_in_time
        TIME check_out_time
        VARCHAR status
        DATETIME created_at
        DATETIME updated_at
    }

    ROOMS {
        BIGINT room_id PK
        BIGINT accommodation_id FK
        VARCHAR room_number
        VARCHAR name
        TEXT description
        BIGINT price_per_night
        INT standard_capacity
        INT max_capacity
        VARCHAR status
        DATETIME created_at
        DATETIME updated_at
    }

    RESERVATIONS {
        BIGINT reservation_id PK
        BIGINT member_id FK
        BIGINT room_id FK
        DATE check_in_date
        DATE check_out_date
        INT guest_count
        BIGINT price_per_night
        INT night_count
        BIGINT total_amount
        VARCHAR status
        DATETIME expires_at
        DATETIME canceled_at
        DATETIME created_at
        DATETIME updated_at
    }

    PAYMENTS {
        BIGINT payment_id PK
        BIGINT reservation_id FK
        BIGINT amount
        VARCHAR method
        VARCHAR provider
        VARCHAR provider_payment_key UK
        VARCHAR status
        VARCHAR failure_reason
        DATETIME paid_at
        DATETIME refunded_at
        DATETIME created_at
        DATETIME updated_at
    }
```

---

## 3. 관계 정의

| 부모 | 자식 | 관계 | 설명 |
| --- | --- | --- | --- |
| `MEMBERS` | `RESERVATIONS` | 1:N | 회원은 여러 예약을 생성할 수 있다. |
| `ACCOMMODATIONS` | `ROOMS` | 1:N | 숙소는 여러 실제 객실을 가질 수 있다. MVP에서는 1개다. |
| `ROOMS` | `RESERVATIONS` | 1:N | 객실은 날짜가 겹치지 않는 여러 예약 이력을 가질 수 있다. |
| `RESERVATIONS` | `PAYMENTS` | 1:N | 하나의 예약에 결제 재시도 이력이 여러 개 생길 수 있다. |

---

## 4. MEMBERS

회원과 인증에 필요한 최소 정보를 저장한다.

| 컬럼 | 타입 | Null | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `member_id` | `BIGINT` | N | PK | 회원 식별자 |
| `email` | `VARCHAR(255)` | N | UK | 로그인 이메일 |
| `password` | `VARCHAR(255)` | N |  | 암호화된 비밀번호 |
| `name` | `VARCHAR(50)` | N | UK | 회원 이름(닉네임 겸용) |
| `role` | `VARCHAR(20)` | N |  | `USER`, `ADMIN` |
| `created_at` | `DATETIME(6)` | N |  | 생성 시각 |
| `updated_at` | `DATETIME(6)` | N |  | 수정 시각 |

### 제약·인덱스

- `UNIQUE(email)`
- `UNIQUE(name)` — 회원가입 시 중복 이름을 허용하지 않는다.
- 비밀번호 원문 저장 금지
- 탈퇴 기능 도입 시 물리 삭제보다 상태 컬럼 추가를 검토

---

## 5. ACCOMMODATIONS

객실이 소속된 숙소 정보를 저장한다.

| 컬럼 | 타입 | Null | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `accommodation_id` | `BIGINT` | N | PK | 숙소 식별자 |
| `name` | `VARCHAR(100)` | N |  | 숙소명 |
| `address` | `VARCHAR(255)` | N |  | 숙소 주소 |
| `description` | `TEXT` | Y |  | 숙소 설명 |
| `check_in_time` | `TIME` | N |  | 기본 체크인 시간 |
| `check_out_time` | `TIME` | N |  | 기본 체크아웃 시간 |
| `status` | `VARCHAR(20)` | N |  | `ACTIVE`, `INACTIVE` |
| `created_at` | `DATETIME(6)` | N |  | 생성 시각 |
| `updated_at` | `DATETIME(6)` | N |  | 수정 시각 |

### 정책

- MVP에서는 더미데이터 1개만 등록한다.
- 거래 이력 보호를 위해 숙소를 물리 삭제하지 않고 `INACTIVE`로 변경하는 방식을 사용한다.

---

## 6. ROOMS

실제로 예약되는 객실 정보를 저장한다.

| 컬럼 | 타입 | Null | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `room_id` | `BIGINT` | N | PK | 객실 식별자 |
| `accommodation_id` | `BIGINT` | N | FK | 소속 숙소 ID |
| `room_number` | `VARCHAR(30)` | N | UK 조합 | 숙소 내 객실 번호 |
| `name` | `VARCHAR(100)` | N |  | 화면에 표시할 객실명 |
| `description` | `TEXT` | Y |  | 객실 설명 |
| `price_per_night` | `BIGINT` | N |  | 현재 1박 가격, 원 단위 |
| `standard_capacity` | `INT` | N |  | 기준 인원 |
| `max_capacity` | `INT` | N |  | 최대 인원 |
| `status` | `VARCHAR(20)` | N |  | `ACTIVE`, `INACTIVE` |
| `created_at` | `DATETIME(6)` | N |  | 생성 시각 |
| `updated_at` | `DATETIME(6)` | N |  | 수정 시각 |

### 제약·인덱스

- `FK accommodation_id → accommodations.accommodation_id`
- `UNIQUE(accommodation_id, room_number)`
- `price_per_night >= 0`
- `standard_capacity >= 1`
- `max_capacity >= standard_capacity`

### 정책

- MVP에서는 더미데이터 1개만 등록한다.
- 객실 가격 변경이 과거 예약 금액에 영향을 주지 않도록 예약 생성 시 가격을 복사한다.
- 객실을 운영 중지해도 기존 예약 이력은 유지한다.

---

## 7. RESERVATIONS

회원의 숙박 예약과 예약 당시 가격을 저장한다.

| 컬럼 | 타입 | Null | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `reservation_id` | `BIGINT` | N | PK | 예약 식별자 |
| `member_id` | `BIGINT` | N | FK | 예약 회원 ID |
| `room_id` | `BIGINT` | N | FK | 예약 객실 ID |
| `check_in_date` | `DATE` | N |  | 체크인 날짜 |
| `check_out_date` | `DATE` | N |  | 체크아웃 날짜 |
| `guest_count` | `INT` | N |  | 예약 인원 |
| `price_per_night` | `BIGINT` | N |  | 예약 당시 1박 가격 스냅샷 |
| `night_count` | `INT` | N |  | 숙박 일수 |
| `total_amount` | `BIGINT` | N |  | 최종 예약 금액 스냅샷 |
| `status` | `VARCHAR(30)` | N |  | 예약 상태 |
| `expires_at` | `DATETIME(6)` | Y |  | 결제 대기 만료 시각 |
| `canceled_at` | `DATETIME(6)` | Y |  | 예약 취소 시각 |
| `created_at` | `DATETIME(6)` | N |  | 생성 시각 |
| `updated_at` | `DATETIME(6)` | N |  | 수정 시각 |

### 예약 상태

```text
PENDING_PAYMENT
CONFIRMED
CANCELED
EXPIRED
COMPLETED
```

### 제약·인덱스

- `FK member_id → members.member_id`
- `FK room_id → rooms.room_id`
- 인덱스: `(member_id, created_at)` — 내 예약 목록 최신순 조회
- 인덱스: `(room_id, status, check_in_date, check_out_date)` — 활성 예약 겹침 조회
- `check_in_date < check_out_date`
- `guest_count >= 1`
- `night_count >= 1`
- `price_per_night >= 0`
- `total_amount >= 0`

### 날짜 겹침 규칙

다음 조건을 만족하는 활성 예약이 존재하면 예약할 수 없다.

```text
existing.check_in_date < requested.check_out_date
AND
existing.check_out_date > requested.check_in_date
```

활성 예약:

```text
status = CONFIRMED
OR
(status = PENDING_PAYMENT AND expires_at > CURRENT_TIMESTAMP)
```

### 가격 정합성

```text
night_count = check_out_date - check_in_date
total_amount = price_per_night × night_count
```

DB에 계산 결과를 저장하되 생성 시 Service에서 다시 검증한다.

---

## 8. PAYMENTS

예약의 결제 시도와 결과를 저장한다.

| 컬럼 | 타입 | Null | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `payment_id` | `BIGINT` | N | PK | 결제 식별자 |
| `reservation_id` | `BIGINT` | N | FK | 결제 대상 예약 ID |
| `amount` | `BIGINT` | N |  | 결제 요청 금액 |
| `method` | `VARCHAR(30)` | N |  | 결제 수단 |
| `provider` | `VARCHAR(30)` | N |  | `FAKE`, 추후 실제 PG사 |
| `provider_payment_key` | `VARCHAR(255)` | Y | UK | 외부 결제 고유 키 |
| `status` | `VARCHAR(30)` | N |  | 결제 상태 |
| `failure_reason` | `VARCHAR(500)` | Y |  | 결제 실패 사유 |
| `paid_at` | `DATETIME(6)` | Y |  | 결제 완료 시각 |
| `refunded_at` | `DATETIME(6)` | Y |  | 전액 환불 완료 시각 |
| `created_at` | `DATETIME(6)` | N |  | 생성 시각 |
| `updated_at` | `DATETIME(6)` | N |  | 수정 시각 |

### 결제 상태

```text
READY
PAID
FAILED
REFUNDED
```

### 제약·인덱스

- `FK reservation_id → reservations.reservation_id`
- 인덱스: `(reservation_id, created_at)` — 예약별 결제 시도 조회
- `UNIQUE(provider_payment_key)` — 값이 존재할 때 외부 결제 중복 반영 방지
- `amount >= 0`
- 하나의 예약에는 최종적으로 하나의 `PAID` 결제만 허용한다. MySQL에서는 Service 검증과 락 또는 멱등성 키로 보장한다.

### 정책

- 결제 금액과 `reservations.total_amount`가 일치해야 한다.
- 결제 실패 기록은 삭제하지 않는다.
- 전액 환불 MVP는 성공한 결제의 상태를 `REFUNDED`로 변경한다.
- 부분 환불과 여러 환불 이력이 필요해지면 별도의 `refunds` 테이블을 추가한다.

---

## 9. 삭제·변경 정책

| 대상 | 정책 |
| --- | --- |
| 회원 | 예약 이력이 있으면 물리 삭제하지 않고 추후 상태 기반 탈퇴 처리 검토 |
| 숙소 | 물리 삭제 대신 `INACTIVE` |
| 객실 | 물리 삭제 대신 `INACTIVE` |
| 예약 | 거래 이력이므로 삭제하지 않고 상태 변경 |
| 결제 | 결제·감사 이력이므로 삭제하지 않음 |

외래 키에 무조건 `ON DELETE CASCADE`를 사용하지 않는다.

---

## 10. 담당자별 테이블

| 담당자 | 담당 테이블 |
| --- | --- |
| 임선구 | `accommodations`, `rooms`, `reservations` |
| 팀원 A | `payments` |
| 팀원 B | `members` |

여러 테이블을 함께 변경하는 유스케이스는 Facade에서 조율하며 다른 담당자의 Repository를 직접 사용하지 않는다.

---

## 11. 버전 확장 시 검토할 테이블

현재 MVP ERD에는 만들지 않는다.

| 이후 기능 | 후보 테이블 |
| --- | --- |
| 별도 환불 이력·부분 환불 | `refunds` |
| 객실 유형별 수량 재고 | `room_types`, `room_daily_inventories` |
| 리뷰 | `reviews` |
| 찜 | `favorites` |
| Refresh Token | `refresh_tokens` 또는 Redis |
| AI 구조화 결과 | `ai_recommendations`, `review_summaries` |
| 프롬프트 외부화·이력 | `prompt_templates`, `prompt_versions` |
| AI 요청 비용·지연 | `ai_usage_logs` 또는 메트릭 저장소 |

---

## 12. 팀 회의에서 최종 확정할 항목

- [ ] `ROOM`을 실제 객실 단위로 관리할지 객실 유형·수량 방식으로 관리할지
- [ ] 결제 재시도를 고려해 예약과 결제를 1:N으로 유지할지
- [ ] 결제 대기 만료 시각 `expires_at`을 MVP부터 사용할지
- [ ] JWT 인증을 사용할지
- [ ] MVP부터 전액 환불을 처리할지
- [ ] 숙소·객실을 물리 삭제하지 않는 정책을 확정할지
- [ ] 예약 겹침 동시성 제어에 사용할 락 전략
- [ ] DB 체크 제약조건을 마이그레이션에 포함할지

확정 후 API 명세와 ERD를 동시에 `v0.2`로 갱신한다.
