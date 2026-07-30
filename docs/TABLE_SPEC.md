# RoomPick 테이블 명세서

- 프로젝트명: **RoomPick(룸픽)**
- 문서 버전: `v0.6`
- 작성일: 2026-07-21
- 최종 수정일: 2026-07-28
- DBMS: MySQL 8.4
- 문자 집합: `utf8mb4`
- 범위: RoomPick MVP 전체 도메인
- 기준 문서: `docs/ERD.md`, `docs/ERD.dbml`, `docs/policy/ROOM_POLICY.md`

이 문서는 RoomPick MVP에서 사용하는 테이블과 컬럼, 키, 제약조건, 인덱스를 구현 가능한 수준으로 정의한다. ERD의 관계가 변경되면 이 문서와 `docs/ERD.md`를 함께 수정한다. 객실 상태 의미는 `docs/policy/ROOM_POLICY.md`를 우선 기준으로 삼는다.

---

## 1. 표기 규칙

| 표기 | 의미 |
| --- | --- |
| PK | Primary Key, 기본 키 |
| FK | Foreign Key, 외래 키 |
| UK | Unique Key, 고유 키 |
| N | `NULL`을 허용하지 않음 |
| Y | `NULL`을 허용함 |
| `-` | DB 기본값 또는 키가 별도로 없음 |

### 공통 원칙

- 모든 PK는 `BIGINT AUTO_INCREMENT`를 사용한다.
- 생성·수정 시각은 JPA Auditing으로 관리한다.
- Enum 값은 순서 번호가 아닌 문자열로 저장한다.
- 예약·결제 이력 보호를 위해 외래 키에 `ON DELETE CASCADE`를 사용하지 않는다.
- 금액은 소수점 없이 원 단위 `BIGINT`로 저장한다.
- 시간대는 애플리케이션 기준 `Asia/Seoul`을 사용한다.

---

## 2. 전체 테이블 목록

| 논리 테이블명 | 물리 테이블명 | 설명 | 담당자 |
| --- | --- | --- | --- |
| 회원 | `members` | 회원 및 인증에 필요한 최소 정보 | oHAHOo |
| 숙소 | `accommodations` | 객실이 소속된 숙소 정보 | IMSUN9 |
| 객실 | `rooms` | 실제로 예약되는 물리적 객실 정보 | IMSUN9 |
| 예약 | `reservations` | 회원의 숙박 예약 및 예약 당시 가격 정보 | IMSUN9 |
| 결제 | `payments` | MVP 기준 예약별 단일 결제 정보와 처리 결과 | minjae123123 |

---

## 3. MEMBERS — 회원

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 물리 테이블명 | `members` |
| 설명 | 회원과 인증에 필요한 최소 정보를 저장한다. |
| Primary Key | `member_id` |
| 담당자 | oHAHOo |

### 컬럼 명세

| 물리 컬럼명 | 논리 컬럼명 | 데이터 타입 | Null | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `member_id` | 회원 ID | `BIGINT` | N | `AUTO_INCREMENT` | PK | 회원 식별자 |
| `email` | 이메일 | `VARCHAR(255)` | N | - | UK | 로그인 이메일 |
| `password` | 비밀번호 | `VARCHAR(255)` | N | - | - | 단방향 암호화된 비밀번호 |
| `name` | 회원 이름 | `VARCHAR(50)` | N | - | - | 사용자 화면에 표시할 이름 |
| `role` | 회원 권한 | `VARCHAR(20)` | N | `USER` | - | `USER`, `ADMIN` |
| `created_at` | 생성 시각 | `DATETIME(6)` | N | - | - | 회원 생성 시각 |
| `updated_at` | 수정 시각 | `DATETIME(6)` | N | - | - | 회원 최종 수정 시각 |

### 키·제약조건·인덱스

| 구분 | 이름 | 대상 | 설명 |
| --- | --- | --- | --- |
| PK | `pk_members` | `member_id` | 회원 기본 키 |
| UK | `uk_members_email` | `email` | 이메일 중복 가입 방지 |

### 정책

- 비밀번호 원문은 저장하지 않는다.
- 일반 회원가입의 기본 권한은 `USER`이며 클라이언트 요청으로 `ADMIN`을 선택할 수 없다.
- 최초 관리자 계정은 회원·인증 담당자가 정한 별도 방식으로 준비하고 비밀번호를 저장소에 커밋하지 않는다.
- 회원 탈퇴 기능 도입 시 물리 삭제 대신 상태 컬럼 추가를 검토한다.
- 예약 이력이 있는 회원은 물리 삭제하지 않는다.

