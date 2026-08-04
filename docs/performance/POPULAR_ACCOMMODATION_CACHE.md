# 인기 숙소 응답 캐시 성능 테스트

## 1. 테스트 목적

인기 숙소 조회 API에 적용된 Redis 응답 캐시가 다음 항목에 미치는 영향을 확인한다.

- 평균 응답시간
- p50, p95, p99 응답시간
- 처리량
- 오류율
- MySQL 조회 횟수
- Redis HIT/MISS
- 애플리케이션 CPU 및 메모리 사용량
- `limit`별 응답 크기 영향
- Redis 장애 시 DB fallback 가용성

테스트 대상 API는 다음과 같다.

```http
GET /api/v1/accommodations/popular?period=DAILY&limit={limit}
```

---

## 2. 테스트 환경

### 2.1 애플리케이션 환경

| 항목 | 값 |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.14 |
| 애플리케이션 실행 | `./gradlew bootRun` |
| 애플리케이션 주소 | `http://localhost:8080` |
| DB | MySQL 8.4 Docker |
| Redis | Redis 7 Docker |
| 부하 테스트 도구 | k6 v2.1.0 |
| 테스트 일자 | 2026-08-04 |

### 2.2 부하 조건

| 항목 | 값 |
|---|---:|
| VU | 10 |
| 측정 시간 | 30초 |
| Think time | 0초 |
| 조회 기간 | DAILY |
| 캐시 적용 전후 비교 limit | 5 |

애플리케이션과 k6는 동일한 로컬 macOS 환경에서 실행했다.

따라서 이 문서의 측정 결과는 운영 서버의 절대 처리 용량이 아니라, 동일한 로컬 환경에서 캐시 적용 전후의 상대적인 성능 차이를 확인하기 위한 값이다.

---

## 3. 테스트 데이터

ACTIVE 상태의 숙소 20개를 생성하고 Redis Sorted Set에 결정적인 점수를 저장했다.

### 3.1 DAILY 랭킹

- 첫 번째 숙소: 200점
- 두 번째 숙소: 190점
- 이후 숙소마다 10점씩 감소
- 마지막 숙소: 10점

### 3.2 WEEKLY 랭킹

- 첫 번째 숙소: 10점
- 두 번째 숙소: 20점
- 이후 숙소마다 10점씩 증가
- 마지막 숙소: 200점

### 3.3 테스트 데이터 생성

다음 스크립트로 테스트 데이터를 반복 생성할 수 있다.

```bash
./performance/scripts/setup-popular-accommodation-data.sh
```

스크립트는 다음 작업을 수행한다.

1. MySQL과 Redis 컨테이너 실행 상태 확인
2. 기존 성능 테스트 숙소 삭제
3. ACTIVE 숙소 20개 생성
4. 생성된 숙소 ID 재조회
5. DAILY·WEEKLY Redis 랭킹 생성
6. 기존 인기 숙소 응답 캐시 삭제
7. 숙소 수와 랭킹 생성 결과 검증

숙소 ID는 스크립트를 반복 실행할 때 증가할 수 있다.

스크립트가 새로 생성된 숙소 ID를 다시 조회한 후 Redis 랭킹을 구성하므로, ID 변경은 테스트 재현성에 영향을 주지 않는다.

---

## 4. 캐시 정책

인기 숙소 응답은 Spring Cache와 Redis를 사용한다.

여기서 Redis Sorted Set의 인기 랭킹 원본과 Spring Cache가 저장하는 API 응답 캐시는
서로 다른 데이터다. 랭킹 원본은 숙소 ID와 상세 조회 점수로 인기 순서를 결정하고,
응답 캐시는 그 원본과 ACTIVE 숙소 정보를 조합한 완성 DTO 목록을 재사용한다.

| 항목 | 값 |
|---|---|
| 캐시 이름 | `popularAccommodations` |
| 기본 TTL | 60초 |
| 캐시 Value | 인기 숙소 응답 DTO 목록 |
| null 저장 | 비활성화 |
| 기본 활성화 여부 | 활성화 |

