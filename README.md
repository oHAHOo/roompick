# RoomPick Backend

RoomPick은 숙소와 실제 객실을 예약하고 결제하는 3인 팀 숙박 예약 서비스입니다. 관리자가 숙소와 실제 객실을 등록·공개하면, 회원이 공개된 객실의 상세 정보를 확인해 체크인·체크아웃 날짜를 선택하고 예약·결제·조회·취소할 수 있습니다.

현재 목표는 검색을 제외한 최소 MVP입니다. 관리자 숙소·객실 등록 → 객실 확인 → 예약 → 결제 → 예약 확인·취소로 이어지는 핵심 흐름을 먼저 검증한 뒤, 타임세일·선착순 특가·인기 숙소 캐싱 같은 후속 기능을 버전별로 확장했습니다.

## 팀원 및 담당 도메인

| 담당자 | 담당 도메인 |
| --- | --- |
| IMSUN9 | 숙소, 객실, 예약, 전체 통합 |
| minjae123123 | 결제, 관리자 기능 |
| oHAHOo | 회원, 인증·인가, 보안 |

관리자 숙소·객실 등록은 minjae123123이 Controller·Facade·DTO를 담당하고, IMSUN9이 소유한 Accommodation·Room Service를 통해 처리합니다. 관리자 기능에서 숙소·객실 Repository를 직접 호출하지 않습니다. `ADMIN` 권한과 접근 제어는 oHAHOo가 담당합니다.

## 기술 스택

| 구분 | 스택 |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.5.14 |
| Build | Gradle 8.14.3 |
| Web/Data | Spring Web, Spring Data JPA, Spring Security, Validation |
| Database | MySQL 8.4 |
| Cache | Redis 7 |
| Search | Elasticsearch 8.18.1 |
| Messaging | Kafka 3.8.0 |
| Mail | Spring Mail |
| Test | JUnit 5, H2, Testcontainers |
| CI/CD | GitHub Actions |

## 주요 기능

`docs/MVP_CONTEXT.md`와 `docs/policy/`에 기록된 완료 조건을 기준으로 구현된 기능입니다.

- 회원가입·로그인, JWT 인증·인가(Access/Refresh Token, Redis 블랙리스트 기반 로그아웃)
- 일반 회원가입으로는 `ADMIN` 권한을 선택할 수 없으며, 관리자 전용 API는 `403 Forbidden`으로 접근을 제한
- 관리자 숙소·객실 등록(이미지 업로드 포함), 객실 공개(`ACTIVE`)·비공개(`INACTIVE`) 상태 전환
- 관리자 숙소·객실 논리 삭제(`INACTIVE` 전환, 거래 이력 보존)
- 공개된 숙소·객실 목록·상세 조회, 체크인·체크아웃 날짜 기준 예약 가능 여부(`ACTIVE`/`SOLD_OUT`) 확인
- 장소명 기반 후보 장소 검색(Kakao Local API)과 선택 좌표 기준 주변 숙소 검색(MySQL Bounding Box 또는 Elasticsearch)
- 예약 생성·조회·취소, 객실 단위 비관적 락 기반 동시성 제어, `Idempotency-Key` 기반 예약 멱등성
- Mock 결제 준비·성공·실패 처리와 결제 실패 시 예약 점유 자동 해제
- 타임세일(숙소 전체/객실 전용 할인)과 예약 가격 스냅샷 연동
- 선착순 특가(`special_offers`)와 Kafka 기반 대기열(`waitlists`)을 통한 처리 순서 보장
- 인기 숙소 Redis 캐싱(Cache-Aside, Single Flight, DB fallback)
- 숙소·객실 이미지 S3 업로드와 CloudFront CDN 배포
- 타임세일·특가·대기열 만료 스케줄러의 전용 스레드 실행 격리
- AWS EC2 + RDS 배포와 GitHub Actions CI/CD 자동 배포

## 기술적 의사결정

<details>
<summary><strong>설계 원칙 — 계층 구조(Controller → Facade → Service → Repository)</strong></summary>

