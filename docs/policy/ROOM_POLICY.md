# 객실 정책

- 문서 버전: `v0.3`
- 최종 수정일: 2026-08-21
- 담당자: IMSUN9(Service), minjae123123(관리자 등록 Controller·Facade)
- 근거 코드: `Room`, `RoomStatus`, `RoomService`, `AdminRoomFacade`, `AdminRoomController`

`ROOM` 한 행은 실제로 예약되는 물리적 객실 한 개를 의미한다(`docs/API_SPEC_OWNER.md` 참고).

이 문서는 객실의 DB 저장 상태와 사용자가 선택한 숙박 기간의 화면 표시 상태를 구분하는 기준 문서다. 다른 문서와 객실 상태 설명이 충돌하면 이 문서를 우선 기준으로 삼는다.

---

## 1. 접근 권한

| 동작 | 필요 권한 | 비고 |
| --- | --- | --- |
| 객실 등록 (`POST /api/v1/admin/**`) | `ADMIN` | Controller·Facade는 minjae123123 담당, 실제 등록 로직은 IMSUN9 소유 `RoomService` 사용 |
| 객실 공개 상태 변경 (`PATCH /api/v1/admin/**/status`) | `ADMIN` | `ACTIVE`, `INACTIVE`만 요청 가능 |
| 객실 논리 삭제 (`DELETE /api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}`) | `ADMIN` | 객실을 물리 삭제하지 않고 `INACTIVE`로 변경 |
| 객실 상세·목록 조회 (`GET /api/v1/rooms/**`) | 없음(비로그인 가능) | `SecurityConfig`의 `PUBLIC_GET_PATHS` 규칙 적용 |

관리자 기능에서 객실 Repository를 직접 호출하지 않는다.

---

## 2. 등록 규칙

- 객실은 존재하는 숙소에만 등록할 수 있다. 소속 숙소가 없으면 `ROOM_ACCOMMODATION_REQUIRED`(`400`)를 반환한다.
- 운영 중지(`INACTIVE`)된 숙소에는 객실을 등록할 수 없다. `ACCOMMODATION_INACTIVE`(`409`)를 반환한다.
  - 구현: `RoomService.createRoom()` → `validateAccommodationActive()`
- 같은 숙소 안에서 객실 번호(`roomNumber`)는 중복될 수 없다. 중복 시 `ROOM_NUMBER_DUPLICATED`(`409`)를 반환하며, DB 유니크 제약(`uk_rooms_accommodation_room_number`)으로도 이중 보호한다.
  - 구현: `RoomService.validateRoomNumberNotDuplicated()`
- 객실 번호와 이름은 필수이며 비어 있으면 각각 `ROOM_NUMBER_REQUIRED`, `ROOM_NAME_REQUIRED`(`400`)를 반환한다.
- 1박 가격(`pricePerNight`)은 0원 이상이어야 한다. 음수면 `INVALID_ROOM_PRICE`(`400`)를 반환한다.
- 기준 인원(`standardCapacity`)은 1명 이상이어야 하고, 최대 인원(`maxCapacity`)은 기준 인원보다 작을 수 없다. 위반 시 `INVALID_ROOM_CAPACITY`(`400`)를 반환한다.
  - 구현: `Room.create()` → `validatePrice()`, `validateCapacity()`
- 객실은 생성 시 항상 `INACTIVE` 상태로 시작한다.
- 관리자가 객실을 공개할 때 `ACTIVE`로 전환한다.
- 관리자가 `SOLD_OUT` 상태로 직접 변경하는 기능은 제공하지 않는다.
- 운영 중지된 숙소의 객실은 `ACTIVE`로 변경할 수 없다. `ACCOMMODATION_INACTIVE`(`409`)를 반환한다.
- 객실을 `INACTIVE`로 변경하는 것은 숙소 상태와 관계없이 허용한다.

---

## 3. 객실 상태 구분

RoomPick은 객실 자체의 공개 여부와 선택한 날짜의 예약 가능 여부를 구분한다.