캐시 키에는 다음 값이 포함된다.

- 조회 기간
- 기간 기준 날짜
- 요청 limit

캐시 키 예시는 다음과 같다.

```text
popularAccommodations::roompick:popular:accommodations:daily:2026-08-04:5
popularAccommodations::roompick:popular:accommodations:daily:2026-08-04:10
popularAccommodations::roompick:popular:accommodations:daily:2026-08-04:20
```

`limit=5`, `limit=10`, `limit=20` 요청이 각각 별도의 응답 캐시 키를 사용하는 것을 직접 확인했다.

각 키에는 해당 limit의 TOP N 전체가 저장된다. 예를 들어 `limit=10` 캐시는 1~10위
전체를 저장하고, 이후 `limit=20` 요청은 11~20위만 이어 붙이지 않고 1~20위 전체를
새 키에 저장한다. 최대 20개와 60초 TTL 범위에서 부분 캐시 조합보다 단순한 키 분리를
선택한 구조다.

숙소 상세 조회 성공 시 Redis 랭킹 원본의 점수가 증가하지만 인기 숙소 목록 조회 자체는
점수를 증가시키지 않는다. 이미 생성된 응답 캐시는 점수 증가 후에도 TTL까지 유지되고,
만료 후 다음 요청에서 최신 랭킹으로 다시 생성된다.

---

## 5. 캐시 활성화 설정

인기 숙소 응답 캐시는 기본적으로 활성화된다.

```properties
roompick.cache.popular-accommodations-enabled=true
```

성능 비교를 위해 다음 실행 인자로 응답 캐시만 비활성화할 수 있다.

```bash
./gradlew bootRun \
  --args='--roompick.cache.popular-accommodations-enabled=false'
```

캐시 비활성화 설정은 Redis 전체를 중단하지 않는다.

Redis 인기 랭킹은 그대로 사용하면서 Spring Cache 응답 캐시만 건너뛰기 때문에, 캐시 미적용 시 매 요청은 다음 경로를 수행한다.

```text
Redis 인기 랭킹 조회
→ MySQL ACTIVE 숙소 조회
→ 랭킹 순서에 따른 응답 조합
```

운영 기본값은 `true`이므로 별도의 설정이 없으면 기존 캐시 동작이 유지된다.

---

## 6. k6 테스트 스크립트

k6 스크립트 위치는 다음과 같다.

```text
performance/k6/popular-accommodation.js
```

기본 실행 예시는 다음과 같다.

```bash
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
PERIOD=DAILY \
LIMIT=5 \
CACHE_STATE=warm \
k6 run performance/k6/popular-accommodation.js
```

스크립트는 다음 항목을 검증한다.

- HTTP 상태가 200인지 확인
- 공통 응답의 `success` 값이 `true`인지 확인
- `data` 값이 배열인지 확인
- 반환 데이터 수가 요청 `limit`을 초과하지 않는지 확인

k6 결과에는 다음 통계를 출력하도록 설정했다.

- 평균
- 최소
- 중앙값
- p90
- p95
- p99
- 최대
- 처리량
- 오류율

---

## 7. 캐시 적용 전후 성능 비교

### 7.1 시나리오

#### 캐시 미적용

```text
요청
→ Redis 인기 랭킹 조회
→ MySQL ACTIVE 숙소 조회
→ 응답 조합
```

#### Warm cache

```text
요청
→ Redis 응답 캐시 HIT
→ 캐싱된 DTO 목록 반환
```

Warm cache 측정 전 동일한 URL을 한 번 호출하여 응답 캐시를 생성했다.

캐시 TTL은 60초이고 테스트 시간은 30초이므로 측정 중 캐시가 만료되지 않는다.

### 7.2 측정 결과