**선택한 방식**

모든 요청은 `Controller → Facade → Service → Repository` 순서로 호출합니다.

- Controller: 요청 검증, DTO 변환, HTTP 응답 생성
- Facade: 여러 도메인 Service를 조합하는 유스케이스, 트랜잭션 시작
- Service: 단일 도메인의 규칙과 트랜잭션 처리
- Repository: 영속성 접근

단일 도메인 조회처럼 조합할 로직이 없는 경우에도 팀의 일관성을 위해 Facade를 거칩니다.

**선택 이유**

- 관리자 숙소·객실 등록처럼 minjae123123(Controller·Facade)과 IMSUN9(Service)이 도메인을 나눠 담당하는 협업 구조에서, Facade가 담당자 간 경계를 명확히 합니다.
- 결제 성공·실패 처리처럼 `PaymentService`와 `ReservationService`가 서로 직접 호출하지 않고 `PaymentFacade`가 두 Service를 조율해, 트랜잭션 경계와 책임을 한 곳에 모읍니다.
- 각 도메인은 기능이 추가될 때 `controller`, `facade`, `service`, `repository`, `entity`, `dto` 하위 패키지를 만들고, 사용되지 않는 빈 계층은 미리 만들지 않습니다.

</details>

<details>
<summary><strong>예약 생성 동시성 제어 — 객실 단위 비관적 락</strong></summary>

**문제 상황**

기존 예약 로직은 "겹치는 예약 조회 → 예약 저장" 순서로 동작해, 서로 다른 회원이 동시에 같은 객실을 예약하면 두 요청이 모두 중복 검사를 통과할 수 있었습니다. 실제 MySQL 환경에서 `CountDownLatch`로 동시 요청을 재현한 결과 예약이 1건이 아닌 2건 저장되는 것을 확인했습니다.

**비교한 대안**

| 대안 | 적용하지 않은 이유 |
| --- | --- |
| 애플리케이션 `synchronized` | 다중 인스턴스 환경에서 인스턴스 간 동시성을 제어할 수 없음 |
| 날짜 조합 Unique Constraint | 임의로 겹치는 숙박 기간 전체를 Unique Constraint만으로 표현하기 어려움 |
| Redis 분산 락 | 별도 인프라와 락 소유권·만료·장애 처리 복잡도가 추가됨 |
| 객실 단위 비관적 락 | MySQL 트랜잭션 안에서 현재 예약 생성 흐름을 일관되게 보호할 수 있어 채택 |

**선택 이유 및 구현 정책**

- 예약 생성 시 대상 객실 행을 `PESSIMISTIC_WRITE`로 조회(`SELECT ... FOR UPDATE`)하고, `ReservationFacade.createReservation()`이 전체 트랜잭션을 시작합니다.
- 숙소는 `fetch join` 하지 않고 객실 행만 잠가, 같은 숙소의 다른 객실 예약이 서로 대기하지 않도록 합니다.
- `RoomService.findReservableRoomForUpdate()`는 `Propagation.MANDATORY`로 설정해 트랜잭션 없이 호출되면 즉시 실패하게 만들어, 락 획득 후 트랜잭션이 조기 종료되는 구현 실수를 방지합니다.
- `innodb_lock_wait_timeout=3`을 HikariCP Connection 초기화 시점에 설정해 락 대기를 최대 3초로 제한하고, 초과 시 `RESERVATION_LOCK_TIMEOUT`(409)으로 응답합니다.
- Testcontainers MySQL 8.4 기반 통합 테스트로 적용 전(2건 성공, 실패)과 적용 후(1건 성공 + `ROOM_NOT_AVAILABLE`, 통과)를 비교 검증했습니다.

</details>

<details>
<summary><strong>예약 생성 API 멱등성 — Idempotency-Key</strong></summary>

**선택한 방식**

예약 생성 API는 `Idempotency-Key` 헤더를 필수로 받습니다. 회원 ID + 멱등성 키 단위로 요청을 식별하고, `roomId|checkInDate|checkOutDate|guestCount`를 SHA-256으로 해싱해 동일 요청 여부를 판단합니다.

