# RoomPick 코드 컨벤션

- 프로젝트명: **RoomPick(룸픽)**
- 문서 버전: `v0.2`
- 작성일: 2026-07-21
- 팀 구성: 3명
- 팀장: 임선구
- 기준: 초기 MVP 합의와 승인된 후속 기능을 함께 반영한 현재 기준

이 문서는 RoomPick 백엔드의 코드 작성 방식과 협업 기준을 통일하기 위한 문서다. 팀에서 새로운 결정을 내리면 문서를 먼저 수정하고 코드에 반영한다.

---

## 1. 기본 개발 원칙

1. MVP에 필요한 기능만 구현하며, 합의되지 않은 기능을 임의로 추가하지 않는다.
2. 합의된 범위만 구현한다. 현재는 장소 후보 검색과 좌표 기반 주변 숙소 검색까지 승인된 후속 범위에 포함한다.
3. 기본 호출 흐름은 `Controller → Facade → Service → Repository`로 통일한다.
4. Controller에 비즈니스 로직을 작성하지 않는다.
5. 여러 Service가 필요한 유스케이스는 Facade에서 조율한다.
6. Service끼리 직접 호출하지 않는다.
7. Entity의 상태는 Entity의 비즈니스 메서드로 변경한다.
8. Entity를 API 요청이나 응답으로 직접 사용하지 않는다.
9. 기능 구현과 함께 정상·실패 테스트를 작성한다.
10. 불필요한 DB 조회, 조인, 저장, flush와 긴 트랜잭션을 피한다.
11. AI가 작성한 코드도 담당자가 모든 동작을 이해하고 설명할 수 있어야 한다.

---

## 2. Java 코드 스타일

- Java 코드는 **네이버 Java 코드 컨벤션**을 기준으로 한다.
- 들여쓰기는 공백 4칸을 사용한다.
- 탭 문자를 사용하지 않는다.
- 와일드카드 import를 사용하지 않는다.
- 사용하지 않는 import와 코드는 커밋 전에 제거한다.
- 한 줄에 여러 문장을 작성하지 않는다.
- 클래스와 메서드 이름은 역할이 드러나도록 작성한다.
- 매직 넘버와 문자열은 상수 또는 Enum으로 관리한다.
- 코드에서 `System.out.println()`을 사용하지 않고 로거를 사용한다.

프로젝트의 Java·Spring Boot·Gradle 버전은 프로젝트 생성 시 팀에서 확정하여 이 문서에 추가한다.

---

## 3. 패키지 구조

도메인 중심으로 패키지를 나누고 공통 기능은 `global`에 둔다.

```text
com.example.roompick
├── global
│   ├── config
│   ├── security
│   ├── exception
│   ├── response
│   └── util
├── member
│   ├── controller
│   ├── facade
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── accommodation
│   ├── controller
│   ├── facade
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── room
│   ├── controller
│   ├── facade
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── reservation
│   ├── controller
│   ├── facade
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
└── payment
    ├── controller
    ├── facade
    ├── service
    ├── repository
    ├── entity
    ├── dto
    └── client
```

### 패키지 규칙

- 실제 클래스가 생길 때만 패키지를 만든다.
- 도메인 코드를 `global`에 넣지 않는다.
- 특정 도메인에서만 사용하는 코드는 해당 도메인 내부에 둔다.
- 실제 PG 연동 코드는 `payment.client` 아래에 둔다.
- 검색, 리뷰, AI 등 MVP 이후 기능은 승인된 구현 시점에 도메인 패키지를 추가한다. 현재 장소 검색은 `place` 도메인에 둔다.
- 기본 패키지명 `com.example.roompick`은 프로젝트 생성 시 최종 Group ID에 맞게 확정한다.

---

## 4. 계층별 책임

### Controller

- HTTP 요청과 응답을 처리한다.
- `@Valid`를 이용해 입력 형식을 검증한다.
- 인증된 회원 정보를 전달한다.
- 비즈니스 로직을 작성하지 않는다.
- Repository나 Service를 직접 호출하지 않고 Facade를 호출한다.

### Facade

