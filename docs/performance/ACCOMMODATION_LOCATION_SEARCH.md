# Elasticsearch 기반 위치 검색 성능 테스트

## 1. 테스트 목적

RoomPick 숙소 검색에서 MySQL 기반 위치 검색과 Elasticsearch 기반 위치 검색의 성능을 동일한 조건에서 비교한다.

이번 테스트에서는 다음 항목을 확인한다.

* 평균 응답시간
* p50 응답시간
* p95 응답시간
* p99 응답시간
* 처리량
* 오류율
* MySQL SELECT 횟수
* 위치 반경 검색 정확성
* 거리순 정렬
* 숙소명·주소 keyword 검색
* Elasticsearch 도입에 따른 검색 DB 부하 변화

테스트 대상 API는 다음과 같다.

```http
GET /api/v1/accommodations/search
```

기본 요청 예시는 다음과 같다.

```http
GET /api/v1/accommodations/search
    ?latitude=37.5665
    &longitude=126.9780
    &radiusKm=5
    &limit=20
```

keyword를 포함한 요청은 다음과 같다.

```http
GET /api/v1/accommodations/search
    ?keyword=룸픽
    &latitude=37.5665
    &longitude=126.9780
    &radiusKm=5
    &limit=20
```

---

## 2. 도입 배경

위치 기반 숙소 검색은 사용자가 전달한 특정 위치를 중심으로 일정 반경 안의 숙소를 조회하고 가까운 순서로 반환해야 한다.

검색 중심 좌표는 반드시 사용자의 현재 GPS 위치일 필요는 없다.

예를 들어 다음과 같은 다양한 입력을 동일한 검색 엔진에서 처리할 수 있다.

```text
사용자 현재 위치
→ latitude / longitude
→ 주변 숙소 검색

서울역 장소 검색
→ 서울역 latitude / longitude
→ 주변 숙소 검색

지도에서 특정 지점 선택
→ latitude / longitude
→ 주변 숙소 검색
```

이번 작업에서는 장소명 자체를 좌표로 변환하는 Geocoding 기능은 범위에서 제외하고, **좌표가 주어졌을 때 주변 숙소를 검색하는 검색 엔진 영역**에 집중한다.

---

## 3. 기존 MySQL 검색 구조

Elasticsearch 적용 전에는 MySQL의 `ST_Distance_Sphere`를 사용하여 위치 검색을 구현했다.

검색 흐름은 다음과 같다.

```text
Controller
→ AccommodationFacade
→ AccommodationLocationSearchService
→ AccommodationLocationSearchRepository
→ MySQL
```

MySQL에서는 다음 조건을 사용한다.

```text
ACTIVE 숙소
+
latitude / longitude 존재
+
선택적 숙소명·주소 keyword 검색
+
ST_Distance_Sphere 거리 계산
+
radiusKm 반경 필터링
+
거리 오름차순 정렬
+
LIMIT
```

거리 계산에서는 MySQL `POINT`의 X축과 Y축 의미에 맞춰 다음 순서를 사용한다.

```text
POINT(longitude, latitude)
```

위치 검색 결과에 필요한 필드만 Native Query Projection으로 조회하여 `Accommodation` Entity 전체 로딩은 피했다.

---

## 4. Elasticsearch 검색 구조

Elasticsearch 적용 후 공개 검색 API의 흐름은 다음과 같다.

```text
Controller
→ AccommodationFacade
→ AccommodationElasticsearchLocationSearchService
→ AccommodationElasticsearchLocationSearchRepository
→ Elasticsearch
```

Elasticsearch 검색 인덱스는 MySQL Entity와 분리된 검색 전용 Read Model을 사용한다.

```text
MySQL
Accommodation
     ↓
AccommodationSearchDocument
     ↓
Elasticsearch
roompick-accommodations-v1
```

### 4.1 검색 Document

검색 Document는 다음 필드를 가진다.

| 필드                | Elasticsearch 타입 | 용도            |
| ----------------- | ---------------- | ------------- |
| `accommodationId` | long             | MySQL 숙소 ID   |
| `name`            | text             | 숙소명 검색        |
| `address`         | text             | 주소 검색         |
| `status`          | keyword          | ACTIVE 필터     |
| `location`        | geo_point        | 반경 검색 및 거리 정렬 |

실제 Elasticsearch 매핑에서 다음 내용을 확인했다.

```json
{
  "accommodationId": {
    "type": "long"
  },
  "address": {
    "type": "text"
  },
  "location": {
    "type": "geo_point"
  },
  "name": {
    "type": "text"
  },
  "status": {
    "type": "keyword"
  }
}
```

### 4.2 검색 조건

Elasticsearch 검색은 다음 조건을 조합한다.

```text
bool
├─ filter
│  └─ status = ACTIVE
│
├─ filter
│  └─ geo_distance
│     ├─ center = latitude / longitude
│     └─ radius = radiusKm
│
└─ must
   └─ multi_match
      ├─ name
      └─ address
      ※ keyword가 있을 때만 적용

sort
└─ geo_distance ASC
```