| 조건 | 처리 결과 |
| --- | --- |
| 최초 키·요청 | 예약 생성 후 `201 Created` |
| 동일 회원·동일 키·동일 요청 | 기존 예약을 `201 Created`로 반환 |
| 동일 회원·동일 키·다른 요청 | `409 RESERVATION_IDEMPOTENCY_CONFLICT` |
| 다른 회원·동일 키 | 회원별로 독립 처리 |

**선택 이유 및 구현 정책**

- 객실 단위 비관적 락은 서로 다른 회원의 경합만 다루므로, 동일 회원의 네트워크 재시도나 중복 클릭으로 인한 반복 요청은 별도로 처리해야 합니다.
- 멱등성 행에 먼저 비관적 락을 획득한 뒤 객실 행 락을 획득하는 순서(`예약 멱등성 행 → 객실 행`)를 고정해 락 순서에 의한 교착 상태를 방지합니다.
- 멱등성 확보, 객실 락, 예약 저장, 처리 완료 기록을 하나의 트랜잭션으로 묶어, 예약 생성 실패 시 멱등성 데이터도 함께 롤백되어 같은 키로 재시도할 수 있습니다.

</details>

<details>
<summary><strong>예약 대기열 시스템 — Kafka 기반 처리 순서 보장</strong></summary>

**문제 상황**

객실 단위 비관적 락은 동일 객실 중복 예약은 막지만 "누가 먼저 요청했는가"는 보장하지 않습니다. 여러 요청이 동시에 락을 두고 경쟁하면 락 획득 순서는 서버 스레드 스케줄링에 따라 사실상 무작위입니다.

**검증**

30명이 동시에 같은 객실을 요청하는 상황을 재현해 두 방식을 비교했습니다.

| 방식 | 요청 순서와 처리 순서가 다른 비율 |
| --- | --- |
| A. 락 경쟁 방식(기존 구조) | 30건 중 29건 (96.7%) |
| B. Kafka 방식(파티션 + 단일 컨슈머) | 30건 중 0건 (0%) |

**선택 이유**

- Kafka 파티션은 적재 순서를 그대로 오프셋으로 보존하므로 별도의 순번 관리 구조 없이 "적재 순서 = 처리 순서"가 보장됩니다. Redis `SETNX` 등으로도 구현할 수 있지만 순서 자체는 별도 자료구조로 직접 관리해야 합니다.
- 이 시스템은 정합성을 대체하지 않고, 기존 DB 락을 최종 안전망으로 유지한 채 그 위에 요청 순서 공정성·대기 상태 가시성·승계 처리를 추가합니다.

**구현 정책**

- 특가 판매 단위는 객실과 1:1(`special_offers`, `room_id` unique)로 묶어 별도 수량 필드를 두지 않습니다.
- 파티션 키는 `special_offers.id`를 사용해 같은 특가 요청이 항상 같은 파티션에 순서대로 쌓이게 합니다.
- 대기열은 `waitlists` 테이블(`WAIT` → `HOLD` → `CONFIRMED`/`EXPIRED`)로 관리하며, 스케줄러가 만료된 `HOLD`를 `EXPIRED`로 바꾸고 같은 `offer_id`의 `WAIT` 중 `id` 오름차순으로 가장 앞선 행을 승격합니다. `id` 오름차순이 실제 Kafka 적재 순서와 항상 일치하기 때문에 `requested_at`이 아닌 `id`를 승계 기준으로 사용합니다.

</details>

<details>
<summary><strong>타임세일 — 숙소 전체·객실 전용 할인과 가격 스냅샷</strong></summary>

**선택한 방식**

타임세일은 숙소 전체(`room_id = NULL`) 또는 특정 객실(`room_id` 지정) 대상으로 등록하며, 객실 전용 타임세일이 숙소 전체 타임세일보다 우선 적용됩니다. 할인율은 1~99%, 기간은 `startAt <= now < endAt`의 반개방 구간으로 처리합니다.