- 하나의 사용자 유스케이스 전체를 조율한다.
- 여러 도메인 Service의 호출 순서와 실패 흐름을 관리한다.
- Service의 내부 정책을 중복 구현하지 않는다.
- 외부 API 호출이 포함되면 DB 트랜잭션이 불필요하게 길어지지 않도록 구간을 분리한다.

### Service

- 한 도메인의 비즈니스 규칙과 상태 변경을 담당한다.
- 다른 도메인의 Service를 직접 호출하지 않는다.
- 조회와 변경에 필요한 최소한의 Repository만 사용한다.

### Repository

- 데이터 저장과 조회만 담당한다.
- 비즈니스 정책을 작성하지 않는다.
- `findAll()` 조회 후 애플리케이션에서 필터링하는 방식을 피한다.
- 존재 여부만 필요할 때는 `existsBy...()` 사용을 우선 검토한다.

### 기본 호출 방향

```text
ReservationController
→ ReservationFacade
   ├─ RoomAvailabilityService
   ├─ ReservationService
   └─ PaymentService
       ↓
    Repository
```

의존 방향을 거꾸로 만들거나 Controller에서 여러 Service를 조합하지 않는다.

---

## 5. 클래스 네이밍

| 종류 | 규칙 | 예시 |
| --- | --- | --- |
| Controller | `도메인Controller` | `ReservationController` |
| Facade | `도메인Facade` | `ReservationFacade` |
| Service | `도메인Service` | `ReservationService` |
| Repository | `도메인Repository` | `ReservationRepository` |
| Entity | 단수 명사 | `Member`, `Room`, `Reservation` |
| Request DTO | `기능RequestDto` | `ReservationCreateRequestDto` |
| Response DTO | `기능ResponseDto` | `ReservationDetailResponseDto` |
| 외부 연동 인터페이스 | `역할Client` | `PaymentClient` |
| 외부 연동 구현체 | `구현명Client` | `FakePaymentClient` |
| Exception | `도메인Exception` | `ReservationException` |
| Enum | 명확한 역할명 | `ReservationStatus` |
| 테스트 클래스 | `대상Test` | `ReservationServiceTest` |
| 통합 테스트 | `대상IntegrationTest` | `ReservationIntegrationTest` |

축약어보다 전체 단어를 사용한다.

```text
resId          지양
reservationId  권장
```

---

## 6. 메서드 네이밍

메서드 이름은 동사로 시작하고 수행하는 일을 구체적으로 표현한다.

```java
// 생성
createReservation()
createPayment()

// 단건 조회
getRoom()
getReservation()

// 목록 조회
getReservations()

// 상태 변경
confirmReservation()
cancelReservation()
completePayment()
failPayment()

// 객실 점유와 복구
reserveRoom()
releaseRoom()

// 검증
validateStayPeriod()
validateGuestCount()
validateCancelable()
```

Boolean 반환 메서드는 `is`, `has`, `can`으로 시작한다.

```java
isAvailable()
hasOverlappingReservation()
canCancel()
```

조회 메서드에서 상태를 변경하거나 저장하지 않는다.

---

## 7. 변수와 DB 컬럼 네이밍

Java 변수는 camelCase를 사용한다.

```java
memberId
accommodationId
roomId
reservationId
paymentId
checkInDate
checkOutDate
guestCount
totalAmount
```

DB 테이블과 컬럼은 snake_case를 사용한다.

```text
member_id
accommodation_id
room_id
reservation_id
payment_id
check_in_date
check_out_date
guest_count
total_amount
```

날짜 범위는 `startDate/endDate`보다 도메인의 의미가 드러나는 `checkInDate/checkOutDate`를 사용한다.

---

## 8. DTO 규칙

- 모든 Request·Response DTO는 `record`로 작성한다.
- DTO 파일명에는 `Dto` 접미사를 붙인다.
- Controller에서 Entity를 직접 받거나 반환하지 않는다.
- Request DTO에는 입력 형식 검증만 작성한다.
- 비즈니스 검증은 Entity 또는 Service에서 수행한다.
- 응답 변환은 `ResponseDto.from(entity)` 형태의 정적 팩토리 메서드를 사용한다.
- 비밀번호와 내부 상태 등 노출하면 안 되는 필드를 응답에 포함하지 않는다.