| 사용자 표시 상태 | `rooms.status` 저장값 | 사용자 화면 노출 | 예약 가능 | 설명 |
| --- | --- | --- | --- | --- |
| `ACTIVE` | `ACTIVE` | 노출 | 가능 | 공개된 객실이며 선택한 숙박 기간에 활성 예약이 없음 |
| `SOLD_OUT` | `ACTIVE` | 노출 | 불가능 | 공개된 객실이지만 선택한 숙박 기간에 활성 예약이 존재함 |
| `INACTIVE` | `INACTIVE` | 미노출 | 불가능 | 아직 공개하지 않았거나 운영을 중지한 객실 |

### DB 저장 상태

`RoomStatus`와 `rooms.status`에는 다음 두 값만 사용한다.

```text
ACTIVE
INACTIVE
```

### 화면 표시 상태

사용자가 선택한 숙박 기간을 기준으로 다음 두 상태를 계산해 표시한다.

```text
ACTIVE
SOLD_OUT
```

`SOLD_OUT`은 `RoomStatus` Enum이나 `rooms.status` 컬럼에 저장하지 않는다.

---

## 4. 상태 전이 규칙

```text
객실 생성
→ INACTIVE
→ 관리자 공개
→ ACTIVE
→ 관리자 운영 중지
→ INACTIVE
```

- `INACTIVE` 객실은 사용자용 객실 목록과 상세 조회에 노출하지 않는다.
- `ACTIVE` 객실만 사용자용 조회 대상이 된다.
- 운영을 중지할 때 객실을 물리 삭제하지 않고 `INACTIVE`로 변경한다.
- 기존 예약 이력은 객실 상태 변경과 관계없이 보존한다.
- `PENDING_PAYMENT` 또는 `CONFIRMED` 예약이 존재해도 관리자는 객실을 `INACTIVE`로 변경할 수 있다.
- 객실을 `INACTIVE`로 변경해도 기존 예약은 취소하거나 변경하지 않는다. 이후 신규 공개 조회와 신규 예약만 차단한다.
- 객실 비공개 전환 시 예약 존재 여부를 조회하거나 검증하지 않는다.
- 객실 논리 삭제는 `INACTIVE` 상태 변경으로 처리하며 이미 비활성화된 객실에도 멱등하게 동작한다.
- 객실 논리 삭제 시 기존 예약·타임세일·특가·대기열·이미지와 S3 객체는 삭제하지 않는다.
- 숙소 논리 삭제 시에는 소속 객실도 모두 `INACTIVE`로 변경한다.

---

## 5. SOLD_OUT 계산 규칙

`SOLD_OUT`은 사용자가 선택한 체크인·체크아웃 날짜를 기준으로 서버가 계산한다.

```text
rooms.status = INACTIVE
→ 사용자 조회 대상에서 제외

rooms.status = ACTIVE
AND 선택한 기간과 겹치는 활성 예약 없음
→ ACTIVE

rooms.status = ACTIVE
AND 선택한 기간과 겹치는 활성 예약 있음
→ SOLD_OUT
```

같은 객실이라도 날짜에 따라 상태가 달라질 수 있다.

예를 들어 101호에 `2026-08-01` 체크인, `2026-08-03` 체크아웃 예약이 존재하면 다음과 같이 판단한다.

| 요청 기간 | 화면 표시 | 예약 가능 |
| --- | --- | --- |
| 2026-08-01 ~ 2026-08-03 | `SOLD_OUT` | 불가능 |
| 2026-08-02 ~ 2026-08-04 | `SOLD_OUT` | 불가능 |
| 2026-08-03 ~ 2026-08-05 | `ACTIVE` | 가능 |
| 2026-08-10 ~ 2026-08-12 | `ACTIVE` | 가능 |

체크아웃 날짜는 숙박일에 포함하지 않는다.

---

## 6. 날짜 중복 판단 규칙

기존 예약과 요청 기간이 다음 조건을 모두 만족하면 날짜가 겹친다.

```text
기존 체크인 < 요청 체크아웃
AND
기존 체크아웃 > 요청 체크인
```

예약 가능 여부를 막는 예약 상태는 다음과 같다.

- 만료되지 않은 `PENDING_PAYMENT`
- `CONFIRMED`

예약 가능 여부를 막지 않는 예약 상태는 다음과 같다.

- `CANCELED`
- `EXPIRED`
- `COMPLETED`

결제 실패, 예약 취소 또는 결제 대기 만료로 활성 예약이 사라지면 해당 기간은 다시 예약 가능한 상태로 계산한다.