**선택 이유 및 구현 정책**

- 예약 생성 트랜잭션 안에서 객실 비관적 락을 획득한 뒤 적용 가능한 타임세일을 조회하고, 계산된 `price_per_night`·`total_amount`를 예약에 스냅샷으로 저장합니다. 이후 타임세일이 종료되거나 객실 정상 가격이 바뀌어도 이미 생성된 예약 금액은 유지됩니다.
- 같은 대상(숙소 전체/특정 객실)의 기간 중복 등록은 대상 행(Accommodation 또는 Room)에 `PESSIMISTIC_WRITE` 락을 건 뒤 검사해, 동시 등록 시 두 요청이 모두 중복 없음으로 판단하는 문제를 막습니다.
- 객실 목록처럼 여러 객실 가격을 한 번에 계산할 때는 객실마다 단건 쿼리를 반복하지 않고, 객실 전용/숙소 전체 타임세일을 각각 1회 배치 조회한 뒤 메모리에서 우선순위대로 매핑해 쿼리 수를 고정합니다.

</details>

<details>
<summary><strong>인기 숙소 캐싱 — Redis Cache-Aside</strong></summary>

**선택한 방식**

인기 숙소 조회 결과에 Redis Cache-Aside 전략을 적용합니다(캐시 이름 `popularAccommodations`, 기본 TTL 60초). 캐시 Key는 기간(`DAILY`/`WEEKLY`), 기준 날짜, `limit`을 포함합니다.

**선택 이유 및 구현 정책**

- 캐시 MISS 시 Redis Sorted Set 인기 랭킹을 구간(`limit × 5`) 단위로 조회하고, 그 ID로만 DB에서 `ACTIVE` 숙소 공개 정보를 조회해 응답을 조합합니다. JPA Entity가 아닌 조회 전용 응답 DTO만 캐시에 저장해 지연 로딩 프록시 직렬화 문제와 캐시-Entity 결합도를 피합니다.
- 숙소 정보 변경(공개 정보 수정, `INACTIVE` 전환, 객실 논리 삭제)이 커밋된 이후에만 캐시 전체를 삭제합니다(`RedisCacheManager.transactionAware()`). 커밋 전에 캐시를 지우면 다른 요청이 아직 반영되지 않은 데이터로 캐시를 재생성할 위험이 있기 때문입니다.
- Redis 장애 시 캐시 조회·저장·삭제 예외는 로그만 남기고 흡수하며, 인기 랭킹 조회까지 실패하면 최신 `ACTIVE` 숙소로 DB fallback 합니다. fallback 결과는 실제 순위가 아니므로 캐시에 저장하지 않습니다.
- 캐시가 빈 상태에서 동일 Key 요청이 몰리는 상황(cold cache)에는 애플리케이션 내부 Single Flight로 최종 조회를 한 번만 수행하고 결과를 공유합니다.
- 로컬 측정 기준 동일 요청의 DB 조회 횟수가 1회 → 0회로, 평균 응답 시간이 약 14.093ms → 약 5.308ms로 감소했습니다.

</details>

<details>
<summary><strong>숙소·객실 이미지 CDN 배포</strong></summary>

**선택한 방식**

숙소·객실 이미지를 S3 원본에서 직접 서빙하지 않고 CloudFront CDN(`Managed-CachingOptimized`, Default TTL 1일)을 경유해 제공합니다.

**선택 이유 및 구현 정책**

- `cdn-domain` 설정이 있으면 CDN URL을, 없으면(local 등) S3 원본 URL을 반환해 환경별로 CDN 적용 여부를 유연하게 전환합니다.
- 이미지 삭제 시에는 S3 객체 삭제가 성공한 경우에만 CloudFront invalidation을 요청합니다. 삭제가 실패했는데 캐시만 무효화하면 삭제되지 않은 이미지가 CloudFront에 재캐싱될 수 있기 때문입니다.
- CDN 적용 이전 S3 원본 URL로 저장된 기존 데이터는 Flyway 마이그레이션으로 CDN URL로 일괄 전환하고, `extractKey()`는 두 URL 형식을 모두 처리해 하위 호환을 유지합니다.