```java
public record ReservationCreateRequestDto(
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate,
        @Min(1) int guestCount
) {
}
```

```java
public record ReservationDetailResponseDto(
        Long reservationId,
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int guestCount,
        long totalAmount,
        ReservationStatus status
) {
    public static ReservationDetailResponseDto from(Reservation reservation) {
        return new ReservationDetailResponseDto(
                reservation.getId(),
                reservation.getRoomId(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getGuestCount(),
                reservation.getTotalAmount(),
                reservation.getStatus()
        );
    }
}
```

DTO 내부에 Repository 조회나 Entity 상태 변경 로직을 넣지 않는다.

---

## 9. API 응답 형식

모든 API는 공통 응답 형식을 사용한다.

```json
{
  "success": true,
  "message": "예약이 생성되었습니다.",
  "data": {}
}
```

실패 응답에는 팀에서 정한 에러 코드를 포함한다.

```json
{
  "success": false,
  "code": "RESERVATION_DATE_UNAVAILABLE",
  "message": "선택한 날짜에는 객실을 예약할 수 없습니다."
}
```

Controller에서는 `ResponseEntity` 변수를 선언한 뒤 반환한다.

```java
@PostMapping
public ResponseEntity<ApiResponse<ReservationDetailResponseDto>> createReservation(
        @AuthenticationPrincipal AuthMember authMember,
        @Valid @RequestBody ReservationCreateRequestDto request
) {
    ReservationDetailResponseDto result = reservationFacade.create(
            authMember.memberId(),
            request
    );

    ResponseEntity<ApiResponse<ReservationDetailResponseDto>> response =
            ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("예약이 생성되었습니다.", result));

    return response;
}
```

### HTTP 상태 코드

| 상황 | 상태 코드 |
| --- | --- |
| 조회·수정·취소 성공 | `200 OK` |
| 생성 성공 | `201 Created` |
| 요청 값 오류 | `400 Bad Request` |
| 미인증 | `401 Unauthorized` |
| 권한 없음 | `403 Forbidden` |
| 리소스 없음 | `404 Not Found` |
| 예약 중복·상태 충돌 | `409 Conflict` |
| 서버 오류 | `500 Internal Server Error` |

---

## 10. URL 컨벤션

- API 기본 경로는 `/api/v1`을 사용한다.
- URL에는 동사보다 복수형 명사를 사용한다.
- 리소스 상태 변경이 단순 CRUD로 표현되지 않을 때만 행위를 하위 경로로 사용한다.
- camelCase 대신 kebab-case를 사용한다.
- 합의되지 않은 검색 URL은 미리 만들지 않는다. 현재 공개 검색 URL은 `/api/v1/places/search`, `/api/v1/accommodations/search`이다.

```text
POST   /api/v1/auth/signup
POST   /api/v1/auth/login

GET    /api/v1/rooms/{roomId}
GET    /api/v1/rooms/{roomId}/availability

POST   /api/v1/reservations
GET    /api/v1/reservations
GET    /api/v1/reservations/{reservationId}
PATCH  /api/v1/reservations/{reservationId}/cancel

POST   /api/v1/reservations/{reservationId}/payments
```

위 URL은 컨벤션 예시이며 최종 API 명세 회의에서 확정한다.

---

## 11. Entity 규칙

