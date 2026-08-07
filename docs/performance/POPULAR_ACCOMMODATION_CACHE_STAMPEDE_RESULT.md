# 인기 숙소 응답 캐시 Stampede 제어 결과

## 1. 문제 배경

인기 숙소 API는 완성된 TOP N 응답 DTO 목록을 Redis에 60초 동안 저장한다. 캐시 HIT에서는
Redis Sorted Set 랭킹 조회, MySQL ACTIVE 숙소 조회, 순서 복원과 DTO 조합을 생략한다.

Cold cache는 해당 응답 캐시 Key가 없고 랭킹 Sorted Set과 MySQL 데이터는 존재하는
상태다. 이때 동일한 `period + 기준 날짜 + limit` 요청이 동시에 Cache Miss를 확인하고
같은 원본 조회를 반복하는 현상을 Cache Stampede라고 정의한다.

## 2. 개선 전후 구조

개선 전에는 동시 요청마다 `@Cacheable` 메서드가 Redis 랭킹과 MySQL을 조회한 뒤 같은
응답 캐시를 중복 생성할 수 있었다.

개선 후에는 애플리케이션 인스턴스 내부의 Key 단위 Single Flight가 다음 최종 작업을
하나의 `CompletableFuture`로 공유한다.

```text
Controller
→ AccommodationFacade
→ PopularAccommodationSingleFlightService
→ 정상 QueryService 조회
   또는 Redis 랭킹 장애 시 AccommodationService fallback
→ 동일 Key 대기 요청에 최종 결과 공유
```

Facade가 정상 조회와 Redis 장애 fallback을 조율하므로 Single Flight Service는 Redis나
숙소 도메인의 장애 정책을 알지 않는다. fallback 결과는 기존 정책대로 응답 캐시에
저장하지 않는다. DB `DataAccessException`과 비즈니스 예외도 fallback으로 숨기지 않는다.

Single Flight Key는 응답 캐시와 같은 `기간별 랭킹 Key + limit`이다. 따라서 DAILY와
WEEKLY, 서로 다른 날짜, 서로 다른 limit은 독립적으로 실행된다.

## 3. 대기 타임아웃과 정리 정책

운영 설정은 다음과 같다.

```yaml
roompick.cache.popular-accommodations.single-flight.wait-timeout: 5s
```

기본 5초는 현재 local/prod Redis 연결·응답 제한 300ms보다 충분히 길면서 원본 작업이
비정상적으로 지연될 때 대기 HTTP 스레드가 무제한 점유되는 것을 막기 위한 초기값이다.
운영 환경에서는 `POPULAR_ACCOMMODATION_SINGLE_FLIGHT_WAIT_TIMEOUT`으로 조정할 수 있다.

대기자는 `CompletableFuture.get(timeout, TimeUnit)`으로 제한 시간만 기다린다. timeout은
`PopularAccommodationSingleFlightTimeoutException`과 HTTP 503
`POPULAR_ACCOMMODATION_REQUEST_TIMEOUT`으로 응답한다. interruption은 interrupt 상태를
복구한 뒤 별도 503 예외로 변환한다.

대기자의 timeout 또는 interruption은 진행 중 Map 항목을 제거하거나 소유 작업을
취소하지 않는다. Map은 실제 최초 작업이 성공 또는 실패했을 때만
`remove(cacheKey, currentRequest)`로 정리한다. 이 비교 제거 방식은 이전 작업의 finally가
같은 Key에 나중에 등록된 Future를 삭제하지 못하게 한다.

Redis에는 300ms 연결·응답 제한이 이미 적용되어 있다. MySQL은 HikariCP와 JDBC 드라이버의
운영 연결 정책을 사용한다. Single Flight 대기 제한은 DB 작업 자체를 취소하는 쿼리
timeout이 아니므로, 장기 지연이 관측되면 MySQL 서버·드라이버 timeout을 전체 API 영향과
함께 별도로 결정해야 한다.

## 4. 자동화 테스트 범위