</details>

<details>
<summary><strong>스케줄러 실행 격리 — 도메인별 전용 ThreadPoolTaskScheduler</strong></summary>

**문제 상황**

타임세일·선착순 특가·대기열 만료 스케줄러가 Spring 기본 공유 스레드를 사용하면, 한 작업이 오래 실행될 때 다른 스케줄러의 실행 시각이 도래해도 대기해야 하는 문제가 있었습니다.

**선택한 방식**

세 스케줄러 각각에 풀 크기 1인 전용 `ThreadPoolTaskScheduler`를 할당합니다(`timeSaleTaskScheduler`, `specialOfferTaskScheduler`, `waitlistTaskScheduler`).

**선택 이유 및 구현 정책**

- 서로 다른 스케줄러는 각자의 전용 스레드에서 동시에 실행되어 서로를 지연시키지 않습니다. 같은 스케줄러는 풀 크기가 1이므로 중첩 실행되지 않습니다.
- `setWaitForTasksToCompleteOnShutdown(true)` + `AwaitTerminationSeconds(10)`으로 애플리케이션 종료 시 실행 중인 작업이 정리될 기회를 줍니다.
- 실행기 분리는 스레드만 분리할 뿐 HikariCP 커넥션 풀, MySQL 락, Redis·Kafka 연결 등은 계속 공유하므로, 세 작업이 동시에 많은 데이터를 처리하면 DB 커넥션 경합이 늘어날 수 있다는 점을 운영 시 함께 모니터링합니다.
- 다중 인스턴스 환경의 중복 실행 방지(ShedLock, 분산 락 등)는 현재 범위에서 제외했습니다.

</details>

## 프로젝트 구조

```text
com.roompick
├── domain
│   ├── member          # 회원가입·로그인, JWT 발급/검증, AuthController·AuthFacade
│   ├── accommodation    # 숙소 목록·상세·인기 숙소·주변 검색 (controller/facade/service/repository/entity/dto)
│   ├── room             # 객실 목록·상세·예약 가능 여부 확인
│   ├── place             # Kakao Local API 기반 장소 후보 검색
│   ├── reservation       # 예약 생성·조회·취소, 동시성 락, 멱등성
│   ├── payment           # Mock 결제 준비·성공·실패 처리
│   ├── timesale           # 타임세일 등록·상태 전이·가격 계산
│   ├── specialOffers      # 선착순 특가 등록·점유(occupy) API
│   ├── waitlist            # 대기열 승계·만료 스케줄러
│   └── admin
│       ├── accommodation  # 관리자 숙소 등록·논리 삭제
│       ├── room             # 관리자 객실 등록·상태 변경·논리 삭제
│       ├── timesale          # 관리자 타임세일 등록
│       └── specialoffer       # 관리자 선착순 특가 등록
└── global
    ├── common
    │   └── s3           # S3 업로드, CloudFront invalidation
    ├── security          # JWT 필터, SecurityConfig, AuthMember
    └── config
        ├── cache         # Redis CacheManager(transactionAware)
        ├── kafka          # Kafka Producer/Consumer 설정
        ├── place           # Kakao Local API 클라이언트 설정
        ├── portone         # 결제 관련 설정
        └── s3              # S3/CloudFront 설정
```

각 도메인은 기능이 추가될 때 `controller`, `facade`, `service`, `repository`, `entity`, `dto` 하위 패키지를 만들고, 사용되지 않는 빈 계층은 미리 만들지 않습니다.

## 와이어프레임

<details>
<summary>와이어프레임</summary>
<img src="docs/FRAME.png" width="1500" />
</details>

## ERD

<details>
<summary>ERD</summary>
<img src="docs/ERD.png" width="" />
</details>