| 지표 | 캐시 미적용 | Warm cache | 개선 결과 |
|---|---:|---:|---:|
| 요청 수 | 47,006 | 214,981 | 약 4.57배 |
| 평균 응답시간 | 6.27ms | 1.31ms | 79.1% 감소 |
| p50 | 5.89ms | 1.17ms | 80.1% 감소 |
| p95 | 9.15ms | 2.21ms | 75.8% 감소 |
| p99 | 13.52ms | 3.84ms | 71.6% 감소 |
| 처리량 | 1,566.50 RPS | 7,166.16 RPS | 약 4.57배 증가 |
| 오류율 | 0% | 0% | 동일 |

### 7.3 분석

Warm cache에서는 Redis 응답 캐시에 저장된 DTO 목록을 바로 반환한다.

따라서 다음 작업이 생략된다.

- Redis Sorted Set 인기 랭킹 조회
- MySQL ACTIVE 숙소 조회
- 랭킹 순서에 따른 응답 재조합
- 응답 캐시 저장

평균 응답시간은 `6.27ms`에서 `1.31ms`로 약 79.1% 감소했다.

p95 응답시간은 `9.15ms`에서 `2.21ms`로 약 75.8% 감소했다.

동일한 10 VU 환경에서 처리량은 `1,566.50 RPS`에서 `7,166.16 RPS`로 약 4.57배 증가했다.

오류율은 두 시나리오 모두 0%였다.

---

## 8. Cold cache 최초 요청

### 8.1 측정 방법

Cold cache는 최초 요청만 응답 캐시 MISS가 발생하고 이후 요청은 HIT가 될 수 있다.

여러 VU로 동시에 부하를 주면 Cold와 Warm 요청이 한 테스트에 섞이므로 다음 방식으로 별도 측정했다.

1. 응답 캐시 키 삭제
2. 단일 API 요청 실행
3. 응답시간 기록
4. 위 과정을 100회 반복
5. 평균, p50, p95, p99 계산

위 순차 단일 요청 측정은 다음 스크립트로 재현할 수 있다.

```bash
./performance/scripts/measure-popular-accommodation-cold-cache.sh
```

기존 결과와 같은 조건의 명시적 실행 예시는 다음과 같다.

```bash
PERIOD=DAILY \
LIMIT=5 \
ITERATIONS=100 \
SLEEP_SECONDS=0.05 \
OUTPUT_DIR=performance/results/reproduction/cold-cache \
./performance/scripts/measure-popular-accommodation-cold-cache.sh
```

기존 공식 측정 결과의 덮어쓰기를 방지하기 위해 새 측정에서는 별도의
`OUTPUT_DIR`을 지정해야 한다. 기본 결과 파일이 이미 존재하면 스크립트는 측정을 중단한다.

스크립트는 매 요청 직전에 해당 기간·기준 날짜·limit의 인기 숙소 **응답 캐시 키만**
삭제한다. 인기 숙소 순위를 결정하는 Redis Sorted Set 랭킹 키는 삭제하지 않으며,
MySQL 테스트 데이터도 변경하지 않는다.

Cold cache 요청은 다음 경로를 수행한다.

```text
Redis 응답 캐시 MISS
→ Redis 인기 랭킹 조회
→ MySQL ACTIVE 숙소 조회
→ 응답 조합
→ Redis 응답 캐시 저장
```

### 8.2 측정 결과

| 지표 | 결과 |
|---|---:|
| 표본 수 | 100회 |
| 평균 | 13.84ms |
| p50 | 11.79ms |
| p95 | 18.75ms |
| p99 | 61.68ms |
| 최소 | 7.22ms |
| 최대 | 77.29ms |

### 8.3 해석

Cold cache는 Redis 랭킹 조회, MySQL 조회, 응답 조합, 응답 캐시 저장을 모두 수행하므로 Warm cache보다 느리게 측정됐다.

다만 Cold cache는 `curl`을 이용한 단일 요청 반복이고, Warm cache와 캐시 미적용은 k6 10 VU 부하 테스트다.

따라서 Cold cache 결과는 Warm cache 결과와 절대 수치를 직접 비교하지 않고, 최초 캐시 생성 비용을 확인하기 위한 별도 시나리오로 해석한다.

---

## 9. limit별 Warm cache 성능

### 9.1 캐시 키 분리 확인