---

## 4. ACCOMMODATIONS — 숙소

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 물리 테이블명 | `accommodations` |
| 설명 | 객실이 소속된 숙소의 기본 정보를 저장한다. |
| Primary Key | `accommodation_id` |
| 담당자 | IMSUN9 |

### 컬럼 명세

| 물리 컬럼명 | 논리 컬럼명 | 데이터 타입 | Null | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `accommodation_id` | 숙소 ID | `BIGINT` | N | `AUTO_INCREMENT` | PK | 숙소 식별자 |
| `name` | 숙소명 | `VARCHAR(100)` | N | - | - | 사용자 화면에 표시할 숙소 이름 |
| `address` | 숙소 주소 | `VARCHAR(255)` | N | - | - | 숙소의 전체 주소 |
| `description` | 숙소 설명 | `TEXT` | Y | - | - | 숙소 상세 설명 |
| `check_in_time` | 체크인 시간 | `TIME` | N | - | - | 기본 체크인 시간 |
| `check_out_time` | 체크아웃 시간 | `TIME` | N | - | - | 기본 체크아웃 시간 |
| `status` | 숙소 상태 | `VARCHAR(20)` | N | `ACTIVE` | - | `ACTIVE`, `INACTIVE` |
| `created_at` | 생성 시각 | `DATETIME(6)` | N | - | - | 숙소 생성 시각 |
| `updated_at` | 수정 시각 | `DATETIME(6)` | N | - | - | 숙소 최종 수정 시각 |

### 키·제약조건·인덱스

| 구분 | 이름 | 대상 | 설명 |
| --- | --- | --- | --- |
| PK | `pk_accommodations` | `accommodation_id` | 숙소 기본 키 |

### 상태값

| 값 | 설명 |
| --- | --- |
| `ACTIVE` | 운영 중인 숙소 |
| `INACTIVE` | 운영이 중지된 숙소 |

### 정책

- 숙소는 관리자 등록 API를 통해 생성하며 생성 상태는 `ACTIVE`이다.
- MVP 시연에는 관리자가 등록한 숙소를 최소 1개 사용하되 DB에서 개수를 강제로 제한하지 않는다.
- 거래 이력 보호를 위해 숙소를 물리 삭제하지 않고 `INACTIVE`로 변경한다.

---

## 5. ROOMS — 객실

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 물리 테이블명 | `rooms` |
| 설명 | 실제로 예약되는 물리적 객실 정보를 저장한다. |
| Primary Key | `room_id` |
| 담당자 | IMSUN9 |

### 컬럼 명세

| 물리 컬럼명 | 논리 컬럼명 | 데이터 타입 | Null | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `room_id` | 객실 ID | `BIGINT` | N | `AUTO_INCREMENT` | PK | 객실 식별자 |
| `accommodation_id` | 숙소 ID | `BIGINT` | N | - | FK | 객실이 소속된 숙소 ID |
| `room_number` | 객실 번호 | `VARCHAR(30)` | N | - | UK 조합 | 같은 숙소 안에서 사용하는 실제 객실 번호 |
| `name` | 객실명 | `VARCHAR(100)` | N | - | - | 사용자 화면에 표시할 객실 이름 |
| `description` | 객실 설명 | `TEXT` | Y | - | - | 객실 상세 설명 |
| `price_per_night` | 1박 가격 | `BIGINT` | N | - | - | 현재 1박 가격, 원 단위 |
| `standard_capacity` | 기준 인원 | `INT` | N | - | - | 객실 기본 이용 인원 |
| `max_capacity` | 최대 인원 | `INT` | N | - | - | 객실에서 허용하는 최대 이용 인원 |
| `status` | 객실 공개 상태 | `VARCHAR(20)` | N | `INACTIVE` | - | `ACTIVE`, `INACTIVE`; `SOLD_OUT`은 저장하지 않음 |
| `created_at` | 생성 시각 | `DATETIME(6)` | N | - | - | 객실 생성 시각 |
| `updated_at` | 수정 시각 | `DATETIME(6)` | N | - | - | 객실 최종 수정 시각 |

### 키·제약조건·인덱스

| 구분 | 이름 | 대상 | 설명 |
| --- | --- | --- | --- |
| PK | `pk_rooms` | `room_id` | 객실 기본 키 |
| FK | `fk_rooms_accommodation` | `accommodation_id → accommodations.accommodation_id` | 객실의 소속 숙소 참조 |
| UK | `uk_rooms_accommodation_room_number` | `accommodation_id, room_number` | 같은 숙소의 객실 번호 중복 방지 |
| CHECK | `chk_rooms_price_per_night` | `price_per_night >= 0` | 음수 가격 방지 |
| CHECK | `chk_rooms_standard_capacity` | `standard_capacity >= 1` | 기준 인원은 1명 이상 |
| CHECK | `chk_rooms_max_capacity` | `max_capacity >= standard_capacity` | 최대 인원은 기준 인원 이상 |