전체 테이블 정의는 [`docs/ERD.md`](docs/ERD.md)에서 확인합니다. `MEMBERS`, `ACCOMMODATIONS`, `ROOMS`, `ACCOMMODATION_IMAGES`, `ROOM_IMAGES`, `RESERVATIONS`, `PAYMENTS`를 중심으로 하며, `ROOM`은 객실 유형이 아니라 실제 예약되는 물리적 객실 1개를 나타내고 별도 재고 테이블 없이 예약 날짜 겹침으로 예약 가능 여부를 계산합니다. 예약당 결제는 MVP에서 1:1로 제한합니다.

## API 명세서

전체 명세는 담당자별 문서(`docs/API_SPEC_MEMBER.md`, `docs/API_SPEC_OWNER.md`, `docs/API_SPEC_ADMIN.md`, `docs/API_SPEC_PAYMENT.md`)에서 관리합니다. 아래는 대표 엔드포인트입니다.

<details>
<summary><strong>인증·회원 (`AuthController`)</strong></summary>

### POST `/api/v1/auth/signup`

이메일·비밀번호·이름으로 회원가입한다. 생성된 회원의 권한은 항상 `USER`이다.

**Request**

```json
{
  "email": "user@roompick.com",
  "password": "roompick1234",
  "name": "홍길동"
}
```