- Entity는 기본 생성자를 `protected`로 제한한다.
- Entity에 공개 Setter를 만들지 않는다.
- Entity 생성은 `create()` 정적 팩토리 메서드를 사용한다.
- 상태 변경은 의미가 드러나는 비즈니스 메서드로 수행한다.
- Entity에 `@Data`, `@Setter`, `@Builder`를 사용하지 않는다.
- 연관관계는 실제 조회와 변경에 필요한 경우에만 설정한다.
- 모든 연관관계를 양방향으로 만들지 않는다.
- 컬렉션은 외부에서 직접 수정하지 못하게 한다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    public static Reservation create(/* 필요한 값 */) {
        Reservation reservation = new Reservation();
        reservation.status = ReservationStatus.PENDING_PAYMENT;
        return reservation;
    }

    public void confirm() {
        validatePendingPayment();
        this.status = ReservationStatus.CONFIRMED;
    }

    public void cancel() {
        validateCancelable();
        this.status = ReservationStatus.CANCELED;
    }
}
```

지양:

```java
reservation.setStatus(ReservationStatus.CONFIRMED);
```

권장:

```java
reservation.confirm();
```

---

## 12. 날짜·금액·상태 규칙

### 날짜

- 체크인·체크아웃처럼 날짜만 필요한 값은 `LocalDate`를 사용한다.
- 생성·수정 시각처럼 시각이 필요한 값은 `LocalDateTime`을 사용한다.
- 체크아웃 날짜는 숙박일에 포함하지 않는다.
- 날짜 비교 규칙은 여러 Service에 중복 작성하지 않는다.

### 금액

- MVP가 원화 정수 금액만 사용하면 금액은 `long`으로 관리한다.
- 변수명에는 단위 대신 의미를 표현한다. 예: `totalAmount`.
- 금액은 음수가 될 수 없도록 생성 시 검증한다.
- 실제 PG의 소수점 통화까지 지원할 경우 팀 합의 후 `BigDecimal`로 변경한다.

### 상태

- 상태는 문자열이 아닌 Enum으로 관리한다.
- DB에는 `EnumType.STRING`으로 저장한다.
- 상태 전이 검증은 Entity의 비즈니스 메서드에서 수행한다.

```text
ReservationStatus
PENDING_PAYMENT
CONFIRMED
CANCELED
EXPIRED
COMPLETED

PaymentStatus
READY
PAID
FAILED
REFUNDED
```

MVP에서 사용하지 않는 상태라도 확장 목적이 명확할 때만 추가하며, 사용되지 않는 분기를 미리 구현하지 않는다.

---

## 13. 트랜잭션 규칙

- Controller에 `@Transactional`을 사용하지 않는다.
- 단일 도메인의 상태 변경 트랜잭션은 Service에서 관리한다.
- 여러 DB 변경이 반드시 하나로 묶여야 할 때만 Facade 트랜잭션을 사용한다.
- 조회 전용 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 외부 결제 API 호출을 DB 트랜잭션 안에 오래 포함하지 않는다.
- 락을 사용하게 되면 락 점유 시간을 최소화한다.
- 변경 감지를 사용하는 경우 불필요한 `save()`와 `flush()`를 호출하지 않는다.
- 예외를 잡은 뒤 무시하여 잘못 커밋되는 상황을 만들지 않는다.

외부 결제 연동은 다음 단계로 분리하는 방식을 우선 검토한다.

```text
결제 준비 상태 저장
→ 짧은 트랜잭션 종료
→ 외부 결제 호출
→ 결제 결과와 예약 상태 반영
```

---

## 14. Repository와 DB 조회 규칙

- Repository 반환 타입으로 `null` 대신 `Optional`을 사용한다.
- 단건 조회는 조회 결과가 없을 때 도메인 예외로 변환한다.
- 존재 여부만 필요할 때 전체 Entity를 조회하지 않는다.
- 목록 조회에는 페이징을 우선 고려한다. 검색·목록 기능은 합의된 범위를 벗어나 임의로 추가하지 않는다.
- N+1 문제를 확인하고 필요한 경우에만 fetch join이나 EntityGraph를 적용한다.
- 모든 관계를 무조건 EAGER로 설정하지 않는다.
- 복잡한 조회가 생기면 QueryDSL을 검토하되 현재 요구에 필요하지 않은 검색 쿼리를 만들지 않는다.
- 운영 로그에서 민감 정보와 전체 SQL 파라미터가 노출되지 않도록 환경별 설정을 분리한다.

지양:

```java
reservationRepository.findAll().stream()
        .filter(/* 날짜와 회원 필터 */)
        .toList();
