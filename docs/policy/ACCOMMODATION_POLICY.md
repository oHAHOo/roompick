# 숙소 정책

- 문서 버전: `v0.3`
- 최종 수정일: 2026-08-21
- 담당자: IMSUN9(Service), minjae123123(관리자 등록 Controller·Facade)
- 근거 코드: `Accommodation`, `AccommodationStatus`, `AccommodationService`,
  `AdminAccommodationFacade`, `AdminAccommodationController`

---

## 1. 접근 권한

| 동작 | 필요 권한 | 비고 |
| --- | --- | --- |
| 숙소 등록 (`POST /api/v1/admin/**`) | `ADMIN` | `SecurityConfig`의 `/api/v1/admin/**` 규칙 적용. Controller·Facade는 minjae123123 담당, 실제 등록 로직은 IMSUN9 소유 `AccommodationService` 사용 |
| 숙소 논리 삭제 (`DELETE /api/v1/admin/accommodations/{accommodationId}`) | `ADMIN` | 숙소와 소속 객실을 모두 `INACTIVE`로 변경 |
| 숙소 상세 조회 (`GET /api/v1/accommodations/**`) | 없음(비로그인 가능) | `SecurityConfig`의 `PUBLIC_GET_PATHS` 규칙 적용 |
| 장소 후보 검색 (`GET /api/v1/places/search`) | 없음(비로그인 가능) | Kakao Local API를 호출하며 숙소 DB를 조회하지 않음 |

관리자 기능에서 숙소 Repository를 직접 호출하지 않는다. 관리자 등록 유스케이스는 항상
`AccommodationService`를 통해서만 숙소를 생성한다(`AGENTS.md` 도메인 담당자 절 참고).

## 2. 등록 규칙

- 이름, 주소는 필수이며 비어 있으면(`null` 또는 공백) 각각 `ACCOMMODATION_NAME_REQUIRED`,
  `ACCOMMODATION_ADDRESS_REQUIRED`(`400`)를 반환한다.
- 체크인·체크아웃 시간은 필수이며 없으면 `ACCOMMODATION_TIME_REQUIRED`(`400`)를 반환한다.
- 관리자 숙소 등록 시 위도와 경도를 필수로 전달하며, 생성 시점부터 한 쌍으로 저장한다.
- 위도는 -90~90, 경도는 -180~180 범위여야 하며 서버가 등록 과정에서 주소를 좌표로 다시 변환하지 않는다.
- 숙소는 생성 시 항상 `ACTIVE` 상태로 시작한다. 생성 시점에 다른 상태를 선택할 수 없다.
  - 구현: `Accommodation.create()`

## 3. 상태 규칙

- 숙소 상태는 `AccommodationStatus`(`ACTIVE`, `INACTIVE`) 두 가지다.
- `INACTIVE` 숙소에는 새로운 객실을 등록할 수 없다. 등록 시도 시 `ACCOMMODATION_INACTIVE`(`409`)를
  반환한다.
  - 구현: `RoomService.validateAccommodationActive()` (자세한 내용은
    [`ROOM_POLICY.md`](ROOM_POLICY.md) 참고)
- 숙소 삭제는 물리 삭제가 아니라 `INACTIVE` 상태로 전환하는 논리 삭제로 처리한다.
- 숙소 논리 삭제 시 소속 객실도 모두 `INACTIVE`로 변경한다.
- 숙소와 객실 상태 변경은 하나의 Facade 트랜잭션으로 처리하며, 일부 처리에 실패하면 전체를 롤백한다.
- 이미 `INACTIVE`인 숙소에 다시 삭제를 요청해도 성공하는 멱등 API로 동작한다.
- 기존 예약·결제·타임세일·특가·대기열·이미지와 S3 객체는 삭제하지 않는다.
- 숙소 논리 삭제가 커밋되면 인기 숙소 캐시를 무효화한다.

## 4. 조회 규칙

- 존재하지 않는 숙소를 조회하면 `ACCOMMODATION_NOT_FOUND`(`404`)를 반환한다.
  - 구현: `AccommodationService.findById()`
- `ACTIVE` 숙소 목록과 좌표 기반 주변 숙소 검색을 제공한다.
- 주변 숙소 검색은 선택된 `latitude`, `longitude`를 중심으로 반경과 반환 개수를 제한하며 거리순으로 반환한다.
- 장소명은 `/api/v1/places/search`에서 후보 좌표로 변환하고, 사용자가 선택한 좌표를 `/api/v1/accommodations/search`에 전달한다.
- production 숙소 위치 검색은 MySQL Bounding Box를 사용한다.
- Elasticsearch 검색 구현은 local 성능 비교와 향후 재도입을 위해 보존하며, local 설정에서 선택할 수 있다.
- 장소 검색 중 숙소 DB를 조회하거나 DB 트랜잭션을 시작하지 않는다.
- 자동완성, 초성 검색, 오타 보정, 동의어, 추천 검색어와 복합 필터·사용자 지정 정렬은 구현하지 않는다.