### DB 저장 상태값

| 값 | 설명 |
| --- | --- |
| `ACTIVE` | 사용자에게 공개된 객실 |
| `INACTIVE` | 사용자에게 공개되지 않는 객실 |

### 화면 표시 상태

| 값 | 설명 |
| --- | --- |
| `ACTIVE` | 선택한 숙박 기간에 활성 예약이 없어 예약 가능한 상태 |
| `SOLD_OUT` | 선택한 숙박 기간에 활성 예약이 있어 예약 불가능한 상태 |

### 정책

- 객실은 관리자가 존재하는 숙소에 등록하며 생성 상태는 `INACTIVE`이다.
- 관리자가 공개한 `ACTIVE` 객실만 사용자용 조회에 포함한다.
- `INACTIVE` 객실은 사용자 화면에 노출하지 않고 신규 예약도 허용하지 않는다.
- `SOLD_OUT`은 `rooms.status`에 저장하지 않으며 사용자가 선택한 숙박 기간의 활성 예약 존재 여부로 계산한다.
- 같은 객실이라도 선택한 날짜에 따라 `ACTIVE` 또는 `SOLD_OUT`으로 다르게 표시될 수 있다.
- 예약 생성·취소·결제 실패·결제 대기 만료 시 객실 상태를 `SOLD_OUT` 또는 `ACTIVE`로 직접 변경하지 않는다.
- MVP 시연에는 관리자가 등록한 실제 객실을 최소 1개 사용하되 DB에서 개수를 강제로 제한하지 않는다.
- 객실 가격은 예약 생성 시 `reservations.price_per_night`에 복사한다.
- 객실 가격이 변경되어도 기존 예약의 금액은 변경하지 않는다.
- 운영 중지된 객실도 기존 예약 이력을 위해 물리 삭제하지 않는다.
- 자세한 상태 계산 규칙은 `docs/policy/ROOM_POLICY.md`를 따른다.

---

## 6. RESERVATIONS — 예약

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 물리 테이블명 | `reservations` |
| 설명 | 회원의 숙박 예약과 예약 당시 가격 스냅샷을 저장한다. |
| Primary Key | `reservation_id` |
| 담당자 | IMSUN9 |

### 컬럼 명세

| 물리 컬럼명 | 논리 컬럼명 | 데이터 타입 | Null | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `reservation_id` | 예약 ID | `BIGINT` | N | `AUTO_INCREMENT` | PK | 예약 식별자 |
| `member_id` | 회원 ID | `BIGINT` | N | - | FK | 예약을 생성한 회원 ID |
| `room_id` | 객실 ID | `BIGINT` | N | - | FK | 예약 대상 객실 ID |
| `check_in_date` | 체크인 날짜 | `DATE` | N | - | - | 숙박 시작 날짜 |
| `check_out_date` | 체크아웃 날짜 | `DATE` | N | - | - | 숙박 종료 날짜이며 숙박일에는 포함하지 않음 |
| `guest_count` | 예약 인원 | `INT` | N | - | - | 실제 숙박 인원 |
| `price_per_night` | 예약 당시 1박 가격 | `BIGINT` | N | - | - | 예약 생성 시 복사한 가격 스냅샷 |
| `night_count` | 숙박 일수 | `INT` | N | - | - | 체크아웃 날짜와 체크인 날짜의 차이 |
| `total_amount` | 총 예약 금액 | `BIGINT` | N | - | - | `price_per_night × night_count` |
| `status` | 예약 상태 | `VARCHAR(30)` | N | - | - | 예약 처리 상태 |
| `expires_at` | 결제 대기 만료 시각 | `DATETIME(6)` | Y | - | - | 결제 대기 예약의 만료 시각 |
| `canceled_at` | 예약 취소 시각 | `DATETIME(6)` | Y | - | - | 예약이 취소된 시각 |
| `created_at` | 생성 시각 | `DATETIME(6)` | N | - | - | 예약 생성 시각 |
| `updated_at` | 수정 시각 | `DATETIME(6)` | N | - | - | 예약 최종 수정 시각 |

### 키·제약조건·인덱스