---

## 7. 예약 가능 인원 규칙

- 예약 인원은 1명 이상이어야 한다. 미만이면 `INVALID_GUEST_COUNT`(`400`)를 반환한다.
- 예약 인원은 객실의 `maxCapacity`를 초과할 수 없다. 초과 시 `ROOM_CAPACITY_EXCEEDED`(`400`)를 반환한다.
  - 구현: `RoomService.validateGuestCount()` (예약 가능 여부 확인·예약용 객실 조회 시), `Reservation.validateGuestCount()` (예약 생성 시 최종 재검증)
- 이 인원 규칙은 객실 조회 시점과 예약 생성 시점 두 곳에서 동일하게 재검증된다. 자세한 예약 흐름은 [`RESERVATION_POLICY.md`](RESERVATION_POLICY.md) 참고.

---

## 8. 조회·예약 API 적용 규칙

### 사용자용 객실 목록·상세 조회

- 숙소와 객실이 모두 `ACTIVE`인 경우에만 공개한다.
- 객실 또는 숙소가 `INACTIVE`이면 비공개 자원의 존재를 노출하지 않도록 `ROOM_NOT_FOUND`(`404`)를 반환한다.
- 날짜 조건이 없는 조회에서는 객실의 공개 정보만 반환한다.
- 날짜 조건이 없으면 `SOLD_OUT` 여부를 확정하지 않는다.

### 예약 가능 여부 조회

- 숙소와 객실이 모두 `ACTIVE`여야 한다.
- 객실 또는 숙소가 `INACTIVE`이면 예약 불가 상태를 명확히 알리기 위해 `ROOM_INACTIVE`(`409`)를 반환한다.
- 체크인 날짜, 체크아웃 날짜, 예약 인원을 기준으로 판단한다.
- `ACTIVE` 객실이고 선택한 기간에 활성 예약이 없으면 `available=true`를 반환한다.
- `ACTIVE` 객실이고 선택한 기간에 활성 예약이 있으면 `available=false`를 반환한다.
- 프론트엔드는 날짜 중복으로 `available=false`인 경우 화면에 `SOLD_OUT`을 표시한다.

### 예약 생성

- 숙소와 객실이 모두 `ACTIVE`인 경우에만 예약 생성을 허용한다.
- 객실 또는 숙소가 `INACTIVE`이면 `ROOM_INACTIVE`(`409`)를 반환한다.
- 예약 생성 직전에도 날짜 중복을 다시 검증한다.
- 동일 기간에 활성 예약이 존재하면 예약 생성을 거절한다.
- 예약 생성 결과로 `rooms.status`를 `SOLD_OUT`으로 변경하지 않는다.

공개 상세 조회의 `404`와 예약 관련 API의 `409`는 의도된 차이다. 상세 조회는 비공개 자원의 존재를 숨기고, 예약 가능 여부와 예약 생성은 예약할 수 없는 상태임을 호출자에게 명확히 전달한다.

---

## 9. 구현 시 주의사항

- `RoomStatus` Enum에는 `ACTIVE`, `INACTIVE`만 둔다.
- `SOLD_OUT`을 DB 컬럼이나 Enum에 추가하지 않는다.
- 객실 하나의 예약 때문에 다른 날짜까지 전부 매진 처리하지 않는다.
- 예약 취소·결제 실패·결제 대기 만료 후 별도의 객실 상태 복구 쿼리를 실행하지 않는다.
- 예약 데이터 기준으로 해당 기간의 예약 가능 여부를 다시 계산한다.
- 예약 가능 여부 조회와 예약 생성은 동일한 날짜 중복 조건과 활성 예약 상태 기준을 사용한다.

---

## 10. 조회 규칙

- 존재하지 않는 객실을 조회하면 `ROOM_NOT_FOUND`(`404`)를 반환한다.
- 객실 검색, 필터, 정렬은 MVP에서 구현하지 않는다.

---

## 11. 문서 동기화 대상

이 정책이 변경되면 다음 문서를 함께 확인한다.

- `docs/TABLE_SPEC.md`
- `docs/API_SPEC_OWNER.md`
- `docs/API_SPEC_ADMIN.md`
- `docs/MVP_CONTEXT.md`
- 객실 및 예약 관련 Postman 예시