다음 세 요청이 서로 다른 캐시 키를 생성하는 것을 확인했다.

```text
limit=5
limit=10
limit=20
```

생성된 키는 다음과 같다.

```text
popularAccommodations::roompick:popular:accommodations:daily:2026-08-04:5
popularAccommodations::roompick:popular:accommodations:daily:2026-08-04:10
popularAccommodations::roompick:popular:accommodations:daily:2026-08-04:20
```

### 9.2 측정 결과

| 지표 | limit=5 | limit=10 | limit=20 |
|---|---:|---:|---:|
| 요청 수 | 214,981 | 208,060 | 194,440 |
| 평균 | 1.31ms | 1.35ms | 1.43ms |
| p50 | 1.17ms | 1.22ms | 1.31ms |
| p95 | 2.21ms | 2.20ms | 2.26ms |
| p99 | 3.84ms | 3.61ms | 3.68ms |
| 처리량 | 7,166.16 RPS | 6,935.15 RPS | 6,481.17 RPS |
| 오류율 | 0% | 0% | 0% |

### 9.3 분석

`limit=5`에서 `limit=20`으로 반환되는 DTO 수가 4배 증가했다.

평균 응답시간은 `1.31ms`에서 `1.43ms`로 약 9.2% 증가했다.

처리량은 `7,166.16 RPS`에서 `6,481.17 RPS`로 약 9.6% 감소했다.

반면 p95는 모든 시나리오에서 약 `2.2ms`로 큰 차이가 없었고 오류율도 모두 0%였다.

Warm cache 상태에서는 DB 조회와 랭킹 조합이 생략되기 때문에, limit 증가에 따른 주요 비용은 다음과 같다.

- Redis 캐시 Value 역직렬화
- JSON 응답 직렬화
- 네트워크 응답 크기 증가

---

## 10. MySQL SELECT 쿼리 수

### 10.1 측정 방법

MySQL의 다음 전역 상태값을 요청 전후로 비교했다.

```sql
SHOW GLOBAL STATUS LIKE 'Com_select';
```

상태값을 조회하는 명령 자체도 SELECT 1회로 집계되므로, 출력된 증가량에서 측정용 SELECT 1회를 제외했다.

### 10.2 측정 결과

| 시나리오 | 측정된 증가량 | 측정용 조회 제외 | 실제 API SELECT |
|---|---:|---:|---:|
| Warm cache | 1회 | -1회 | 0회 |
| Cold cache | 2회 | -1회 | 1회 |

### 10.3 분석

Warm cache에서는 캐싱된 DTO 목록을 반환하므로 MySQL SELECT가 발생하지 않았다.

Cold cache에서는 Redis 랭킹에 포함된 숙소 ID를 이용하여 ACTIVE 숙소를 한 번에 조회하므로 MySQL SELECT가 1회 발생했다.

```text
Warm cache
→ MySQL SELECT 0회

Cold cache
→ MySQL SELECT 1회
```

이는 인기 숙소별로 개별 SELECT를 실행하지 않고 필요한 숙소를 한 번에 조회하도록 구현한 결과다.

---

## 11. Redis HIT/MISS

### 11.1 측정 방법

Redis의 다음 통계를 API 요청 전후로 비교했다.

```bash
redis-cli INFO stats
```

확인한 값은 다음과 같다.

```text
keyspace_hits
keyspace_misses
```

### 11.2 측정 결과

| 시나리오 | Redis HIT 증가량 | Redis MISS 증가량 |
|---|---:|---:|
| Warm cache | 1회 | 0회 |
| Cold cache | 1회 | 1회 |

### 11.3 분석

Warm cache 요청은 인기 숙소 응답 캐시 조회에서 HIT 1회가 발생했다.

Cold cache 요청은 다음 Redis 접근을 수행했다.

```text
응답 캐시 조회
→ MISS 1회

인기 랭킹 Sorted Set 조회
→ HIT 1회
```

따라서 Cold cache에서는 HIT 1회와 MISS 1회가 함께 증가했다.

---

