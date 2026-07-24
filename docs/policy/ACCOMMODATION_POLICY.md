# 숙소 정책

- 문서 버전: `v0.1`
- 최종 수정일: 2026-07-24
- 담당자: IMSUN9(Service), minjae123123(관리자 등록 Controller·Facade)
- 근거 코드: `Accommodation`, `AccommodationStatus`, `AccommodationService`,
  `AdminAccommodationFacade`, `AdminAccommodationController`

---

## 1. 접근 권한

| 동작 | 필요 권한 | 비고 |
| --- | --- | --- |
| 숙소 등록 (`POST /api/v1/admin/**`) | `ADMIN` | `SecurityConfig`의 `/api/v1/admin/**` 규칙 적용. Controller·Facade는 minjae123123 담당, 실제 등록 로직은 IMSUN9 소유 `AccommodationService` 사용 |
| 숙소 상세 조회 (`GET /api/v1/accommodations/**`) | 없음(비로그인 가능) | `SecurityConfig`의 `PUBLIC_GET_PATHS` 규칙 적용 |

관리자 기능에서 숙소 Repository를 직접 호출하지 않는다. 관리자 등록 유스케이스는 항상
`AccommodationService`를 통해서만 숙소를 생성한다(`AGENTS.md` 도메인 담당자 절 참고).

## 2. 등록 규칙

- 이름, 주소는 필수이며 비어 있으면(`null` 또는 공백) 각각 `ACCOMMODATION_NAME_REQUIRED`,
  `ACCOMMODATION_ADDRESS_REQUIRED`(`400`)를 반환한다.
- 체크인·체크아웃 시간은 필수이며 없으면 `ACCOMMODATION_TIME_REQUIRED`(`400`)를 반환한다.
- 숙소는 생성 시 항상 `ACTIVE` 상태로 시작한다. 생성 시점에 다른 상태를 선택할 수 없다.
  - 구현: `Accommodation.create()`

## 3. 상태 규칙

- 숙소 상태는 `AccommodationStatus`(`ACTIVE`, `INACTIVE`) 두 가지다.
- `INACTIVE` 숙소에는 새로운 객실을 등록할 수 없다. 등록 시도 시 `ACCOMMODATION_INACTIVE`(`409`)를
  반환한다.
  - 구현: `RoomService.validateAccommodationActive()` (자세한 내용은
    [`ROOM_POLICY.md`](ROOM_POLICY.md) 참고)
- 숙소 수정·삭제·상태 변경 API는 MVP 범위에 포함하지 않는다(`docs/MVP_CONTEXT.md` 6절 참고).

## 4. 조회 규칙

- 존재하지 않는 숙소를 조회하면 `ACCOMMODATION_NOT_FOUND`(`404`)를 반환한다.
  - 구현: `AccommodationService.findById()`
- 숙소 목록 조회, 검색, 필터, 정렬은 MVP에서 구현하지 않는다.