| 구분 | 이름 | 대상 | 설명 |
| --- | --- | --- | --- |
| PK | `pk_reservations` | `reservation_id` | 예약 기본 키 |
| FK | `fk_reservations_member` | `member_id → members.member_id` | 예약 회원 참조 |
| FK | `fk_reservations_room` | `room_id → rooms.room_id` | 예약 객실 참조 |
| INDEX | `idx_reservations_member_created_at` | `member_id, created_at` | 내 예약 목록 최신순 조회 |
| INDEX | `idx_reservations_room_status_stay_period` | `room_id, status, check_in_date, check_out_date` | 활성 예약 날짜 겹침 조회 |
| CHECK | `chk_reservations_stay_period` | `check_in_date < check_out_date` | 잘못된 숙박 기간 방지 |
| CHECK | `chk_reservations_guest_count` | `guest_count >= 1` | 예약 인원은 1명 이상 |
| CHECK | `chk_reservations_night_count` | `night_count >= 1` | 숙박 일수는 1일 이상 |
| CHECK | `chk_reservations_price_per_night` | `price_per_night >= 0` | 음수 가격 방지 |
| CHECK | `chk_reservations_total_amount` | `total_amount >= 0` | 음수 총액 방지 |

### 상태값

| 값 | 설명 |
| --- | --- |
| `PENDING_PAYMENT` | 예약 생성 후 결제를 기다리는 상태 |
| `CONFIRMED` | 결제에 성공해 예약이 확정된 상태 |
| `CANCELED` | 사용자 취소 또는 결제 실패로 취소된 상태 |
| `EXPIRED` | 결제 대기 시간이 만료된 상태 |
| `COMPLETED` | 체크아웃까지 완료된 상태, 후속 버전에서 자동화 검토 |

### 정책

- 예약 생성 시 객실의 가격, 숙박 일수, 총액을 스냅샷으로 저장한다.
- 활성 예약의 날짜 겹침 조건은 `기존 체크인 < 요청 체크아웃 AND 기존 체크아웃 > 요청 체크인`이다.
- `CONFIRMED` 예약과 만료 전 `PENDING_PAYMENT` 예약만 객실 예약 가능 여부를 막고 해당 기간을 `SOLD_OUT`으로 계산하게 한다.
- `CANCELED`, `EXPIRED`, `COMPLETED` 예약은 신규 예약을 막지 않는다.
- 예약 상태가 바뀌면 객실 DB 상태를 직접 변경하지 않고 예약 가능 여부를 다시 계산한다.
- 예약은 거래 이력이므로 물리 삭제하지 않고 상태를 변경한다.

---

## 7. PAYMENTS — 결제

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 물리 테이블명 | `payments` |
| 설명 | MVP 기준으로 예약별 하나의 결제 정보와 성공·실패·환불 상태를 저장한다. |
| Primary Key | `payment_id` |
| 담당자 | minjae123123 |

### 컬럼 명세

| 물리 컬럼명 | 논리 컬럼명 | 데이터 타입 | Null | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `payment_id` | 결제 ID | `BIGINT` | N | `AUTO_INCREMENT` | PK | 결제 식별자 |
| `reservation_id` | 예약 ID | `BIGINT` | N | - | FK, UK | 결제 대상 예약 ID. MVP에서는 예약당 결제 1건만 허용 |
| `amount` | 결제 금액 | `BIGINT` | N | - | - | 예약의 `total_amount`를 복사한 원 단위 금액 |
| `status` | 결제 상태 | `VARCHAR(20)` | N | - | - | `READY`, `PAID`, `FAILED`, `REFUNDED` |
| `approved_at` | 결제 승인 시각 | `DATETIME(6)` | Y | - | - | Mock 결제가 승인된 시각 |
| `failed_at` | 결제 실패 시각 | `DATETIME(6)` | Y | - | - | Mock 결제가 실패 처리된 시각 |
| `created_at` | 생성 시각 | `DATETIME(6)` | N | - | - | 결제 생성 시각 |
| `updated_at` | 수정 시각 | `DATETIME(6)` | N | - | - | 결제 정보 최종 수정 시각 |

### 키·제약조건·인덱스

| 구분 | 이름 | 대상 | 설명 |
| --- | --- | --- | --- |
| PK | `pk_payments` | `payment_id` | 결제 기본 키 |
| FK | `fk_payments_reservation` | `reservation_id → reservations.reservation_id` | 결제 대상 예약 참조 |
| UK | `uk_payments_reservation_id` | `reservation_id` | MVP에서 예약 1건당 결제 1건만 허용 |
| CHECK | `chk_payments_amount` | `amount >= 0` | 음수 결제 금액 방지 |