전체 결과 수는 API에서 필요하지 않기 때문에 `track_total_hits` 계산은 사용하지 않는다.

Elasticsearch에서 요청한 `limit`만큼만 가져오고, 애플리케이션에서 전체 검색 결과를 로딩한 뒤 잘라내는 방식은 사용하지 않는다.

---

## 5. 위치 데이터 모델

숙소에 다음 좌표 필드를 추가했다.

```text
latitude  DECIMAL(9, 6)
longitude DECIMAL(10, 6)
```

기존 숙소에는 좌표가 존재하지 않을 수 있으므로 nullable로 추가했다.

위치 검색에서는 다음 조건을 모두 만족하는 숙소만 대상으로 한다.

```text
latitude IS NOT NULL
AND
longitude IS NOT NULL
```

애플리케이션에서는 좌표 입력 시 다음 범위를 검증한다.

| 항목    |        허용 범위 |
| ----- | -----------: |
| 위도    |     -90 ~ 90 |
| 경도    |   -180 ~ 180 |
| 검색 반경 | 0 초과 ~ 100km |
| limit |      1 ~ 100 |

잘못된 값은 MySQL 또는 Elasticsearch에 요청하기 전에 Service에서 차단하여 불필요한 검색 리소스 사용을 방지한다.

---

## 6. Elasticsearch 재색인 구조

MySQL을 숙소 데이터의 Source of Truth로 유지하고 Elasticsearch는 검색용 Read Model로 사용한다.

전체 재색인 흐름은 다음과 같다.

```text
MySQL
↓
검색 인덱스용 Projection
↓
1,000건 Keyset 조회
↓
Elasticsearch Bulk Index
↓
마지막 accommodationId 저장
↓
다음 1,000건 조회
↓
반복
↓
전체 완료 후 refresh 1회
```

### 6.1 Keyset Pagination

50,000건을 재색인할 때 OFFSET 기반 조회 대신 숙소 ID 기반 Keyset Pagination을 사용한다.

```sql
WHERE accommodation_id > :lastAccommodationId
ORDER BY accommodation_id ASC
LIMIT 1000
```

다음과 같은 큰 OFFSET 조회는 사용하지 않는다.

```sql
LIMIT 1000 OFFSET 40000
```

각 배치 조회는 read-only 트랜잭션으로 짧게 종료하고, 50,000건 전체 재색인을 하나의 긴 DB 트랜잭션으로 감싸지 않는다.

### 6.2 Projection 조회

재색인에 필요한 다음 데이터만 MySQL에서 조회한다.

```text
accommodationId
name
address
status
latitude
longitude
```

따라서 재색인을 위해 `Accommodation` Entity 전체를 반복 로딩하지 않는다.

### 6.3 Bulk 저장

숙소 한 건마다 Elasticsearch 요청을 보내지 않고 1,000건 단위 Bulk 작업으로 저장한다.

또한 매 배치마다 refresh하지 않고 전체 Bulk 저장이 완료된 후 한 번만 refresh한다.

### 6.4 전체 인덱스 재생성

전체 재색인 시 기존 인덱스를 삭제하고 현재 Document 매핑으로 다시 생성한다.

이를 통해 다음과 같은 오래된 검색 문서도 함께 제거한다.

* MySQL에서 삭제된 숙소
* 좌표가 제거된 숙소
* 이전 인덱스에만 남아 있는 문서

---

## 7. 로컬 재색인 실행 정책

전체 재색인을 공개 API로 노출하지 않았다.

대신 local 프로필에서 특정 설정이 활성화된 경우에만 실행되는 `ApplicationRunner`를 사용한다.

기본 설정은 다음과 같다.

```yaml
roompick:
  search:
    reindex-enabled: ${ACCOMMODATION_SEARCH_REINDEX_ENABLED:false}
```

기본값은 `false`이므로 일반 서버 실행에서는 전체 재색인이 발생하지 않는다.

로컬에서 재색인이 필요한 경우에만 다음과 같이 실행한다.

```bash
ACCOMMODATION_SEARCH_REINDEX_ENABLED=true \
./gradlew bootRun
```

실제 50,000건 재색인 결과는 다음과 같았다.

```text
로컬 Elasticsearch 숙소 검색 인덱스 재색인을 완료했습니다.
indexedCount=50000
```

Elasticsearch `_count` API에서도 50,000개의 Document가 저장된 것을 확인했다.

```json
{
  "count": 50000
}
```

---

## 8. 테스트 환경

### 8.1 애플리케이션 환경

| 항목                | 값                            |
| ----------------- | ---------------------------- |
| Java              | 17                           |
| Spring Boot       | 3.5.14                       |
| 애플리케이션 실행         | `./gradlew bootRun`          |
| 애플리케이션 주소         | `http://localhost:8080`      |
| MySQL             | MySQL 8.4 Docker             |
| Elasticsearch     | Elasticsearch 8.18.1 Docker  |
| Elasticsearch 인덱스 | `roompick-accommodations-v1` |
| 부하 테스트 도구         | k6                           |
| 테스트 일자            | 2026-08-10                   |