`PopularAccommodationCacheStampedeIntegrationTest`는 실제 Redis **응답 캐시**와 Mock
Ranking/Accommodation Service를 조합한다. 동시 진입을 결정적으로 통제하고 Service 역할
호출이 한 번인지 검증하지만, Mock 호출 횟수를 실제 Redis 명령이나 MySQL SELECT 횟수로
해석하지 않는다.

`PopularAccommodationCacheStampedeRealIntegrationTest`는 Redis 7.2 Testcontainer의 실제
Sorted Set, MySQL 8.4 Testcontainer의 실제 숙소 데이터를 사용한다. Cold cache 동일 요청
10건에서 실제 Ranking Repository 구현 호출 1회와 Hibernate Statistics의 prepared
statement 1회를 검증한다. Repository Spy는 지연 진입점을 제공할 뿐 실제 메서드를 호출한다.

단위 테스트는 동일 Key 성공·실패, DAILY/WEEKLY 분리, limit 분리, 캐시 비활성화 우회,
timeout 시 Map 유지, interruption 상태 복구와 소유 작업 완료 후 정리를 검증한다.

Redis 장애 fallback 동시 요청은 실제 Redis 응답 캐시와 Mock Service를 조합한 테스트에서
랭킹 조회 역할 1회, `findLatestActive(limit)` 1회, 10건 모두 HTTP 계층에 전달 가능한 정상
fallback DTO 수신, fallback 미캐싱을 검증했다. Redis 연결 차단 상태의 응답시간은 이번
측정에서 다시 측정하지 않았다.

## 5. 실제 측정 환경과 방법

| 항목 | 값 |
|---|---|
| 실행 일시 | 2026-08-06 |
| 운영체제 | 로컬 macOS |
| Java | OpenJDK 17.0.18 |
| Spring Boot | 3.5.14 |
| Redis | 7.4.10, 로컬 Docker |
| MySQL | 8.4.10, 로컬 Docker |
| 애플리케이션 인스턴스 | 1개 |
| API | `GET /api/v1/accommodations/popular?period=DAILY&limit=10` |
| 동시 요청 | k6 10 VU, VU당 1회, 총 10건 burst |
| 반복 | 개선 전·후 각각 3회 |

`setup-popular-accommodation-data.sh`로 MySQL ACTIVE 숙소 20개와 실제 DAILY/WEEKLY Redis
Sorted Set을 준비했다. 각 run 직전에 정확한 인기 숙소 **응답 캐시 Key만** 삭제했고,
랭킹 Sorted Set과 MySQL 데이터는 유지했다.

Redis 랭킹 조회 횟수는 `INFO commandstats`의 `ZREVRANGE` 호출 증가량으로 측정했다.
MySQL SELECT 횟수는 `SHOW GLOBAL STATUS LIKE 'Com_select'` 전후 차이에서 계측용 두 번째
상태 조회 1회를 제외했다. 응답시간과 처리량, 오류율은 k6 summary 원본을 사용했다.

개선 전은 임시 detached worktree의 `b83f36e` 커밋, 개선 후는 현재 작업 트리에서
측정했다. 현재 브랜치는 전환하지 않았다.

```bash
./performance/scripts/setup-popular-accommodation-data.sh

SCENARIO=before-single-flight RUNS=3 VUS=10 PERIOD=DAILY LIMIT=10 \
  ./performance/scripts/measure-popular-accommodation-stampede.sh

SCENARIO=single-flight-enabled RUNS=3 VUS=10 PERIOD=DAILY LIMIT=10 \
  ./performance/scripts/measure-popular-accommodation-stampede.sh
```

## 6. 개선 전 실제 결과