## 12. CPU 및 메모리 비교

### 12.1 측정 방법

k6 부하 테스트가 실행되는 30초 동안 애플리케이션 Java 프로세스를 1초 간격으로 측정했다.

```bash
ps -p "$PID" -o %cpu= -o rss=
```

총 30개의 표본을 수집했다.

macOS의 `ps` CPU 값은 논리 코어별 사용률을 합산한다.

예를 들어 CPU `300%`는 논리 코어 약 3개를 사용한 것으로 해석한다.

### 12.2 측정 결과

| 지표 | 캐시 미적용 | Warm cache |
|---|---:|---:|
| 표본 수 | 30회 | 30회 |
| 평균 CPU | 188.92% | 315.06% |
| 최대 CPU | 310.80% | 392.40% |
| 평균 메모리 RSS | 214.60MB | 193.18MB |
| 최대 메모리 RSS | 284.28MB | 212.33MB |
| 처리량 | 1,566.50 RPS | 7,166.16 RPS |

### 12.3 CPU 분석

Warm cache의 CPU 절대값이 캐시 미적용보다 높게 측정됐다.

이는 캐시 적용으로 요청 한 건의 비용은 감소했지만, 동일한 10 VU 조건에서 약 4.57배 많은 요청을 처리했기 때문이다.

처리량 1,000 RPS당 CPU 사용량을 계산하면 다음과 같다.

| 시나리오 | 1,000 RPS당 CPU |
|---|---:|
| 캐시 미적용 | 약 120.60% |
| Warm cache | 약 43.96% |

Warm cache는 처리량을 기준으로 약 2.74배 더 높은 CPU 효율을 보였다.

1,000 RPS당 CPU 사용량은 캐시 미적용 대비 약 63.5% 감소했다.

### 12.4 메모리 분석

Warm cache 적용 후 평균 RSS는 `214.60MB`에서 `193.18MB`로 약 10.0% 감소했다.

최대 RSS는 `284.28MB`에서 `212.33MB`로 약 25.3% 감소했다.

캐시 미적용에서는 매 요청마다 다음 작업이 반복된다.

- Redis 랭킹 결과 생성
- 숙소 ID 목록 생성
- MySQL 조회 결과 생성
- 랭킹 순서 재조합
- 응답 DTO 목록 생성

Warm cache에서는 이미 생성된 DTO 목록을 Redis에서 조회하므로 요청별 임시 객체 생성량이 줄어든 것으로 해석할 수 있다.

단, JVM GC 시점과 로컬 환경 상태에 따라 메모리 수치는 달라질 수 있다.

---

## 13. Redis 장애 시 DB fallback

### 13.1 측정 방법

Redis 컨테이너를 중단한 상태에서 동일한 API를 호출했다.

```bash
docker stop roompick-redis
```

단일 요청 확인 후 동일한 10 VU, 30초 조건으로 k6 부하 테스트를 실행했다.

테스트 완료 후 Redis 컨테이너를 다시 시작하고 `PING`, 랭킹 데이터 수, API HTTP 상태를 확인했다.

### 13.2 단일 요청 확인

| 항목 | 결과 |
|---|---:|
| HTTP 상태 | 200 |
| 응답시간 | 170.163ms |

Redis가 중단된 상태에서도 HTTP 200 응답이 반환되어 DB fallback이 정상 동작하는 것을 확인했다.

### 13.3 부하 테스트 결과

| 지표 | Redis 장애 |
|---|---:|
| 요청 수 | 4,517 |
| 평균 | 67.22ms |
| p50 | 39.47ms |
| p90 | 98.19ms |
| p95 | 154.30ms |
| p99 | 821.17ms |
| 최대 | 1.02초 |
| 처리량 | 148.10 RPS |
| 오류율 | 0% |

### 13.4 Warm cache 대비 비교

| 지표 | Warm cache | Redis 장애 |
|---|---:|---:|
| 평균 | 1.31ms | 67.22ms |
| p95 | 2.21ms | 154.30ms |
| p99 | 3.84ms | 821.17ms |
| 처리량 | 7,166.16 RPS | 148.10 RPS |
| 오류율 | 0% | 0% |