애플리케이션, k6, MySQL, Elasticsearch는 동일한 로컬 macOS 환경에서 실행했다.

따라서 이번 결과는 운영 서버의 최대 처리량이나 절대적인 Elasticsearch 성능을 의미하지 않는다.

**동일한 로컬 환경과 동일 데이터에서 MySQL 검색과 Elasticsearch 검색의 상대적인 차이를 확인하기 위한 결과**로 해석한다.

---

## 9. 성능 테스트 데이터

위치 검색 성능 비교를 위해 ACTIVE 숙소 50,000개를 생성했다.

### 9.1 데이터 구성

| 항목                 |          값 |
| ------------------ | ---------: |
| 전체 숙소              |    50,000건 |
| `룸픽` keyword 포함 숙소 |    10,000건 |
| keyword 비율         |        20% |
| 최소 위도              |  37.450000 |
| 최대 위도              |  37.649200 |
| 최소 경도              | 126.800000 |
| 최대 경도              | 127.198000 |

좌표는 서울 권역을 가정한 결정적인 격자 형태로 생성했다.

```text
위도 250개
×
경도 200개
=
서로 다른 좌표 50,000개
```

숙소 5건마다 한 건의 이름에 `룸픽`을 포함시켰다.

예시는 다음과 같다.

```text
위치 성능 숙소 00001
위치 성능 숙소 00002
위치 성능 숙소 00003
위치 성능 숙소 00004
룸픽 위치 성능 숙소 00005
```

따라서 위치만 검색하는 시나리오와 keyword를 함께 사용하는 시나리오를 같은 데이터에서 비교할 수 있다.

### 9.2 데이터 생성 스크립트

```text
performance/scripts/setup-accommodation-location-search-data.sh
```

실행 방법은 다음과 같다.

```bash
bash performance/scripts/setup-accommodation-location-search-data.sh
```

스크립트는 다음 작업을 수행한다.

1. MySQL 컨테이너 상태 확인
2. 기존 위치 검색 성능 테스트 데이터만 삭제
3. ACTIVE 숙소 50,000건 생성
4. 좌표 결정적 생성
5. `룸픽` keyword 데이터 10,000건 생성
6. MySQL `ANALYZE TABLE` 실행
7. 데이터 수와 좌표 범위 확인

기존 일반 숙소 데이터와 이전 인기 숙소 성능 테스트 데이터는 삭제하지 않는다.

---

## 10. 부하 테스트 조건

MySQL과 Elasticsearch 모두 동일한 조건을 사용했다.

| 항목         |          값 |
| ---------- | ---------: |
| 검색 중심      | 서울시청 기준 좌표 |
| 위도         |    37.5665 |
| 경도         |   126.9780 |
| 검색 반경      |        5km |
| limit      |         20 |
| VU         |         10 |
| 측정 시간      |        30초 |
| Think time |         0초 |
| 반복 측정      |         3회 |
| 오류율 기준     |      1% 미만 |

각 검색 엔진에서 본 측정 전에 1 VU, 5초 워밍업을 수행했다.

JIT, Connection Pool, 검색 엔진 최초 요청 등의 초기 비용이 본 측정 결과에 과도하게 포함되는 것을 줄이기 위한 목적이다.

---

## 11. 테스트 시나리오

두 가지 시나리오를 각각 MySQL과 Elasticsearch에서 측정했다.

### 11.1 geo-only

keyword 없이 위치 조건만 사용한다.

```http
GET /api/v1/accommodations/search
    ?latitude=37.5665
    &longitude=126.9780
    &radiusKm=5
    &limit=20
```

### 11.2 geo-keyword

위치 조건과 `룸픽` keyword를 함께 사용한다.

```http
GET /api/v1/accommodations/search
    ?keyword=룸픽
    &latitude=37.5665
    &longitude=126.9780
    &radiusKm=5
    &limit=20
```

---

## 12. k6 측정 스크립트

공통 k6 스크립트는 다음 파일을 사용한다.

```text
performance/k6/accommodation-location-search.js
```

동일 스크립트에 `SEARCH_ENGINE` 태그만 변경하여 MySQL과 Elasticsearch 결과를 구분한다.

검색 엔진 이름은 결과 분류용 태그이며 API 요청 파라미터로 전달하지 않는다.

스크립트는 다음 항목을 검증한다.

* HTTP 상태가 200인지 확인
* `success`가 `true`인지 확인
* `data`가 배열인지 확인
* 반환 데이터가 `limit`을 초과하지 않는지 확인
* 반환 결과가 `distanceKm` 오름차순인지 확인

출력 통계에는 다음 값을 포함한다.