**Response — 201 Created**

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "data": { "memberId": 1, "email": "user@roompick.com", "name": "홍길동" }
}
```

**Error**: `400 INVALID_INPUT_VALUE`, `400 DUPLICATED_EMAIL`

### POST `/api/v1/auth/login`

**Response — 200 OK**

```json
{
  "success": true,
  "message": "로그인에 성공했습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer"
  }
}
```

**Error**: `400 INVALID_INPUT_VALUE`, `401 INVALID_LOGIN`

### POST `/api/v1/auth/refresh`

Refresh Token은 1회용이며 재발급마다 회전(재사용 시 `401 INVALID_REFRESH_TOKEN`)한다.

### POST `/api/v1/auth/logout` (인증 필요)

Access/Refresh Token을 모두 Redis 블랙리스트에 등록한다.

</details>

<details>
<summary><strong>숙소·객실 (`AccommodationController`, `RoomController`, `PlaceController`)</strong></summary>

### GET `/api/v1/accommodations`

`ACTIVE` 숙소 목록을 ID 오름차순 페이지 단위로 조회한다(인증 불필요).

```json
{
  "success": true,
  "message": "숙소 목록 조회에 성공했습니다.",
  "data": {
    "content": [{ "accommodationId": 1, "name": "룸픽 호텔", "address": "서울특별시 강남구 테헤란로 123", "imageUrl": "https://images.roompick.ina3700.click/accommodations/....jpg" }],
    "pageNumber": 0, "pageSize": 20, "totalElements": 1, "totalPages": 1, "last": true
  }
}
```

### GET `/api/v1/accommodations/popular?period=WEEKLY&limit=10`

DAILY/WEEKLY 인기 숙소 TOP N을 Redis 랭킹 기준으로 조회한다. Redis 장애 시 최신 `ACTIVE` 숙소로 fallback한다.

### GET `/api/v1/accommodations/{accommodationId}` / `/api/v1/accommodations/{accommodationId}/rooms`

숙소 상세, 숙소별 `ACTIVE` 객실 목록을 조회한다. **Error**: `404 ACCOMMODATION_NOT_FOUND`, `409 ACCOMMODATION_INACTIVE`

### GET `/api/v1/rooms/{roomId}` / `/api/v1/rooms/{roomId}/availability`

객실 상세, 체크인·체크아웃·인원 기준 예약 가능 여부(`available`, `status: ACTIVE|SOLD_OUT`)를 조회한다. **Error**: `404 ROOM_NOT_FOUND`, `409 ROOM_INACTIVE`, `400 INVALID_STAY_PERIOD`

### GET `/api/v1/places/search?query=강남역&limit=5`

Kakao Local API로 장소 후보와 좌표를 검색한다(Redis 5분 캐시). **Error**: `502/503/504` Kakao 연동 오류

### GET `/api/v1/accommodations/search?latitude=&longitude=&radiusKm=&limit=`

선택 좌표 기준 주변 숙소를 거리순으로 조회한다(MySQL Bounding Box 또는 Elasticsearch).

</details>

<details>
<summary><strong>예약 (`ReservationController`)</strong></summary>

### POST `/api/v1/reservations` (인증 필요, `Idempotency-Key` 헤더 필수)

**Request**

```json
{ "roomId": 1, "checkInDate": "2026-08-10", "checkOutDate": "2026-08-12", "guestCount": 2 }
```

**Response — 201 Created**

```json
{
  "success": true,
  "message": "예약이 생성되었습니다. 제한 시간 내에 결제를 완료해 주세요.",
  "data": {
    "reservationId": 1, "memberId": 1,
    "accommodation": { "accommodationId": 1, "name": "룸픽 호텔" },
    "room": { "roomId": 1, "name": "디럭스 더블룸", "roomNumber": "101" },
    "checkInDate": "2026-08-10", "checkOutDate": "2026-08-12", "guestCount": 2,
    "nightCount": 2, "pricePerNight": 100000, "totalAmount": 200000,
    "status": "PENDING_PAYMENT", "expiresAt": "2026-08-01T14:10:00"
  }
}
```

**Error**: `400 INVALID_STAY_PERIOD`, `400 INVALID_GUEST_COUNT`, `400 ROOM_CAPACITY_EXCEEDED`, `401 UNAUTHORIZED`, `404 ROOM_NOT_FOUND`, `409 ROOM_INACTIVE`, `409 ROOM_NOT_AVAILABLE`, `409 RESERVATION_LOCK_TIMEOUT`, `409 RESERVATION_IDEMPOTENCY_CONFLICT`

### GET `/api/v1/reservations` / GET `/api/v1/reservations/{reservationId}` (인증 필요)

내 예약 목록(`createdAt DESC` 고정)·상세를 조회한다. **Error**: `403 RESERVATION_ACCESS_DENIED`, `404 RESERVATION_NOT_FOUND`

### PATCH `/api/v1/reservations/{reservationId}/cancel` (인증 필요)

`PENDING_PAYMENT`/`CONFIRMED` 예약을 취소한다. **Error**: `409 RESERVATION_NOT_CANCELABLE`, `409 PAYMENT_REFUND_FAILED`

</details>

<details>
<summary><strong>결제 (`PaymentController`)</strong></summary>

### POST `/api/v1/reservations/{reservationId}/payments` (인증 필요)

`PENDING_PAYMENT` 예약의 `totalAmount`로 `READY` 결제를 생성한다.

```json
{ "success": true, "message": "결제가 준비되었습니다.", "data": { "paymentId": 1, "reservationId": 10, "amount": 200000, "status": "READY" } }
```

**Error**: `404 RESERVATION_NOT_FOUND`, `409 RESERVATION_NOT_PAYABLE`, `409 RESERVATION_PAYMENT_EXPIRED`, `409 PAYMENT_ALREADY_EXISTS`

### POST `/api/v1/payments/{paymentId}/approve` (인증 필요)

Mock 결제 승인. `Payment: READY → PAID`, `Reservation: PENDING_PAYMENT → CONFIRMED`를 한 트랜잭션으로 처리한다.

**Request**: `{ "amount": 200000 }` (서버 저장 금액과 일치해야 함)

**Error**: `400 PAYMENT_AMOUNT_MISMATCH`, `409 INVALID_PAYMENT_STATUS`, `409 RESERVATION_NOT_PAYABLE`, `409 RESERVATION_PAYMENT_EXPIRED`

### POST `/api/v1/payments/{paymentId}/fail` (인증 필요)

Mock 결제 실패. `Payment: READY → FAILED`, `Reservation: PENDING_PAYMENT → CANCELED`로 객실 점유를 해제한다.

</details>

<details>
<summary><strong>관리자 (`AdminAccommodationController`, `AdminRoomController`, `AdminTimeSaleController`, `AdminSpecialOfferController`)</strong></summary>

모든 관리자 API는 `Authorization: Bearer {accessToken}`과 `ADMIN` 권한이 필요하다(`401 UNAUTHORIZED` / `403 FORBIDDEN`).

### POST `/api/v1/admin/accommodations` (`multipart/form-data`)

`name`, `address`, `description`, `latitude`, `longitude`, `checkInTime`, `checkOutTime`, `images[]`를 받아 `ACTIVE` 상태의 숙소를 등록한다. **Error**: `400 ACCOMMODATION_NAME_REQUIRED`, `502 IMAGE_UPLOAD_FAILED`

### POST `/api/v1/admin/accommodations/{accommodationId}/rooms` (`multipart/form-data`)

`roomNumber`, `name`, `pricePerNight`, `standardCapacity`, `maxCapacity`, `images[]`를 받아 `INACTIVE` 상태의 객실을 등록한다. **Error**: `409 ROOM_NUMBER_DUPLICATED`, `409 ACCOMMODATION_INACTIVE`

### PATCH `/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}/status`

```json
{ "status": "ACTIVE" }
```

`ACTIVE`/`INACTIVE`만 허용하며 멱등 API다. 숙소가 `INACTIVE`이면 객실을 `ACTIVE`로 바꿀 수 없다.

### DELETE `/api/v1/admin/accommodations/{accommodationId}` / `/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}`

물리 삭제 대신 `INACTIVE`로 전환하는 논리 삭제. 예약·결제·이미지 데이터는 유지하며, 인기 숙소 캐시는 트랜잭션 커밋 후 무효화한다.

### POST `/api/v1/admin/accommodations/{accommodationId}/time-sales`

```json
{ "roomId": null, "discountRate": 20, "startAt": "2026-08-14T10:00:00", "endAt": "2026-08-14T18:00:00" }
```

`roomId`가 없으면 숙소 전체, 있으면 해당 객실 전용 타임세일이다. **Error**: `409 TIME_SALE_PERIOD_OVERLAP`, `409 TIME_SALE_TARGET_MISMATCH`

</details>

## 로컬 개발 환경

필수 도구는 JDK 17과 Docker입니다.

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

`docker compose up -d`는 MySQL, Redis, Elasticsearch를 실행합니다. Kafka가 필요하면 별도 컴포즈 파일을 추가로 실행합니다.

```bash
docker compose -f kafka/docker-compose.kafka.yml up -d
```

애플리케이션 상태는 `GET http://localhost:8080/actuator/health`에서 확인합니다.