### 상태값

| 값 | 설명 |
| --- | --- |
| `READY` | 결제 정보가 생성되고 Mock 승인을 기다리는 상태 |
| `PAID` | Mock 결제 승인 완료 상태 |
| `FAILED` | Mock 결제 실패 상태 |
| `REFUNDED` | 전액 환불 완료 상태 |

### 예약-결제 관계 정책

| 단계 | 관계 | 적용 방식 |
| --- | --- | --- |
| 현재 MVP | 예약 `1` : 결제 `1` | `payments.reservation_id`에 Unique Constraint를 적용하여 예약당 하나의 결제만 생성 |
| 후속 확장 | 예약 `1` : 결제 `N` | 재결제·결제 수단 변경·PG 재시도 이력 요구가 생기면 Unique Constraint를 제거하고 결제 시도 이력 구조로 전환 |

> 현재 테이블 구조, ERD 관계, 외래 키 관계표, DBML 및 Flyway V4는 모두 MVP 기준 `1:1`만 표현한다. 후속 `1:N` 전환 계획은 정책 메모이며 현재 스키마 정의에는 반영하지 않는다.

### 정책

- 결제 준비 시 클라이언트 요청 금액이 아니라 `reservations.total_amount`를 Payment에 복사한다.
- 결제 금액은 `reservations.total_amount`와 일치해야 한다.
- MVP에서는 결제 실패 후 새로운 Payment 행을 생성하는 재결제 기능을 제공하지 않는다.
- Mock 결제 성공 시 `Payment.status`를 `PAID`, 예약 상태를 `CONFIRMED`로 변경한다.
- Mock 결제 실패 시 `Payment.status`를 `FAILED`, 예약 상태를 `CANCELED`로 변경하여 해당 기간의 예약 점유를 해제한다.
- 후속 버전에서 여러 결제 시도 이력이 필요해지면 `uk_payments_reservation_id`를 제거하고 예약 `1` : 결제 `N` 구조로 변경한다.
- 전액 환불은 성공한 결제의 상태를 `REFUNDED`로 변경한다.
- 부분 환불과 여러 환불 이력이 필요해지면 별도의 `refunds` 테이블을 추가한다.
- 결제 데이터는 감사 이력이므로 물리 삭제하지 않는다.

---

## 8. 외래 키 관계

| 자식 테이블 | 외래 키 컬럼 | 부모 테이블 | 참조 컬럼 | 관계 |
| --- | --- | --- | --- | --- |
| `rooms` | `accommodation_id` | `accommodations` | `accommodation_id` | N:1 |
| `reservations` | `member_id` | `members` | `member_id` | N:1 |
| `reservations` | `room_id` | `rooms` | `room_id` | N:1 |
| `payments` | `reservation_id` | `reservations` | `reservation_id` | 1:1 |

---

## 9. 삭제·변경 정책

| 대상 | 정책 |
| --- | --- |
| 회원 | 예약 이력이 있으면 물리 삭제하지 않고 상태 기반 탈퇴 처리를 검토한다. |
| 숙소 | 물리 삭제하지 않고 `INACTIVE`로 변경한다. |
| 객실 | 물리 삭제하지 않고 `INACTIVE`로 변경한다. |
| 예약 | 거래 이력이므로 삭제하지 않고 상태를 변경한다. |
| 결제 | 결제·감사 이력이므로 삭제하지 않는다. |

외래 키에는 무조건적인 `ON DELETE CASCADE`를 적용하지 않는다.

---

## 10. 구현 전 최종 확인 항목

- [x] 회원 인증 방식을 JWT로 확정할지 → JWT로 확정. 로그아웃은 추후 JWT + Redis로 구현
- [ ] 최초 관리자 계정을 준비하는 방식을 무엇으로 할지
- [ ] 결제 대기 만료 시각 `expires_at`을 MVP부터 사용할지
- [x] 예약과 결제 관계 → 현재 MVP 및 관련 ERD·DBML·테이블 명세·Flyway V4는 `1:1`로 확정하고, 후속 버전에서만 `1:N` 전환을 검토
- [ ] MVP에서 전액 환불을 처리할지
- [ ] 예약 겹침 동시성 제어 방식을 무엇으로 할지
- [ ] 모든 CHECK 제약조건을 Flyway 마이그레이션에 포함할지

위 항목 또는 객실 상태 정책이 변경되면 `docs/ERD.md`, `docs/ERD.dbml`, `docs/policy/ROOM_POLICY.md`, 이 문서를 함께 갱신한다.