```

권장:

```java
reservationRepository.findByMemberId(memberId);
reservationRepository.existsOverlappingReservation(roomId, checkInDate, checkOutDate);
```

---

## 15. 예외 처리 규칙

- 모든 비즈니스 예외는 전역 예외 처리기로 전달한다.
- 에러 코드는 `ErrorCode` Enum으로 관리한다.
- 에러 코드에는 HTTP 상태와 사용자 메시지를 포함한다.
- 예상 가능한 비즈니스 실패를 일반 `RuntimeException`으로 던지지 않는다.
- 내부 구현 정보나 스택 트레이스를 API 응답에 노출하지 않는다.

```java
public enum ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "객실을 찾을 수 없습니다."),
    INVALID_STAY_PERIOD(HttpStatus.BAD_REQUEST, "숙박 기간이 올바르지 않습니다."),
    ROOM_NOT_AVAILABLE(HttpStatus.CONFLICT, "선택한 날짜에는 객실을 예약할 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    RESERVATION_NOT_CANCELABLE(HttpStatus.CONFLICT, "취소할 수 없는 예약입니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제에 실패했습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
}
```

```java
throw new ReservationException(ErrorCode.ROOM_NOT_AVAILABLE);
```

---

## 16. 의존성 주입과 Lombok

### 권장

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor
@Slf4j
```

- 의존성은 생성자 주입을 사용한다.
- Spring Bean 필드는 `final`로 선언한다.
- 생성자 주입에 `@RequiredArgsConstructor`를 사용할 수 있다.

### 지양

```java
@Data
@Setter
@Autowired // 필드 주입
@Builder   // Entity
```

- DTO는 record이므로 Lombok을 사용하지 않는다.
- Entity에는 공개 Setter와 Builder를 사용하지 않는다.

---

## 17. 주석과 로깅 규칙

### 주석

- 클래스 상단에는 해당 클래스의 책임을 설명하는 주석을 작성한다.
- 복잡한 정책은 무엇을 하는지보다 왜 필요한지를 설명한다.
- 코드만 읽어도 명확한 내용은 반복해서 주석으로 작성하지 않는다.
- TODO에는 담당자 또는 관련 Issue 번호를 남긴다.

```java
/**
 * 예약 생성·결제·객실 복구 순서를 조율하는 Facade다.
 */
@Component
@RequiredArgsConstructor
public class ReservationFacade {
}
```

### 로깅

- `@Slf4j`를 사용한다.
- 정상적인 주요 상태 변경은 `info`로 기록한다.
- 복구 가능한 예외 상황은 `warn`으로 기록한다.
- 예상하지 못한 장애는 전역 예외 처리기에서 `error`로 기록한다.
- 비밀번호, 토큰, 개인정보, 전체 결제 정보는 로그에 남기지 않는다.
- 로그에는 추적에 필요한 ID를 포함한다.

```java
log.info("Reservation confirmed. reservationId={}, memberId={}", reservationId, memberId);
```

---

## 18. 설정 파일과 민감 정보

- 운영 비밀번호, API Key, JWT Secret을 Git에 커밋하지 않는다.
- 환경별 설정은 Spring Profile로 분리한다.
- 공개 가능한 기본값만 `application.yml`에 작성한다.
- 로컬 전용 설정 파일과 `.env`는 `.gitignore`에 추가한다.
- `application.yml`, Security 설정, 공통 예외, Gradle 의존성을 수정하기 전에 팀에 공유한다.
- 더미데이터 생성 방식은 팀에서 하나로 통일한다.

---

## 19. 테스트 컨벤션

- 테스트 메서드 이름은 한글을 허용한다.
- 성공 케이스뿐 아니라 실패와 경계값을 함께 테스트한다.
- 테스트 하나에는 핵심 검증 목적 하나를 둔다.
- 외부 결제 연동은 Fake 또는 Mock으로 격리한다.
- 테스트 데이터는 테스트 내부에서 명확하게 생성한다.
- 테스트끼리 실행 순서와 데이터에 의존하지 않는다.