* 평균
* 최소
* p50
* p90
* p95
* p99
* 최대
* 처리량
* 오류율

---

## 13. MySQL geo-only 기준선

### 13.1 개별 실행 결과

| Run | 요청 수 |      avg |      p50 |        p95 |        p99 |   RPS | 오류율 |
| --- | ---: | -------: | -------: | ---------: | ---------: | ----: | --: |
| 1   |  513 | 596.35ms | 537.63ms | 1,123.03ms | 1,482.15ms | 16.61 |  0% |
| 2   |  453 | 665.39ms | 544.48ms | 1,640.67ms | 2,037.55ms | 14.97 |  0% |
| 3   |  656 | 459.13ms | 444.21ms |   635.46ms |   796.57ms | 21.69 |  0% |

### 13.2 3회 평균

| 지표      |         결과 |
| ------- | ---------: |
| 평균 응답시간 |   573.62ms |
| p50     |   508.77ms |
| p95     | 1,133.05ms |
| p99     | 1,438.76ms |
| 처리량     |  17.75 RPS |
| 오류율     |         0% |

MySQL SELECT 증가량과 요청 수가 각 실행에서 동일했다.

```text
Run 1: 513 requests / 513 SELECT
Run 2: 453 requests / 453 SELECT
Run 3: 656 requests / 656 SELECT
```

따라서 MySQL 기반 위치 검색에서는 API 요청당 숙소 검색 SELECT가 1회 발생했다.

---

## 14. MySQL geo-keyword 기준선

### 14.1 개별 실행 결과

| Run |  요청 수 |      avg |      p50 |      p95 |      p99 |   RPS | 오류율 |
| --- | ----: | -------: | -------: | -------: | -------: | ----: | --: |
| 1   | 1,093 | 274.95ms | 257.98ms | 392.98ms | 502.19ms | 36.22 |  0% |
| 2   |   950 | 316.41ms | 303.80ms | 420.69ms | 502.46ms | 31.48 |  0% |
| 3   |   902 | 333.48ms | 316.24ms | 482.48ms | 687.55ms | 29.90 |  0% |

### 14.2 3회 평균

| 지표      |        결과 |
| ------- | --------: |
| 평균 응답시간 |  308.28ms |
| p50     |  292.67ms |
| p95     |  432.05ms |
| p99     |  564.07ms |
| 처리량     | 32.53 RPS |
| 오류율     |        0% |

50,000건 중 `룸픽` keyword에 매칭되는 숙소는 10,000건이다.

따라서 keyword가 없는 geo-only보다 검색 후보가 줄어들어 이번 데이터에서는 geo-keyword가 더 빠르게 측정됐다.

이는 keyword 조건이 항상 위치 검색을 빠르게 만든다는 일반적인 의미가 아니라, **이번 테스트 데이터 분포와 MySQL 쿼리 구조에서 관측된 결과**다.

---

## 15. Elasticsearch geo-only 결과

### 15.1 개별 실행 결과

| Run |   요청 수 |    avg |    p50 |     p95 |     p99 |      RPS | 오류율 |
| --- | -----: | -----: | -----: | ------: | ------: | -------: | --: |
| 1   | 45,273 | 6.42ms | 5.31ms | 12.87ms | 24.59ms | 1,508.94 |  0% |
| 2   | 51,166 | 5.67ms | 5.19ms |  9.10ms | 15.47ms | 1,705.23 |  0% |
| 3   | 46,404 | 6.26ms | 5.55ms | 10.37ms | 21.85ms | 1,546.56 |  0% |

### 15.2 3회 평균

| 지표                    |           결과 |
| --------------------- | -----------: |
| 평균 응답시간               |       6.12ms |
| p50                   |       5.35ms |
| p95                   |      10.78ms |
| p99                   |      20.64ms |
| 처리량                   | 1,586.91 RPS |
| 오류율                   |           0% |
| 검색 중 측정된 MySQL SELECT |           0회 |

---

## 16. Elasticsearch geo-keyword 결과

### 16.1 개별 실행 결과

| Run |   요청 수 |    avg |    p50 |    p95 |     p99 |      RPS | 오류율 |
| --- | -----: | -----: | -----: | -----: | ------: | -------: | --: |
| 1   | 56,517 | 5.11ms | 4.43ms | 8.78ms | 19.70ms | 1,883.74 |  0% |
| 2   | 56,026 | 5.16ms | 4.67ms | 8.18ms | 14.88ms | 1,867.34 |  0% |
| 3   | 52,545 | 5.51ms | 4.96ms | 8.88ms | 16.41ms | 1,751.35 |  0% |

### 16.2 3회 평균

| 지표      |           결과 |
| ------- | -----------: |
| 평균 응답시간 |       5.26ms |
| p50     |       4.69ms |
| p95     |       8.61ms |
| p99     |      17.00ms |
| 처리량     | 1,834.14 RPS |
| 오류율     |           0% |

MySQL 글로벌 SELECT 카운터는 다음과 같이 측정됐다.

