# 예약 정책

- 문서 버전: `v0.1`
- 최종 수정일: 2026-07-24
- 담당자: IMSUN9
- 근거 코드: `Reservation`, `ReservationStatus`, `ReservationService`, `ReservationPrice`

---

## 1. 접근 권한

| 동작 | 필요 권한 | 비고 |
| --- | --- | --- |
| 예약 가능 여부 확인 | 없음(비로그인 가능) | 객실 조회와 동일하게 공개 |
| 예약 생성 | 인증된 회원(`USER`, `ADMIN` 모두 가능) | `SecurityConfig` 기본 규칙(인증 필요) |
| 내 예약 목록·상세 조회 | 인증된 회원, 본인 예약만 | 아래 4절 참고 |
| 예약 취소 | 미구현 | 아래 6절 참고 |

- 인증된 회원 ID는 항상 서버 인증 컨텍스트(`AuthMember`)에서 가져오며, Request Body의 회원 ID
  값은 신뢰하지 않는다.
  - 구현: `ReservationService.validateMemberId()`가 `null` 회원 ID를 `UNAUTHORIZED`(`401`)로
    거부한다. 이는 회원 ID가 누락된 경우에 대한 방어이며, 실제 회원 ID 값 자체는 컨트롤러가
    인증 컨텍스트에서 채운다.

## 2. 숙박 기간 규칙

- 체크인 날짜는 체크아웃 날짜보다 이전이어야 한다.
- 체크인 날짜는 오늘(`Asia/Seoul` 기준) 이전일 수 없다.
- 체크아웃 날짜는 숙박일에 포함하지 않는다.
- 위 조건을 어기면 `INVALID_STAY_PERIOD`(`400`)를 반환한다.
  - 구현: `ReservationService.validateStayPeriod()`
- 이 검증은 예약 가능 여부 확인(`isRoomAvailable`)과 예약 생성(`createReservation`) 양쪽에서
  동일하게 다시 수행된다.

## 3. 예약 가능 여부·중복 예약 규칙

- 같은 객실에 대해 `CONFIRMED` 상태이거나 만료되지 않은 `PENDING_PAYMENT` 상태인 예약과
  하루라도 겹치면 예약할 수 없다.
  - 구현: `ReservationRepository.existsActiveOverlappingReservation()`
- 예약 생성 시 이 중복 확인을 다시 수행하며, 겹치는 예약이 있으면 `ROOM_NOT_AVAILABLE`(`409`)를
  반환한다.
  - 구현: `ReservationService.validateRoomAvailable()`
- 정확히 동시에 들어오는 요청에 대한 락 전략과 동시성 테스트는 MVP 이후 버전(`v1.1`)에서
  다룬다(`docs/MVP_CONTEXT.md` 11절 로드맵 참고). 현재 구현은 낙관적으로 재확인만 한다.

## 4. 예약 생성 규칙

- 예약 인원은 1명 이상이어야 하고 객실의 `maxCapacity`를 초과할 수 없다.
  `INVALID_GUEST_COUNT`(`400`) 또는 `ROOM_CAPACITY_EXCEEDED`(`400`)를 반환한다. 자세한 내용은
  [`ROOM_POLICY.md`](ROOM_POLICY.md) 4절 참고.
  - 구현: `Reservation.validateGuestCount()`
- 예약 생성 시 상태는 항상 `PENDING_PAYMENT`로 시작한다.
- 예약 가격(`pricePerNight`, `nightCount`, `totalAmount`)은 예약 시점의 객실 가격으로 스냅샷
  저장한다. 이후 객실 가격이 바뀌어도 이미 생성된 예약 금액은 바뀌지 않는다.
  - 구현: `Reservation.create()` → `ReservationPrice.calculate()`
- 예약 생성 시 결제 대기 만료 시각(`expiresAt`)을 현재 시각 + 10분으로 설정한다
  (`PAYMENT_WAIT_MINUTES`). 이 10분 값은 초안이며 팀 회의에서 최종 확정한다
  (`docs/API_SPEC_OWNER.md` 참고).

## 5. 내 예약 조회 규칙

- 인증된 회원이 생성한 예약만 조회할 수 있다.
- 목록 조회는 페이지 단위로 제공하며, 생성일 내림차순(동률이면 예약 ID 내림차순)으로 정렬한다.
  - 구현: `ReservationService.findMyReservations()`
- 페이지 번호는 0 이상, 페이지 크기는 1 이상 100 이하만 허용한다. 벗어나면
  `INVALID_INPUT_VALUE`(`400`)를 반환한다.
  - 구현: `ReservationService.validatePageRequest()`
- 예약 상세 조회 시 예약이 존재하지 않으면 `RESERVATION_NOT_FOUND`(`404`), 본인이 생성하지 않은
  예약이면 `RESERVATION_ACCESS_DENIED`(`403`)를 반환한다.
  - 구현: `ReservationService.findMyReservation()` → `validateReservationOwner()`

## 6. 취소 규칙 (미구현)

다음은 `docs/MVP_CONTEXT.md` 8절에 정의된 취소 정책이며, 아직 `ReservationService`에
구현되지 않았다. `RESERVATION_NOT_CANCELABLE` 에러 코드는 정의만 되어 있고 사용되지 않는다.

- MVP에서는 체크인 이전의 확정(`CONFIRMED`) 예약만 취소할 수 있도록 단순화한다.
- 예약을 취소하면 상태는 `CANCELED`가 되고 점유했던 객실이 다시 예약 가능해진다.
- MVP의 환불 정책은 전액 취소를 기본으로 하며, 취소 수수료와 부분 환불은 이후 버전에서 다룬다.