```java
@Test
void 예약_생성에_성공한다() {
}

@Test
void 체크아웃이_체크인보다_빠르면_예약에_실패한다() {
}

@Test
void 기존_예약과_날짜가_겹치면_예약에_실패한다() {
}

@Test
void 결제에_실패하면_점유한_객실을_복구한다() {
}

@Test
void 다른_회원의_예약은_취소할_수_없다() {
}
```

### 테스트 범위

- Entity: 상태 전이와 도메인 규칙
- Service: 비즈니스 로직과 Repository 연동
- Facade: 여러 도메인의 성공·실패 흐름
- Controller: 요청 검증, 인증, HTTP 상태와 응답 형식
- 통합 테스트: 예약 생성부터 결제·취소·객실 복구까지의 흐름

---

## 20. Git 브랜치 규칙

### 기본 브랜치

```text
main     배포 가능한 최종 코드
develop  기능 통합 브랜치
```

- `main`과 `develop`에 직접 push하지 않는다.
- 작업 전 현재 브랜치를 확인하고 팀에 작업 브랜치를 공유한다.
- 기능 브랜치는 `develop`에서 생성한다.
- 하나의 브랜치에는 하나의 Issue 목적만 담는다.

### 브랜치 이름

```text
feature/{issue-number}-{기능}
fix/{issue-number}-{오류}
refactor/{issue-number}-{대상}
docs/{issue-number}-{문서}
```

예시:

```text
feature/12-member-login
feature/18-room-detail
feature/23-reservation-create
feature/27-fake-payment
fix/31-payment-failure-restore
```

### 커밋 메시지

```text
feat: 예약 생성 기능 구현
fix: 결제 실패 시 객실이 복구되지 않는 오류 수정
refactor: 예약 흐름을 Facade로 분리
test: 중복 예약 실패 테스트 추가
docs: RoomPick API 명세 수정
chore: 테스트 의존성 추가
perf: 예약 가능 여부 조회 쿼리 개선
```

커밋 유형:

| 유형 | 의미 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 오류 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 변경 |
| `chore` | 설정·의존성·빌드 작업 |
| `perf` | 성능 개선 |

커밋은 리뷰와 롤백이 가능한 기능 단위로 나눈다.

---

## 21. PR 규칙

- 모든 기능은 Issue 생성 후 브랜치와 PR을 연결한다.
- PR 대상 브랜치는 기본적으로 `develop`이다.
- PR 작성자는 스스로 diff를 먼저 검토한다.
- 최소 1명의 승인을 받은 뒤 병합한다.
- 충돌 해결 후 테스트를 다시 실행한다.
- 자신의 담당 패키지 외 파일을 수정했다면 PR 설명과 팀 채널에 명시한다.
- 공통 파일 변경은 구현 전에 팀원에게 공유한다.

### PR 제목

```text
[Feat] 예약 생성 기능 구현
[Fix] 결제 실패 시 객실 복구 오류 수정
[Refactor] 예약 처리 Facade 분리
[Test] 예약 취소 통합 테스트 추가
```

### PR 본문 필수 항목

```text
## 작업 내용
- 구현하거나 수정한 내용

## 변경 이유
- 정책 및 기술 선택 근거

## 테스트
- 실행한 명령어
- 테스트 결과

## 확인이 필요한 부분
- 리뷰어가 집중해서 볼 내용

## 관련 Issue
- closes #번호

## AI 활용
- 사용 도구:
- 활용 범위:
- AI 결과에서 직접 검토·수정한 내용:
- 해당 코드를 직접 설명할 수 있는지: 예 / 아니요
```

---

## 22. 팀원별 담당 영역 초안

| 담당자 | MVP 주 담당 | 주요 패키지 |
| --- | --- | --- |
| 임선구(팀장) | 예약, 취소, 전체 흐름 통합 | `reservation`, 관련 `facade` |
| 팀원 A | 회원, 인증, 공통 응답·예외 | `member`, `global.security`, `global.response`, `global.exception` |
| 팀원 B | 숙소, 객실, 예약 가능 여부, 간단 결제 | `accommodation`, `room`, `payment` |

### 협업 원칙

