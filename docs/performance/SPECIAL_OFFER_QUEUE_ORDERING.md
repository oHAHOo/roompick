# 한정 수량 특가 대기열 처리 순서 개선 전후 비교

> VU 수를 단계적으로 늘려가며 한계점을 찾는 부하 테스트는
> `docs/performance/SPECIAL_OFFER_OCCUPY_LOAD_TEST.md`에 별도로 기록했다.
> 이 문서는 순서 보장 방법론과 최초 30 VU 측정만 다룬다.

## 1. 배경

이슈 #131은 한정 수량 특가에 다수 사용자가 동시에 몰릴 때, 기존 객실 단위
비관적 락 경쟁 방식이 "먼저 요청한 사용자가 우선 처리된다"는 순서를
보장하지 않는다는 문제에서 출발했다. 락 경쟁 방식과 Kafka 파티션 +
단일 컨슈머 방식을 비교한 최초 실측(30건 동시 요청, 순수 자바 시뮬레이션)은
`docs/RESERVATION_QUEUE_BACKGROUND.md`에 이미 기록되어 있다.

| 방식 | 요청 순서와 처리 순서가 다른 비율 |
|---|---|
| 락 경쟁 방식 | 30건 중 29건 (96.7%) |
| Kafka 방식 (파티션 + 단일 컨슈머) | 30건 중 0건 (0%) |

이 수치는 순서 보장 메커니즘 자체(락 vs 파티션 로그)를 비교한 것이며,
실제로 구현된 `SpecialOffer`/`Waitlist` API 엔드포인트를 대상으로 측정한
값은 아니다. 이 문서는 이슈 #131로 실제 구현된 시스템을 대상으로 한
엔드포인트 단위 측정 방법과 결과를 남긴다.

## 2. 측정 대상 구조

```
사용자 요청
    → POST /api/v1/special-offers/{offerId}/occupy-requests (Producer)
    → offer-occupy-request 토픽, partition key = offerId
    → OfferOccupyEventConsumer (파티션당 컨슈머 1개)
    → WaitlistService.occupy(): 존재 확인 → HOLD 또는 WAIT 등록
        → HOLD인 경우 기존 Reservation.create() + DB 비관적 락 호출
```

개선 전 구조는 이 대기열 계층 없이 `POST /api/v1/reservations`가 곧바로
`roomService.findReservableRoomForUpdate()` 비관적 락을 거치는 것이다.
동일 객실·기간에 다수 요청이 몰리면 처리 순서는 락 획득 순서에 좌우된다.

## 3. 측정 방법

### 3.1 접수(Producer) 단계 처리량·오류율

`performance/k6/special-offer-occupy-stampede.js`가 담당한다. `setup()`에서
VU 수만큼 회원을 가입시켜 각자의 액세스 토큰을 발급받고, 각 VU가 동일한
`offerId`에 정확히 한 번씩 점유 요청을 보내는 burst를 만든다.

```bash
# 1) 관리자 API로 ACTIVE 상태의 특가 상품을 미리 등록하고 offerId를 확인한다.
# 2) 로컬 Kafka·MySQL·애플리케이션을 기동한 상태에서 실행한다.

OFFER_ID=<등록한 특가 ID> VUS=30 \
  k6 run performance/k6/special-offer-occupy-stampede.js
```

이 스크립트는 `202 Accepted` 응답 비율과 응답 시간만 측정한다. 실제로
누가 HOLD를 받았는지, 요청 순서와 HOLD 배정 순서가 일치하는지는 이
스크립트만으로 알 수 없다 — Producer는 접수만 담당하고 실제 처리는
컨슈머가 비동기로 하기 때문이다.

### 3.2 처리 순서 일치율

접수 순서와 실제 처리 순서(HOLD 배정)가 일치하는지는 각 요청 시점의
`requestedAt`과, 처리 후 `GET /occupy-requests/me` 또는 `waitlists` 테이블의
`status`/`requested_at`을 대조해서 확인한다. `WaitlistOccupyOrderMySqlIntegrationTest`
(순차 호출 기준)와 `WaitlistExpirationAndPromotionMySqlIntegrationTest`
(만료·승계 기준)가 애플리케이션 로직 수준에서 이 순서 일치를 이미
자동화된 통합 테스트로 검증한다.

실제 동시 요청 burst에서의 일치율을 수치로 확인하려면, k6 실행 후
`waitlists` 테이블을 `requested_at` 순으로 조회해 첫 번째 행이 항상
`HOLD`인지, 그 이후 행이 도착 순서대로 `WAIT`인지 확인한다.

```sql
SELECT member_id, status, requested_at
FROM waitlists
WHERE special_offer_id = <OFFER_ID>
ORDER BY requested_at ASC;
```

## 4. 실제 측정 결과

### 4.1 측정 환경