```text
Run 1: 0
Run 2: 0
Run 3: 2
```

현재 숙소 검색 API 경로는 Elasticsearch Repository를 사용하므로 위치 검색 자체를 위해 MySQL을 조회하지 않는다.

다만 `Com_select`는 애플리케이션 전체 MySQL 서버의 글로벌 누적 카운터이므로 Run 3에서 관측된 2회는 측정 구간에 발생한 다른 조회 또는 백그라운드 요청이 포함된 값으로 해석한다.

따라서 원본 수치를 임의로 0으로 수정하지 않고 그대로 보존한다.

---

## 17. MySQL vs Elasticsearch 최종 비교

아래 값은 각 조건을 3회 측정한 후 각 통계값을 단순 산술평균한 결과다.

### 17.1 geo-only

| 지표                 |      MySQL | Elasticsearch |       개선 결과 |
| ------------------ | ---------: | ------------: | ----------: |
| 평균 응답시간            |   573.62ms |        6.12ms | 약 98.93% 감소 |
| p50                |   508.77ms |        5.35ms | 약 98.95% 감소 |
| p95                | 1,133.05ms |       10.78ms | 약 99.05% 감소 |
| p99                | 1,438.76ms |       20.64ms | 약 98.57% 감소 |
| 처리량                |  17.75 RPS |  1,586.91 RPS |    약 89.38배 |
| 오류율                |         0% |            0% |          동일 |
| 숙소 검색 MySQL SELECT |     요청당 1회 |            0회 | 검색 DB 부하 제거 |

### 17.2 geo-keyword

| 지표      |     MySQL | Elasticsearch |       개선 결과 |
| ------- | --------: | ------------: | ----------: |
| 평균 응답시간 |  308.28ms |        5.26ms | 약 98.29% 감소 |
| p50     |  292.67ms |        4.69ms | 약 98.40% 감소 |
| p95     |  432.05ms |        8.61ms | 약 98.01% 감소 |
| p99     |  564.07ms |       17.00ms | 약 96.99% 감소 |
| 처리량     | 32.53 RPS |  1,834.14 RPS |    약 56.38배 |
| 오류율     |        0% |            0% |          동일 |

---

## 18. 결과 분석

### 18.1 평균 응답시간

geo-only 검색의 평균 응답시간은 다음과 같이 감소했다.

```text
573.62ms
→
6.12ms
```

약 98.93% 감소했다.

geo-keyword 검색은 다음과 같다.

```text
308.28ms
→
5.26ms
```

약 98.29% 감소했다.

### 18.2 Tail Latency

평균뿐 아니라 p95와 p99에서도 큰 차이가 나타났다.

geo-only p95:

```text
1,133.05ms
→
10.78ms
```

약 99.05% 감소했다.

geo-only p99:

```text
1,438.76ms
→
20.64ms
```

약 98.57% 감소했다.

따라서 이번 테스트에서는 평균 요청뿐 아니라 느린 상위 요청의 지연시간도 크게 개선됐다.

### 18.3 처리량

geo-only 처리량은:

```text
17.75 RPS
→
1,586.91 RPS
```

약 89.38배 증가했다.

geo-keyword 처리량은:

```text
32.53 RPS
→
1,834.14 RPS
```

약 56.38배 증가했다.

동일한 10 VU 환경에서 Elasticsearch 경로가 훨씬 많은 요청을 처리했다.

### 18.4 MySQL 부하

기존 MySQL 검색은 요청마다 위치 검색 SELECT 1회를 수행했다.

```text
API 요청
→ MySQL 위치 검색 SELECT
→ ST_Distance_Sphere
→ 반경 필터
→ 거리 정렬
```

Elasticsearch 전환 후 검색 경로는 다음과 같다.

```text
API 요청
→ Elasticsearch
→ geo_distance
→ 거리 정렬
```

따라서 숙소 검색 트래픽을 MySQL의 트랜잭션 데이터 처리 경로와 분리할 수 있다.

### 18.5 keyword 시나리오

MySQL에서도 geo-keyword가 geo-only보다 빠르게 측정됐다.

50,000건 중 keyword에 매칭되는 데이터가 10,000건으로 제한되었기 때문에 이번 테스트 데이터에서는 거리 계산과 정렬 후보가 감소한 영향이 있는 것으로 해석한다.

Elasticsearch에서도 geo-keyword가 geo-only보다 소폭 높은 처리량을 기록했지만, 두 시나리오 모두 평균 5~6ms 수준으로 측정됐다.

---

## 19. 검색 결과 정확성 확인

성능뿐 아니라 실제 검색 결과도 확인했다.

### 19.1 거리 오름차순

서울시청 좌표를 기준으로 다음 결과가 반환됐다.

```text
0.033km
0.055km
0.122km
0.144km
0.179km
```

가까운 숙소부터 정상 정렬됐다.

### 19.2 keyword