- 담당자는 해당 도메인의 1차 구현과 문서화를 책임진다.
- 담당 패키지는 독점 영역이 아니며 변경이 필요하면 담당자와 먼저 공유한다.
- 결제 실패 시 객실 복구처럼 여러 도메인이 연결되는 흐름은 Facade 담당자와 함께 설계한다.
- 배포와 전체 통합 테스트를 팀장 혼자 담당하지 않는다.
- 팀원 A와 B의 실제 이름이 확정되면 이 표를 수정한다.
- 모든 팀원은 중간 브리핑 전 전체 예약 흐름을 설명할 수 있어야 한다.

### 공통 파일

다음 파일은 수정 전 팀에 공유한다.

```text
global/**
application*.yml
build.gradle
settings.gradle
docker-compose.yml
SecurityConfig
GitHub Actions workflow
```

---

## 23. AI 활용 규칙

- AI는 초안 작성, 코드 리뷰, 테스트 케이스 도출, 문서화에 사용할 수 있다.
- AI 결과를 그대로 복사하지 않고 현재 코드와 정책에 맞는지 검토한다.
- AI가 MVP 제외 기능을 임의로 추가하지 못하도록 RoomPick MVP 기준 문서를 함께 제공한다.
- 존재하지 않는 클래스·API·테이블을 가정하지 않았는지 확인한다.
- AI가 작성한 코드의 트랜잭션, 예외, 쿼리 수와 실패 복구 흐름을 담당자가 검토한다.
- 이해하지 못한 코드는 병합하지 않는다.
- 중요한 기술 선택에는 AI 제안과 팀의 최종 판단을 구분하여 기록한다.
- 프롬프트 개선 이력과 AI 성능 수치는 AI 기능 도입 버전부터 별도 관리한다.

---

## 24. PR 전 최종 체크리스트

### 구조

- [ ] 현재 작업 브랜치가 올바른가?
- [ ] Controller가 Facade만 호출하는가?
- [ ] Controller에 비즈니스 로직이 없는가?
- [ ] Service끼리 직접 호출하지 않는가?
- [ ] Entity를 API에 직접 사용하지 않는가?

### 코드

- [ ] DTO를 record로 작성하고 파일명에 `Dto`를 붙였는가?
- [ ] Entity 생성에 정적 팩토리 메서드를 사용했는가?
- [ ] 공개 Setter와 Entity Builder를 사용하지 않았는가?
- [ ] 상태 변경을 Entity 비즈니스 메서드로 수행했는가?
- [ ] 예외 코드와 메시지가 기존 형식과 일치하는가?
- [ ] 민감 정보가 로그와 Git에 포함되지 않았는가?

### DB·트랜잭션

- [ ] 불필요한 조회, 조인, `save()`, `flush()`가 없는가?
- [ ] 조회 전용 트랜잭션을 적용했는가?
- [ ] 외부 호출로 DB 트랜잭션이 길어지지 않는가?
- [ ] 실패 시 예약·객실·결제 상태가 일관되게 복구되는가?

### 테스트

- [ ] 정상 케이스 테스트가 있는가?
- [ ] 실패·권한·경계값 테스트가 있는가?
- [ ] 전체 테스트와 빌드가 통과하는가?
- [ ] 테스트가 실행 순서에 의존하지 않는가?

### 협업·문서

- [ ] 담당 패키지 외 수정 사항을 공유했는가?
- [ ] API 또는 정책 변경을 문서에 반영했는가?
- [ ] PR에 변경 이유와 테스트 결과를 작성했는가?
- [ ] AI 활용 범위와 직접 수정한 부분을 기록했는가?
- [ ] 작성한 코드를 본인이 설명할 수 있는가?

---

## 25. 문서 적용 우선순위

지침이 충돌하면 다음 순서로 적용한다.

```text
1. 팀이 회의에서 새로 확정한 결정
2. 최신 API 명세와 비즈니스 정책
3. 이 코드 컨벤션
4. 개인 선호 또는 AI 제안
```

팀 결정이나 API 명세가 변경되면 코드만 수정하지 않고 관련 문서도 함께 갱신한다.