| 항목 | 값 |
|---|---|
| 실행 일시 | 2026-08-16 |
| 운영체제 | 로컬 Windows, Docker Desktop |
| Kafka | apache/kafka:3.8.0, 로컬 Docker 단일 브로커 (KRaft), 파티션 3개 |
| 컨슈머 | `special-offer-occupy-consumer`, concurrency=3 (파티션당 1개) |
| MySQL | 8.4, 로컬 Docker |
| 애플리케이션 | `./gradlew bootRun --args='--spring.profiles.active=local'`, 인스턴스 1개 |
| k6 | `grafana/k6` Docker 이미지 |
| 대상 | `POST /api/v1/special-offers/{offerId}/occupy-requests`, 동일 특가 상품 1건 |
| 동시 요청 | k6 30 VU, VU당 1회, 총 30건 burst |

토픽 파티션 수(3)와 컨슈머 concurrency(3)는 [[project-kafka-consumer-group-sizing]]
메모에 남긴 대로 아직 k6 실측 기반으로 확정한 값이 아니라, 코드에 미리
반영해 둔 잠정치를 그대로 사용했다.

### 4.2 Producer 접수 단계 결과

| VU 수 | 202 응답 비율 | 오류율 | 평균 응답시간 | p95 | p99 | 처리량 |
|---:|---:|---:|---:|---:|---:|---:|
| 30 | 100% (30/30) | 0% | 147.71ms | 267.85ms | 271.63ms | 14.81 req/s |

### 4.3 처리 결과 (waitlists 테이블 직접 조회)

동일 요청 30건 처리 후 `waitlists`를 조회한 결과, 정확히 1건만 `HOLD`,
나머지 29건은 `WAIT`로 등록되었다 — 중복 점유나 예외 없이 안전하게
처리됨을 확인했다.

```sql
SELECT status, COUNT(*) FROM waitlists WHERE special_offer_id = 1 GROUP BY status;
-- HOLD: 1, WAIT: 29
```

**처리 순서 일치율은 이번 측정에서 수치로 확인하지 못했다.** `requested_at`이
밀리초 단위인데 30건의 k6 요청이 수 밀리초 간격으로 몰려 여러 행이 동일한
타임스탬프를 가졌다 — 이 컬럼만으로는 실제 도착 순서를 정밀하게 재구성할
수 없다. 실제 처리 순서를 결정하는 것은 `requested_at` 값이 아니라 Kafka
파티션 오프셋이므로, 순서 일치율을 정확히 측정하려면 Producer가 각 요청의
오프셋을 함께 기록하거나 별도 로깅이 필요하다 — 이번 측정 범위에는
포함하지 않았다.

### 4.4 메모리 제한을 건 재측정

4.1~4.3절 측정은 컨테이너·JVM에 메모리 제한을 걸지 않은 상태였다. 운영
환경(`docs/DEPLOYMENT.md`)은 EC2 `t3.small`(2vCPU/2GB) 위에서 앱·Prometheus·
Grafana·Redis가 이미 메모리를 나눠 쓰고 있는데, **이 배포 문서에는 Kafka
브로커를 어디에 얼마의 메모리로 둘지가 아직 정의되어 있지 않다**
([[project-kafka-consumer-group-sizing]] 메모 참고 — 아직 결정 안 된 항목).

그래서 실제 운영 수치를 그대로 재현할 수는 없었고, 대신 소형 인스턴스
(`t3.micro` 수준)를 가정한 **보수적인 임의 제한값**으로 전체 스택을 다시
제한하고 재측정했다. 아래 수치는 실측이지만, 제한값 자체는 운영에서
확정된 값이 아니라 추정치임을 명시한다.

| 구성요소 | 메모리 제한 | 방식 |
|---|---:|---|
| MySQL | 512MB | `docker update --memory` |
| Redis | 128MB | `docker update --memory` |
| Kafka | 512MB (힙 256MB) | `KAFKA_HEAP_OPTS=-Xmx256m -Xms256m` + 컨테이너 512MB |
| 애플리케이션 | JVM 힙 512MB | `JAVA_TOOL_OPTIONS=-Xmx512m -Xms256m` (컨테이너화하지 않고 호스트에서 직접 실행했으므로 힙만 제한, 프로세스 전체 메모리는 미제한) |

같은 방식으로 새 특가 상품(`specialOfferId=2`)을 등록하고 동일하게 k6
30 VU를 실행했다.

| 항목 | 제한 없음 (4.2) | 메모리 제한 |
|---|---:|---:|
| 202 응답 비율 | 100% (30/30) | 100% (30/30) |
| 오류율 | 0% | 0% |
| 평균 응답시간 | 147.71ms | 142.77ms |
| p95 | 267.85ms | 246.92ms |
| p99 | 271.63ms | 250.58ms |
| HOLD/WAIT 결과 | HOLD 1 / WAIT 29 | HOLD 1 / WAIT 29 |

부하 종료 직후 컨테이너 메모리 사용량(`docker stats`): MySQL 459.9MiB/512MiB
(89.8%), Kafka 306.8MiB/512MiB (59.9%), Redis 9.9MiB/128MiB (7.7%) — 제한을
넘기지 않고 안정적으로 처리했다.