Redis 장애 시 평균 응답시간은 Warm cache 대비 약 51.3배 증가했다.

처리량은 약 97.9% 감소했다.

오류율은 0%로 유지되어 서비스 가용성은 보장됐지만, Redis 연결 실패 처리와 DB fallback으로 인해 성능 저하가 크게 나타났다.

### 13.5 장애 시 요청 흐름

```text
Redis 응답 캐시 조회 실패
→ 캐시 오류 무시
→ Redis 인기 랭킹 조회 실패
→ Facade에서 예외 처리
→ MySQL 기반 fallback 조회
→ HTTP 200 응답
```

현재 구조는 Redis 장애 시 요청 실패를 방지하지만, 장애가 장시간 지속되면 DB 부하와 응답 지연이 증가할 수 있다.

---

## 14. 전체 결과 요약

| 시나리오 | 평균 | p95 | p99 | 처리량 | 오류율 |
|---|---:|---:|---:|---:|---:|
| 캐시 미적용 | 6.27ms | 9.15ms | 13.52ms | 1,566.50 RPS | 0% |
| Warm cache, limit=5 | 1.31ms | 2.21ms | 3.84ms | 7,166.16 RPS | 0% |
| Warm cache, limit=10 | 1.35ms | 2.20ms | 3.61ms | 6,935.15 RPS | 0% |
| Warm cache, limit=20 | 1.43ms | 2.26ms | 3.68ms | 6,481.17 RPS | 0% |
| Redis 장애 | 67.22ms | 154.30ms | 821.17ms | 148.10 RPS | 0% |

Cold cache는 단일 요청을 100회 반복한 별도 측정 방식이므로 위 부하 테스트 표에서 제외했다.

---

## 15. 결론

### 15.1 응답시간

Redis 응답 캐시 적용 후 평균 응답시간은 `6.27ms`에서 `1.31ms`로 약 79.1% 감소했다.

p95는 약 75.8%, p99는 약 71.6% 감소했다.

### 15.2 처리량

동일한 10 VU 조건에서 처리량은 `1,566.50 RPS`에서 `7,166.16 RPS`로 약 4.57배 증가했다.

### 15.3 DB 부하

Warm cache 요청에서는 MySQL SELECT가 발생하지 않았다.

Cold cache 요청에서는 필요한 숙소를 한 번에 조회하는 SELECT 1회만 발생했다.

### 15.4 Redis 접근

Warm cache에서는 응답 캐시 HIT 1회만 발생했다.

Cold cache에서는 응답 캐시 MISS 1회와 인기 랭킹 HIT 1회가 발생했다.

### 15.5 CPU 효율

Warm cache는 더 많은 요청을 처리해 CPU 절대 사용률은 높았지만, 1,000 RPS당 CPU 사용량은 약 63.5% 감소했다.

### 15.6 메모리

Warm cache는 캐시 미적용 대비 평균 RSS가 약 10.0%, 최대 RSS가 약 25.3% 감소했다.

### 15.7 Redis 장애

Redis 장애 상황에서도 오류율 0%와 HTTP 200 응답을 유지해 fallback 가용성을 확인했다.

다만 평균 응답시간이 약 51.3배 증가하고 처리량이 약 97.9% 감소했으므로, 장애 감지와 빠른 Redis 복구가 필요하다.

---

## 16. 운영 시 권장 사항

### 16.1 Redis 장애 모니터링

다음 지표에 대한 모니터링과 알림이 필요하다.

- Redis 연결 실패 횟수
- 캐시 오류 처리 횟수
- DB fallback 실행 횟수
- 인기 숙소 API p95, p99
- MySQL 커넥션 풀 사용량
- Redis 메모리 사용량
- 응답 캐시 HIT 비율

### 16.2 fallback 보호

Redis 장애가 장시간 지속되면 모든 인기 숙소 요청이 DB로 전달될 수 있다.

다음 보호 방안을 추가로 검토할 수 있다.