`keyword=룸픽` 요청 시 다음과 같이 `룸픽`에 매칭되는 숙소만 반환됐다.

```text
룸픽 위치 성능 숙소 22395
룸픽 위치 성능 숙소 22645
룸픽 위치 성능 숙소 22145
...
```

### 19.3 MySQL과 Elasticsearch 거리값 차이

동일 숙소의 거리값은 MySQL과 Elasticsearch에서 매우 미세한 차이가 존재했다.

예:

```text
MySQL
0.0333584046 km

Elasticsearch
0.0333565325 km
```

각 검색 엔진의 거리 계산 방식에 따른 미세한 차이가 존재하지만 이번 반경 검색과 거리 정렬 요구사항에는 영향을 주지 않았다.

---

## 20. 테스트 검증

### 20.1 MySQL Repository 통합 테스트

실제 MySQL 8.4 Testcontainer에서 다음 항목을 검증했다.

* `ST_Distance_Sphere` 실행
* 반경 검색
* ACTIVE 필터
* 좌표 없는 숙소 제외
* 거리 오름차순
* keyword 검색
* limit

### 20.2 Elasticsearch Service 단위 테스트

다음 항목을 검증했다.

* latitude 검증
* longitude 검증
* radius 검증
* limit 검증
* keyword trim
* 빈 keyword 정규화
* Elasticsearch 검색 결과 → API DTO 변환

### 20.3 재색인 Service 단위 테스트

다음 항목을 검증했다.

* 기존 인덱스 삭제
* 신규 인덱스 생성
* Keyset Pagination
* 다음 배치 ID 전달
* Bulk 저장
* 마지막 refresh
* 인덱스 삭제 실패
* 인덱스 생성 실패

### 20.4 실제 MySQL + Elasticsearch 통합 테스트

MySQL 8.4와 Elasticsearch 8.18.1을 Testcontainers로 동시에 실행했다.

검증 흐름은 다음과 같다.

```text
MySQL 숙소 저장
→ 전체 재색인
→ Elasticsearch Document 저장
→ geo_distance 검색
→ keyword 검색
→ 거리순 정렬
→ limit
→ Response DTO
```

통합 테스트는 정상 통과했다.

테스트 종료 과정에서 Testcontainer MySQL 종료 후 Hibernate `create-drop` 종료 DDL이 연결을 요청하면서 Hikari 경고가 출력됐지만 테스트 결과는 `BUILD SUCCESSFUL`이었다.

---

## 21. 성능 테스트 재현 순서

### 21.1 인프라 실행

```bash
docker compose up -d
```

MySQL, Redis, Elasticsearch가 정상 실행되는지 확인한다.

```bash
docker compose ps
```

### 21.2 위치 검색 데이터 생성

```bash
bash performance/scripts/setup-accommodation-location-search-data.sh
```

생성 결과에서 다음 값을 확인한다.

```text
total_count=50000
keyword_match_count=10000
```

### 21.3 Elasticsearch 전체 재색인

```bash
ACCOMMODATION_SEARCH_REINDEX_ENABLED=true \
./gradlew bootRun
```

다음 로그를 확인한다.

```text
숙소 검색 Elasticsearch 전체 재색인을 완료했습니다.
indexedCount=50000
```

### 21.4 Elasticsearch Document 수 확인

```bash
curl -s \
  "http://localhost:9200/roompick-accommodations-v1/_count"
```

예상 결과:

```json
{
  "count": 50000
}
```

### 21.5 geo_point 매핑 확인

```bash
curl -s \
  "http://localhost:9200/roompick-accommodations-v1/_mapping"
```

다음 매핑을 확인한다.

```json
"location": {
  "type": "geo_point"
}
```

### 21.6 일반 서버 재실행

재색인 완료 후 서버를 종료한다.

```text
Ctrl + C
```

재색인 옵션 없이 다시 실행한다.

```bash
./gradlew bootRun
```

### 21.7 실제 검색 확인

```bash
curl -s \
  "http://localhost:8080/api/v1/accommodations/search?latitude=37.5665&longitude=126.9780&radiusKm=5&limit=5"
```

keyword 검색:

```bash
curl -s \
  "http://localhost:8080/api/v1/accommodations/search?keyword=%EB%A3%B8%ED%94%BD&latitude=37.5665&longitude=126.9780&radiusKm=5&limit=5"
```

---

## 22. 성능 측정 재현

공통 측정 스크립트는 다음 파일이다.

```text
performance/scripts/measure-accommodation-location-search.sh
```

### 22.1 MySQL geo-only

Facade가 MySQL 검색 경로를 사용하는 기준 구현에서 측정한다.

```bash
SEARCH_ENGINE=mysql \
KEYWORD="" \
LATITUDE=37.5665 \
LONGITUDE=126.9780 \
RADIUS_KM=5 \
LIMIT=20 \
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
RUNS=3 \
bash performance/scripts/measure-accommodation-location-search.sh
```

### 22.2 MySQL geo-keyword