**해석**: 이번 30건 burst 규모에서는 메모리 제한 유무가 응답시간·성공률에
유의미한 차이를 만들지 않았다. 다만 MySQL이 90%에 근접한 건 이 규모에서도
여유가 크지 않다는 신호이고, VU 수를 더 키우면 (특히 MySQL 512MB 제한에서)
차이가 드러날 가능성이 있다 — 이번 측정 범위(30건)에서는 확인하지 못했다.

## 5. 원본 파일

- k6 스크립트: `performance/k6/special-offer-occupy-stampede.js`
- 순서 보장 통합 테스트: `src/test/java/com/roompick/domain/waitlist/service/WaitlistOccupyOrderMySqlIntegrationTest.java`
- 만료·승계 통합 테스트: `src/test/java/com/roompick/domain/waitlist/service/WaitlistExpirationAndPromotionMySqlIntegrationTest.java`
- 기존 락 경쟁 vs Kafka 방식 비교(시뮬레이션): `docs/RESERVATION_QUEUE_BACKGROUND.md`

## 6. 한계

- 4.3절에서 설명한 대로, `requested_at`의 밀리초 해상도로는 30건 burst의
  실제 도착 순서를 구분할 수 없어 처리 순서 일치율은 수치로 측정하지
  못했다. 순서 보장 자체(정확히 1건만 HOLD)는 확인했지만, "1등이 실제로
  가장 먼저 요청한 사람인가"는 이번 측정으로 증명하지 않았다 — 이건
  `WaitlistOccupyOrderMySqlIntegrationTest`처럼 애플리케이션 로직을
  직접 순차 호출하는 통합 테스트가 대신 검증한다.
- k6 스크립트의 `setup()`은 매 실행마다 새 회원을 가입시킨다. 반복
  실행 시 회원 테이블에 테스트 데이터가 누적되므로, 별도 정리 스크립트가
  필요하다.
- 컨슈머 처리 지연이 있는 경우, Producer 응답(202)과 실제 HOLD/WAIT
  확정 사이에 시간차가 생긴다. 3.2절의 SQL 조회는 컨슈머가 모든 메시지를
  처리한 뒤에 실행해야 정확하다.
- 이번 측정은 단일 로컬 Docker 브로커(1노드)·MySQL·애플리케이션 인스턴스
  1개로 진행했다. 운영 환경의 다중 브로커·다중 인스턴스 처리량은 이
  수치로 일반화할 수 없다.
- VU 30건은 짧은 burst 참고값이며 지속 부하 처리량이 아니다. 첫 실행이라
  JVM JIT·커넥션 워밍업 비용의 영향을 받을 수 있다.
- 4.4절의 메모리 제한값은 운영에서 확정된 수치가 아니라, 운영 배포 문서에
  Kafka 메모리 배치가 아직 정의되지 않은 상태에서 소형 인스턴스를 가정해
  임의로 정한 추정치다. 실제 운영 배치가 결정되면 그 값으로 다시 측정해야
  한다.
- 애플리케이션은 컨테이너화하지 않고 호스트에서 `JAVA_TOOL_OPTIONS`로 JVM
  힙만 제한했다. 컨테이너 전체 메모리(스레드 스택, 메타스페이스, 네이티브
  메모리 포함)를 제한한 것은 아니므로 운영의 컨테이너 `--memory` 제한과
  완전히 동일한 조건은 아니다.

## 7. 알려진 버그 — 만료·승계 실패 (수정 완료)

`WaitlistExpirationAndPromotionMySqlIntegrationTest`를 실제 MySQL
(Testcontainers)로 실행한 결과, 만료된 HOLD를 다음 대기자에게 승계하는
흐름이 `ErrorCode.ROOM_NOT_AVAILABLE`로 실패하는 것을 확인했다.

`WaitlistService.HOLD_DURATION_MINUTES`(5분)와 `ReservationService`의
`PAYMENT_WAIT_MINUTES`(10분)가 서로 다른 값이며 연결되어 있지 않다.
`Reservation.expiresAt`은 `WaitlistService`가 넘기는 시각과 무관하게
`ReservationService` 내부에서 실제 벽시계 시각 기준으로 독립 계산된다.

그 결과 waitlist HOLD가 5분 시점에 만료돼 승계를 시도해도, 이전
점유자의 `Reservation.expiresAt`은 아직 10분이 지나지 않아 유효한
것으로 남아있고, `existsActiveOverlappingReservation`이 겹침으로
판단해 다음 대기자의 새 예약 생성을 거부한다. 최소
`PAYMENT_WAIT_MINUTES - HOLD_DURATION_MINUTES`(5분) 동안 승계가
계속 실패한다.

`Waitlist`에 `reservation_id`를 추가(V17 마이그레이션)해 HOLD가 만든
예약을 추적하고, HOLD 만료 시 `ReservationService.findById()` +
`cancelByPaymentFailure()`로 그 예약을 명시적으로 취소한 뒤 다음
대기자를 승격하도록 수정했다. `WaitlistExpirationAndPromotionMySqlIntegrationTest`
3개 테스트 모두 Testcontainers MySQL 실제 환경에서 통과를 확인했다.
재현/검증: `./gradlew integrationTest --tests
"com.roompick.domain.waitlist.service.WaitlistExpirationAndPromotionMySqlIntegrationTest"`