| run | Redis 랭킹 조회 | MySQL SELECT | 평균 | p95 | p99 | 처리량 | 오류율 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 10 | 10 | 171.21ms | 171.72ms | 171.78ms | 55.53 RPS | 0% |
| 2 | 10 | 10 | 22.69ms | 23.70ms | 23.73ms | 387.97 RPS | 0% |
| 3 | 10 | 10 | 17.66ms | 18.95ms | 19.83ms | 447.23 RPS | 0% |
| 3회 평균 | 10 | 10 | 70.52ms | 71.46ms | 71.78ms | 296.91 RPS | 0% |

## 7. 개선 후 실제 결과

| run | Redis 랭킹 조회 | MySQL SELECT | 평균 | p95 | p99 | 처리량 | 오류율 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 1 | 34.17ms | 34.77ms | 34.97ms | 243.78 RPS | 0% |
| 2 | 1 | 1 | 15.03ms | 15.36ms | 15.49ms | 569.80 RPS | 0% |
| 3 | 1 | 1 | 13.37ms | 13.47ms | 13.48ms | 629.56 RPS | 0% |
| 3회 평균 | 1 | 1 | 20.86ms | 21.20ms | 21.31ms | 481.05 RPS | 0% |

동일 Cold cache 요청 10건에서 원본 Redis 랭킹 조회와 MySQL SELECT가 각각 10회에서
1회로 감소했다. 세 번의 짧은 burst 평균에서는 평균 응답시간과 tail latency가 낮아지고
처리량이 증가했지만, 첫 run의 JVM·연결 워밍업 영향이 크므로 운영 최대 처리량으로
일반화하지 않는다.

## 8. Redis 장애 fallback 결과

자동화 동시성 테스트에서 동일 Key 요청 10건은 Redis 랭킹 장애를 하나의 최종 작업으로
공유했다. 랭킹 조회 역할과 최신 ACTIVE 숙소 fallback 조회는 각각 1회였고 모든 요청이
연속 rank의 정상 fallback 결과를 받았다. 작업 종료 후 다음 동일 Key 요청이 새 작업으로
진행되며 fallback 결과는 Redis 응답 캐시에 저장되지 않았다.

실제 Redis 프로세스를 중단한 상태의 평균·p95·p99·처리량은 이번 변경에서 측정하지
않았다. 기존 Redis 장애 성능 결과는 일반 반복 부하 조건이며 이번 Cold cache burst와
직접 비교할 수 없으므로 숫자를 재사용하지 않았다.

## 9. 원본 파일

- 측정 스크립트: `performance/scripts/measure-popular-accommodation-stampede.sh`
- k6 스크립트: `performance/k6/popular-accommodation-stampede.js`
- 개선 전 원본: `performance/results/stampede/20260806-194633-before-single-flight/`
- 개선 후 원본: `performance/results/stampede/20260806-194507-single-flight-enabled/`

기존 성능 측정 파일은 수정하거나 덮어쓰지 않았다.

## 10. 한계와 후속 조건

- 측정은 애플리케이션과 k6, Docker Redis·MySQL이 같은 로컬 장비를 공유한다.
- run당 요청이 10건뿐이어서 처리량은 지속 부하 처리량이 아니라 짧은 burst의 참고값이다.
- 첫 run은 JVM JIT, 커넥션과 직렬화 준비 비용의 영향을 크게 받는다.
- MySQL 전역 `Com_select`는 측정 중 다른 클라이언트가 조회하면 함께 증가할 수 있다.
- 자동화 통합 테스트는 Hibernate prepared statement 수를 사용하므로 실제 운영 DB 모니터링
  수치와 측정 기준이 다르다.
- Redis 장애 fallback의 실제 네트워크 차단 성능 수치는 이번 실험에서 미측정이다.
- Single Flight Map은 단일 JVM 안에서만 공유된다. 애플리케이션 인스턴스가 여러 개이고
  같은 Key의 동시 Cold cache 요청이 인스턴스별 원본 조회를 유의미하게 반복한다는 측정이
  확인되면 Redis 분산 락을 검토한다. 그때는 락 TTL, 소유권 검증, 최대 대기, Redis 장애 시
  fallback과 락 서버 자체 장애를 함께 설계해야 한다.