MySQL에 직접 접속할 때는 로그인부터 시작합니다.

```bash
mysql -h 127.0.0.1 -P 3307 -u roompick -p
```

비밀번호는 `.env`의 `DB_PASSWORD` 값이며 기본 예시는 `roompick`입니다.

```sql
USE roompick;
SHOW TABLES;
```

종료할 때 데이터는 보존됩니다.

```bash
docker compose down
```

## 테스트

```bash
./gradlew test
```

테스트는 별도의 H2 인메모리 데이터베이스를 사용하므로 로컬 MySQL이 없어도 실행할 수 있습니다. 동시성·락 관련 검증처럼 실제 MySQL 동작 확인이 필요한 테스트는 Testcontainers 기반 통합 테스트로 분리되어 있습니다.

```bash
./gradlew integrationTest
```

## 배포

AWS EC2 + RDS 배포 절차, 모니터링(Prometheus/Grafana/Kafka) 구성, CI/CD 자동 배포와 롤백 절차는 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)에서 확인합니다. `develop`에 push되면 GitHub Actions가 테스트 → 빌드 → GHCR push → EC2 배포까지 자동으로 진행합니다.

## 브랜치와 PR

1. `develop`에서 작업 브랜치를 만듭니다.
2. `feature/{기능명}` 형식을 사용합니다.
3. PR은 `develop`을 대상으로 작성합니다.
4. 최소 1명의 리뷰 후 병합합니다.

상세 규칙과 설계 문서는 [`docs`](docs/)에서 확인합니다.