```bash
SEARCH_ENGINE=mysql \
KEYWORD="룸픽" \
LATITUDE=37.5665 \
LONGITUDE=126.9780 \
RADIUS_KM=5 \
LIMIT=20 \
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
RUNS=3 \
bash performance/scripts/measure-accommodation-location-search.sh
```

### 22.3 Elasticsearch geo-only

Facade가 Elasticsearch 검색 경로를 사용하는 구현에서 측정한다.

```bash
SEARCH_ENGINE=elasticsearch \
KEYWORD="" \
LATITUDE=37.5665 \
LONGITUDE=126.9780 \
RADIUS_KM=5 \
LIMIT=20 \
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
RUNS=3 \
bash performance/scripts/measure-accommodation-location-search.sh
```

### 22.4 Elasticsearch geo-keyword

```bash
SEARCH_ENGINE=elasticsearch \
KEYWORD="룸픽" \
LATITUDE=37.5665 \
LONGITUDE=126.9780 \
RADIUS_KM=5 \
LIMIT=20 \
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
RUNS=3 \
bash performance/scripts/measure-accommodation-location-search.sh
```

각 실행은 timestamp 기반의 새로운 결과 디렉터리를 생성하므로 기존 공식 결과를 덮어쓰지 않는다.

---

## 23. 원본 결과 파일

이번 공식 측정 결과는 다음 디렉터리에 보관했다.

### 23.1 MySQL geo-only

```text
performance/results/location-search/
20260810-144307-mysql-geo-only/
```

### 23.2 MySQL geo-keyword

```text
performance/results/location-search/
20260810-144737-mysql-geo-keyword/
```

### 23.3 Elasticsearch geo-only

```text
performance/results/location-search/
20260810-175101-elasticsearch-geo-only/
```

### 23.4 Elasticsearch geo-keyword

```text
performance/results/location-search/
20260810-190138-elasticsearch-geo-keyword/
```

각 디렉터리에는 다음 자료가 포함된다.

```text
conditions.txt
warmup.txt
query-counts.csv

run-1.txt
run-1-summary.json

run-2.txt
run-2-summary.json

run-3.txt
run-3-summary.json

summary.csv
```

---

## 24. 운영 구조에서 고려할 사항

### 24.1 MySQL은 Source of Truth

Elasticsearch는 검색을 위한 Read Model이다.

숙소 생성·수정·상태 변경의 원본 데이터는 MySQL에 유지한다.

```text
Write
→ MySQL

Search
→ Elasticsearch
```

Elasticsearch 장애 또는 데이터 유실 시 MySQL을 기반으로 인덱스를 다시 생성할 수 있어야 한다.

### 24.2 실시간 인덱스 동기화

이번 전체 재색인은 초기 데이터 적재와 복구 경로를 제공한다.

운영 환경에서는 숙소가 생성·수정·비활성화될 때 Elasticsearch Document도 함께 동기화할 필요가 있다.

추후 다음 흐름을 적용할 수 있다.

```text
숙소 생성 / 수정 / 비활성화
→ MySQL Transaction Commit
→ Elasticsearch Document 동기화
```

MySQL 트랜잭션과 Elasticsearch 작업을 동일한 DB 트랜잭션처럼 취급할 수 없으므로 실패 처리와 재동기화 전략이 필요하다.

### 24.3 인덱스 버전

현재 인덱스명은 다음과 같다.

```text
roompick-accommodations-v1
```

향후 검색 매핑이 크게 변경되면 다음과 같이 새 버전 인덱스를 생성할 수 있다.

```text
roompick-accommodations-v1
→
roompick-accommodations-v2
```

운영 환경에서는 alias 기반 무중단 인덱스 전환도 추가로 검토할 수 있다.

### 24.4 한국어 검색 품질

이번 keyword 검색은 Elasticsearch 도입과 위치 검색 성능 비교에 초점을 맞췄다.

다음 검색 품질 기능은 이번 범위에서 제외했다.

* 한국어 형태소 분석기
* 자동완성
* 초성 검색
* 오타 보정
* 검색어 추천
* 동의어
* 검색 점수 튜닝

이 항목들은 별도 검색 품질 고도화 과제로 진행할 수 있다.

### 24.5 장소 검색 / Geocoding

이번 API는 좌표를 직접 입력하는 사용자 UX를 의미하지 않는다.

실제 서비스에서는 다음과 같이 좌표를 전달할 수 있다.

```text
현재 위치 버튼
→ 브라우저 GPS
→ 좌표
→ 위치 검색

"서울역" 검색
→ 장소 검색 / Geocoding
→ 서울역 좌표
→ 위치 검색
```

`서울역 → latitude / longitude` 변환은 이번 Elasticsearch 검색과 별도의 후속 기능이다.

---

## 25. 테스트 한계

이번 테스트에는 다음 한계가 있다.

