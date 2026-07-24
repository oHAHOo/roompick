# 객실 정책

- 문서 버전: `v0.1`
- 최종 수정일: 2026-07-24
- 담당자: IMSUN9(Service), minjae123123(관리자 등록 Controller·Facade)
- 근거 코드: `Room`, `RoomStatus`, `RoomService`, `AdminRoomFacade`, `AdminRoomController`

`ROOM` 한 행은 실제로 예약되는 물리적 객실 한 개를 의미한다(`docs/API_SPEC_OWNER.md` 참고).

---

## 1. 접근 권한

| 동작 | 필요 권한 | 비고 |
| --- | --- | --- |
| 객실 등록 (`POST /api/v1/admin/**`) | `ADMIN` | Controller·Facade는 minjae123123 담당, 실제 등록 로직은 IMSUN9 소유 `RoomService` 사용 |
| 객실 상세·목록 조회 (`GET /api/v1/rooms/**`) | 없음(비로그인 가능) | `SecurityConfig`의 `PUBLIC_GET_PATHS` 규칙 적용 |

관리자 기능에서 객실 Repository를 직접 호출하지 않는다.

## 2. 등록 규칙

- 객실은 존재하는 숙소에만 등록할 수 있다. 소속 숙소가 없으면
  `ROOM_ACCOMMODATION_REQUIRED`(`400`)를 반환한다.
- 운영 중지(`INACTIVE`)된 숙소에는 객실을 등록할 수 없다. `ACCOMMODATION_INACTIVE`(`409`)를
  반환한다.
  - 구현: `RoomService.createRoom()` → `validateAccommodationActive()`
- 같은 숙소 안에서 객실 번호(`roomNumber`)는 중복될 수 없다. 중복 시
  `ROOM_NUMBER_DUPLICATED`(`409`)를 반환하며, DB 유니크 제약(`uk_rooms_accommodation_room_number`)으로도
  이중 보호된다.
  - 구현: `RoomService.validateRoomNumberNotDuplicated()`
- 객실 번호와 이름은 필수이며 비어 있으면 각각 `ROOM_NUMBER_REQUIRED`, `ROOM_NAME_REQUIRED`(`400`)를
  반환한다.
- 1박 가격(`pricePerNight`)은 0원 이상이어야 한다. 음수면 `INVALID_ROOM_PRICE`(`400`)를 반환한다.
- 기준 인원(`standardCapacity`)은 1명 이상이어야 하고, 최대 인원(`maxCapacity`)은 기준 인원보다
  작을 수 없다. 위반 시 `INVALID_ROOM_CAPACITY`(`400`)를 반환한다.
  - 구현: `Room.create()` → `validatePrice()`, `validateCapacity()`
- 객실은 생성 시 항상 `ACTIVE` 상태로 시작한다.

## 3. 상태 규칙

- 객실 상태는 `RoomStatus`(`ACTIVE`, `INACTIVE`) 두 가지다.
- `ACTIVE` 상태가 아닌 객실은 예약할 수 없다. 예약 가능 여부 확인·예약 생성 시
  `ROOM_INACTIVE`(`409`)를 반환한다.
  - 구현: `RoomService.validateRoomStatus()`
- 객실 유형별 수량 재고, 여러 객실 일괄 등록, 객실 수정·삭제는 MVP 범위에 포함하지 않는다.

## 4. 예약 가능 인원 규칙

- 예약 인원은 1명 이상이어야 한다. 미만이면 `INVALID_GUEST_COUNT`(`400`)를 반환한다.
- 예약 인원은 객실의 `maxCapacity`를 초과할 수 없다. 초과 시 `ROOM_CAPACITY_EXCEEDED`(`400`)를
  반환한다.
  - 구현: `RoomService.validateGuestCount()` (예약 가능 여부 확인·예약용 객실 조회 시),
    `Reservation.validateGuestCount()` (예약 생성 시 최종 재검증)
- 이 인원 규칙은 객실 조회 시점과 예약 생성 시점 두 곳에서 동일하게 재검증된다. 자세한 예약
  흐름은 [`RESERVATION_POLICY.md`](RESERVATION_POLICY.md) 참고.

## 5. 조회 규칙

- 존재하지 않는 객실을 조회하면 `ROOM_NOT_FOUND`(`404`)를 반환한다.
- 객실 검색, 필터, 정렬은 MVP에서 구현하지 않는다.
