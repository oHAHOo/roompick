# RoomPick 테이블 명세서

- 프로젝트명: **RoomPick(룸픽)**
- 문서 버전: `v0.2`
- 작성일: 2026-07-21
- 최종 수정일: 2026-07-22
- DBMS: MySQL 8.4
- 문자 집합: `utf8mb4`
- 범위: RoomPick MVP 전체 도메인
- 기준 문서: `docs/ERD.md`, `docs/ERD.dbml`

이 문서는 RoomPick MVP에서 사용하는 테이블과 컬럼, 키, 제약조건, 인덱스를 구현 가능한 수준으로 정의한다. ERD의 관계가 변경되면 이 문서와 `docs/ERD.md`를 함께 수정한다.

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
| 결제 | `payments` | 예약별 결제 시도와 결과 이력 | minjae123123 |

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
| `status` | 객실 상태 | `VARCHAR(20)` | N | `ACTIVE` | - | `ACTIVE`, `INACTIVE` |
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

### 상태값

| 값 | 설명 |
| --- | --- |
| `ACTIVE` | 운영 중이며 예약 가능한 객실 |
| `INACTIVE` | 운영이 중지되어 신규 예약이 불가능한 객실 |

### 정책

- 객실은 관리자가 존재하는 숙소에 등록하며 생성 상태는 `ACTIVE`이다.
- MVP 시연에는 관리자가 등록한 실제 객실을 최소 1개 사용하되 DB에서 개수를 강제로 제한하지 않는다.
- 객실 가격은 예약 생성 시 `reservations.price_per_night`에 복사한다.
- 객실 가격이 변경되어도 기존 예약의 금액은 변경하지 않는다.
- 운영 중지된 객실도 기존 예약 이력을 위해 물리 삭제하지 않는다.

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
- `CONFIRMED` 예약과 만료 전 `PENDING_PAYMENT` 예약만 객실 예약 가능 여부를 막는다.
- `CANCELED`, `EXPIRED`, `COMPLETED` 예약은 신규 예약을 막지 않는다.
- 예약은 거래 이력이므로 물리 삭제하지 않고 상태를 변경한다.

---

## 7. PAYMENTS — 결제

### 기본 정보

| 항목 | 내용 |
| --- | --- |
| 물리 테이블명 | `payments` |
| 설명 | 예약별 결제 시도와 성공·실패·환불 결과를 저장한다. |
| Primary Key | `payment_id` |
| 담당자 | minjae123123 |

### 컬럼 명세

| 물리 컬럼명 | 논리 컬럼명 | 데이터 타입 | Null | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- | --- |
| `payment_id` | 결제 ID | `BIGINT` | N | `AUTO_INCREMENT` | PK | 결제 식별자 |
| `reservation_id` | 예약 ID | `BIGINT` | N | - | FK | 결제 대상 예약 ID |
| `amount` | 결제 금액 | `BIGINT` | N | - | - | 결제 요청 금액, 원 단위 |
| `method` | 결제 수단 | `VARCHAR(30)` | N | - | - | 카드 등 결제 수단 |
| `provider` | 결제 제공자 | `VARCHAR(30)` | N | - | - | MVP에서는 `FAKE`, 추후 실제 PG사 |
| `provider_payment_key` | 외부 결제 키 | `VARCHAR(255)` | Y | - | UK | 외부 결제의 고유 식별자 |
| `status` | 결제 상태 | `VARCHAR(30)` | N | - | - | 결제 처리 상태 |
| `failure_reason` | 실패 사유 | `VARCHAR(500)` | Y | - | - | 결제 실패 원인 |
| `paid_at` | 결제 완료 시각 | `DATETIME(6)` | Y | - | - | 결제 승인 완료 시각 |
| `refunded_at` | 환불 완료 시각 | `DATETIME(6)` | Y | - | - | 전액 환불 완료 시각 |
| `created_at` | 생성 시각 | `DATETIME(6)` | N | - | - | 결제 시도 생성 시각 |
| `updated_at` | 수정 시각 | `DATETIME(6)` | N | - | - | 결제 정보 최종 수정 시각 |

### 키·제약조건·인덱스

| 구분 | 이름 | 대상 | 설명 |
| --- | --- | --- | --- |
| PK | `pk_payments` | `payment_id` | 결제 기본 키 |
| FK | `fk_payments_reservation` | `reservation_id → reservations.reservation_id` | 결제 대상 예약 참조 |
| UK | `uk_payments_provider_payment_key` | `provider_payment_key` | 외부 결제 결과의 중복 반영 방지 |
| INDEX | `idx_payments_reservation_created_at` | `reservation_id, created_at` | 예약별 결제 시도 이력 조회 |
| CHECK | `chk_payments_amount` | `amount >= 0` | 음수 결제 금액 방지 |

### 상태값

| 값 | 설명 |
| --- | --- |
| `READY` | 결제 요청 전 준비 상태 |
| `PAID` | 결제 승인 완료 상태 |
| `FAILED` | 결제 실패 상태 |
| `REFUNDED` | 전액 환불 완료 상태 |

### 정책

- 결제 금액은 reservations.total_amount와 일치해야 한다.
- MVP에서는 예약과 결제를 1:1 관계로 구성하며, payments.reservation_id에 Unique Constraint를 적용해 예약 1건당 결제 1건만 생성한다.
- MVP의 결제 재처리는 동일한 Payment의 상태 전이 범위에서 처리하며, 별도의 결제 시도 행을 추가하지 않는다.
- 후속 버전에서 재결제, 결제 수단 변경, PG 재시도 등 여러 결제 시도 이력이 필요해지면 예약과 결제를 1:N 관계로 변경한다.
- 1:N 전환 시 uk_payments_reservation_id를 제거하고 reservation_id, created_at 인덱스를 통해 예약별 결제 시도 이력을 조회한다.
- 1:N 구조에서도 하나의 예약에는 최종적으로 하나의 PAID 결제만 허용하도록 애플리케이션 및 DB 정책을 추가한다.
- 결제 실패 기록과 환불 이력은 삭제하지 않는다.
- 부분 환불과 여러 환불 이력이 필요해지면 별도의 refunds 테이블을 추가한다.

---

## 8. 외래 키 관계

| 자식 테이블 | 외래 키 컬럼 | 부모 테이블 | 참조 컬럼 | 관계 |
| --- | --- | --- | --- | --- |
| `rooms` | `accommodation_id` | `accommodations` | `accommodation_id` | N:1 |
| `reservations` | `member_id` | `members` | `member_id` | N:1 |
| `reservations` | `room_id` | `rooms` | `room_id` | N:1 |
| `payments` | `reservation_id` | `reservations` | `reservation_id` | N:1 |

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
- [ ] 예약과 결제의 1:N 관계를 유지할지
- [ ] MVP에서 전액 환불을 처리할지
- [ ] 예약 겹침 동시성 제어 방식을 무엇으로 할지
- [ ] 모든 CHECK 제약조건을 Flyway 마이그레이션에 포함할지

위 항목이 변경되면 `docs/ERD.md`, `docs/ERD.dbml`, 이 문서를 함께 갱신한다.