* 애플리케이션과 k6가 동일한 로컬 macOS 장비에서 실행됐다.
* MySQL과 Elasticsearch 모두 로컬 Docker 환경이다.
* 실제 운영 네트워크 지연이 포함되지 않았다.
* 단일 애플리케이션 인스턴스에서 측정했다.
* 10 VU, 30초의 비교적 짧은 부하 테스트다.
* JVM GC와 JIT 상태가 실행마다 완전히 동일하지 않을 수 있다.
* Elasticsearch는 단일 노드 구성이다.
* Elasticsearch 운영 클러스터의 replica, shard 분산 효과가 포함되지 않았다.
* MySQL 기준 구현은 현재의 `ST_Distance_Sphere` 검색 구조를 기준으로 한다.
* MySQL Spatial Index 등 별도의 공간 검색 최적화와 비교하지 않았다.
* geo-keyword 데이터는 전체 50,000건 중 20%가 keyword에 매칭되는 인위적인 분포다.
* `Com_select`는 API 단위가 아닌 MySQL 서버의 글로벌 누적 카운터이므로 다른 조회가 함께 포함될 수 있다.
* 한국어 검색 품질 분석은 이번 측정 범위에 포함하지 않았다.

따라서 이번 결과를 다음과 같이 일반화해서는 안 된다.

```text
Elasticsearch는 항상 MySQL보다 89배 빠르다.
```

정확한 해석은 다음과 같다.

```text
동일한 RoomPick 로컬 환경,
ACTIVE 숙소 50,000건,
서울시청 중심 5km,
limit=20,
10 VU,
30초 조건에서

현재 MySQL ST_Distance_Sphere 기반 위치 검색과
Elasticsearch geo_distance 기반 위치 검색을 비교했을 때

Elasticsearch 검색 경로에서
응답시간과 처리량이 크게 개선됐다.
```

---

## 26. 전체 결과 요약

| 시나리오        | 엔진            |       평균 |      p50 |        p95 |        p99 |          처리량 | 오류율 |
| ----------- | ------------- | -------: | -------: | ---------: | ---------: | -----------: | --: |
| geo-only    | MySQL         | 573.62ms | 508.77ms | 1,133.05ms | 1,438.76ms |    17.75 RPS |  0% |
| geo-only    | Elasticsearch |   6.12ms |   5.35ms |    10.78ms |    20.64ms | 1,586.91 RPS |  0% |
| geo-keyword | MySQL         | 308.28ms | 292.67ms |   432.05ms |   564.07ms |    32.53 RPS |  0% |
| geo-keyword | Elasticsearch |   5.26ms |   4.69ms |     8.61ms |    17.00ms | 1,834.14 RPS |  0% |

---

## 27. 결론

### 27.1 위치 검색 성능

50,000건의 동일 숙소 데이터에서 MySQL `ST_Distance_Sphere` 기반 검색과 Elasticsearch `geo_distance` 기반 검색을 비교했다.

geo-only 평균 응답시간은:

```text
573.62ms
→
6.12ms
```

약 98.93% 감소했다.

geo-keyword 평균 응답시간은:

```text
308.28ms
→
5.26ms
```

약 98.29% 감소했다.

### 27.2 Tail Latency

geo-only p95는:

```text
1,133.05ms
→
10.78ms
```

약 99.05% 감소했다.

p99도:

```text
1,438.76ms
→
20.64ms
```

약 98.57% 감소했다.

평균 응답시간뿐 아니라 느린 요청의 지연시간도 크게 개선됐다.

### 27.3 처리량

geo-only 처리량은:

```text
17.75 RPS
→
1,586.91 RPS
```

약 89.38배 증가했다.

geo-keyword 처리량은:

```text
32.53 RPS
→
1,834.14 RPS
```

약 56.38배 증가했다.

### 27.4 DB 리소스 분리

MySQL 검색에서는 위치 검색 요청마다 MySQL SELECT가 발생했다.

Elasticsearch 적용 후 숙소 검색 경로는 검색 엔진으로 분리되어 MySQL의 예약·결제·숙소 관리 등 트랜잭션 처리와 검색 트래픽의 리소스 경합을 줄일 수 있는 구조가 됐다.

### 27.5 검색 확장성

이번 작업으로 다음 검색 고도화의 기반을 마련했다.

* `geo_point`
* `geo_distance`
* 반경 검색
* 거리순 정렬
* 숙소명·주소 keyword 검색
* 전체 재색인
* Keyset Pagination
* Bulk Index
* MySQL Source of Truth
* Elasticsearch Read Model
* 인덱스 버전 관리

추후 장소 검색/Geocoding, 한국어 검색 분석기, 자동완성, 인덱스 실시간 동기화 등을 별도 기능으로 확장할 수 있다.

이번 테스트 결과를 통해 RoomPick의 위치 기반 숙소 검색에서는 현재 MySQL 기준 구현보다 Elasticsearch 검색 전용 구조가 성능과 검색 확장성 측면에서 유의미한 개선을 제공하는 것을 확인했다.