- Redis 연결 timeout 단축
- Circuit Breaker
- fallback 결과의 애플리케이션 로컬 캐시
- API Rate Limit
- 장애 시 반환 limit 축소
- Redis 고가용성 구성

### 16.3 TTL 조정

현재 인기 숙소 응답 캐시 TTL은 60초다.

TTL을 늘리면 캐시 HIT 비율은 높아질 수 있지만 인기 순위 반영이 늦어질 수 있다.

운영 트래픽과 순위 변경 빈도를 확인하면서 다음 항목을 함께 고려해야 한다.

- 인기 순위 실시간성
- Redis 메모리 사용량
- DB 조회 감소량
- 캐시 무효화 빈도

---

## 17. 테스트 재현 순서

### 17.1 인프라 실행

```bash
docker compose up -d
```

### 17.2 테스트 데이터 생성

```bash
./performance/scripts/setup-popular-accommodation-data.sh
```

### 17.3 Warm cache 서버 실행

```bash
./gradlew bootRun
```

### 17.4 Warm cache 생성

```bash
curl -s \
  "http://localhost:8080/api/v1/accommodations/popular?period=DAILY&limit=5" \
  > /dev/null
```

### 17.5 Warm cache 부하 테스트

```bash
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
PERIOD=DAILY \
LIMIT=5 \
CACHE_STATE=warm \
k6 run performance/k6/popular-accommodation.js
```

### 17.6 캐시 미적용 서버 실행

기존 서버를 종료한 후 다음 명령으로 실행한다.

```bash
./gradlew bootRun \
  --args='--roompick.cache.popular-accommodations-enabled=false'
```

### 17.7 캐시 미적용 부하 테스트

```bash
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
PERIOD=DAILY \
LIMIT=5 \
CACHE_STATE=disabled \
k6 run performance/k6/popular-accommodation.js
```

### 17.8 Redis 장애 테스트

기본 캐시 활성화 상태의 서버를 실행한 후 Redis를 중단한다.

```bash
docker stop roompick-redis
```

부하 테스트를 실행한다.

```bash
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
PERIOD=DAILY \
LIMIT=5 \
CACHE_STATE=redis-outage \
k6 run performance/k6/popular-accommodation.js
```

테스트 후 Redis를 복구한다.

```bash
docker start roompick-redis
docker exec roompick-redis redis-cli PING
```

---

## 18. 결과 파일

원본 측정 결과는 다음 경로에 저장했다.

```text
performance/results/cache-disabled-daily-limit5.txt
performance/results/cold-cache-daily-limit5-summary.txt
performance/results/cold-cache-daily-limit5.csv
performance/results/redis-outage-daily-limit5.txt
performance/results/resources-cache-disabled-daily-limit5.csv
performance/results/resources-warm-daily-limit5.csv
performance/results/warm-daily-limit5.txt
performance/results/warm-daily-limit10.txt
performance/results/warm-daily-limit20.txt
```

---

## 19. 테스트 한계

이번 테스트에는 다음 한계가 있다.

- 애플리케이션과 k6가 동일한 로컬 장비에서 실행됐다.
- 로컬 CPU, 메모리, 백그라운드 프로세스 상태의 영향을 받을 수 있다.
- 단일 애플리케이션 인스턴스에서 측정했다.
- 실제 운영 네트워크 지연이 포함되지 않았다.
- MySQL과 Redis가 모두 로컬 Docker에서 실행됐다.
- 10 VU, 30초의 비교적 짧은 부하 테스트다.
- JVM GC와 JIT 워밍업 상태가 각 실행마다 완전히 동일하지 않을 수 있다.
- Cold cache는 k6 부하 테스트가 아닌 단일 요청 100회 반복 방식이다.
- Redis 장애 테스트에는 연결 실패 및 timeout 처리 비용이 포함된다.

따라서 결과는 운영 서버의 최대 처리량을 보장하는 값이 아니라, 같은 환경에서 캐시 적용 전후와 장애 상황의 상대적 차이를 보여주는 자료로 사용한다.
