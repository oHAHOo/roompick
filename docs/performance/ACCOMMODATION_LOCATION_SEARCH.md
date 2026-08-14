# 위치 기반 숙소 검색 최적화 및 MySQL·Elasticsearch 성능 비교

## 1. 테스트 목적

RoomPick 숙소 위치 검색에서 다음 세 가지 검색 구조를 동일한 데이터와 부하 조건으로 비교한다.

```text
1. MySQL Baseline
   ST_Distance_Sphere 기반 반경 검색

2. MySQL Bounding Box 최적화
   Bounding Box
   + latitude / longitude 복합 인덱스
   + ST_Distance_Sphere 정확 거리 검증

3. Elasticsearch
   geo_point / geo_distance 기반 검색
```

이번 테스트의 목적은 단순히 MySQL과 Elasticsearch의 성능 차이를 확인하는 것이 아니다.

먼저 기존 MySQL 위치 검색에서 병목을 확인하고, MySQL 자체에서 적용할 수 있는 Bounding Box와 인덱스 최적화를 수행한 뒤 Elasticsearch 검색 전용 구조와 다시 비교한다.

이를 통해 다음 항목을 확인한다.

* 평균 응답시간
* p50 응답시간
* p95 응답시간
* p99 응답시간
* 처리량
* 오류율
* MySQL SELECT 횟수
* 위치 반경 검색 정확성
* 거리 오름차순 정렬
* 숙소명·주소 keyword 검색
* Bounding Box 적용에 따른 후보군 감소
* MySQL 실행계획과 좌표 인덱스 사용 여부
* Elasticsearch 도입에 따른 검색 DB 부하 변화
* 성능뿐 아니라 운영 복잡도와 인프라 비용을 고려한 최종 기술 선택

테스트 대상 API는 세 검색 구조 모두 동일하다.

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

예를 들어 다음과 같은 다양한 입력을 동일한 위치 검색 API에서 처리할 수 있다.

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

현재 사용자 입력 해석과 숙소 검색 엔진은 다음처럼 분리되어 있다.

```text
사용자 입력 해석
장소명
→ GET /api/v1/places/search
→ Kakao Local API
→ 장소 후보 + latitude / longitude

숙소 검색 엔진
사용자가 선택한 latitude / longitude
→ GET /api/v1/accommodations/search
→ MySQL Bounding Box 또는 Elasticsearch
→ 주변 숙소
```

장소명 검색과 좌표 변환은 현재 구현되어 있지만, 이 문서의 성능 비교 대상은 두 번째 영역인
**좌표가 주어졌을 때 주변 숙소를 검색하는 검색 엔진**이다. Kakao Local API 호출 시간과 네트워크
지연은 MySQL Baseline, MySQL Bounding Box, Elasticsearch 측정값에 포함되지 않았다.

초기 구현은 MySQL `ST_Distance_Sphere()`를 사용했다. 이후 50,000건 성능 테스트에서 전체 후보에 대한 정확 거리 계산 비용이 크게 나타나 MySQL 내부 최적화 가능성을 먼저 검토했다.

따라서 고도화 순서는 다음과 같이 잡았다.

```text
MySQL Baseline
→ Bounding Box 후보군 선필터링
→ 좌표 복합 인덱스와 실행계획 검증
→ MySQL 최적화 성능 재측정
→ Elasticsearch geo_distance 도입
→ 동일 조건 재측정
→ 성능 + 운영 복잡도 + 비용을 함께 고려해 최종 판단
```

---

## 3. 기존 MySQL Baseline 검색 구조

최초 MySQL 위치 검색은 `ST_Distance_Sphere()`를 사용하여 구현했다.

검색 흐름은 다음과 같다.

```text
Controller
→ AccommodationFacade
→ AccommodationLocationSearchService
→ AccommodationLocationSearchRepository
→ MySQL
```

Baseline 검색에서는 다음 조건을 사용한다.

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
limit
```

거리 계산에서는 MySQL `POINT`의 X축과 Y축 의미에 맞춰 다음 순서를 사용한다.

```text
POINT(longitude, latitude)
```

위치 검색 결과에 필요한 필드만 Native Query Projection으로 조회하여 `Accommodation` Entity 전체 로딩은 피했다.

다만 Baseline 구조에서는 정확한 거리 계산 전에 위도와 경도로 후보군을 줄이는 과정이 없었다.

```text
전체 후보
→ ST_Distance_Sphere
→ 반경 필터
→ 거리 정렬
→ limit
```

따라서 데이터가 증가할수록 많은 후보에 대해 `ST_Distance_Sphere()`가 실행되는 구조였다.

이 병목을 줄이기 위해 이후 다음 MySQL 최적화 단계를 추가했다.

```text
MySQL Baseline
→ Bounding Box 계산
→ latitude / longitude 범위 선필터링
→ 좌표 인덱스
→ ST_Distance_Sphere 정확 거리 검증
```

Bounding Box 구현과 인덱스 실행계획 비교는 `15. MySQL Bounding Box + 좌표 인덱스 최적화`에서 상세히 다룬다.

---

## 4. Elasticsearch 검색 구조

Elasticsearch 검색 엔진을 선택한 경우 공개 검색 API의 흐름은 다음과 같다.

```text
Controller
→ AccommodationFacade
→ AccommodationElasticsearchLocationSearchService
→ AccommodationElasticsearchLocationSearchRepository
→ Elasticsearch
```

현재 Facade는 설정값에 따라 MySQL Bounding Box 검색과 Elasticsearch 검색 중 하나를 선택할 수 있다.
production에서는 `MYSQL`을 사용하며, Elasticsearch 전용 Repository·Service·재색인 Bean은 생성하지 않는다.
따라서 Elasticsearch 서버가 없어도 애플리케이션을 기동할 수 있다. Elasticsearch 경로는 local 성능 비교와
향후 재도입을 위해 설정값이 `ELASTICSEARCH`일 때만 활성화한다.

```text
ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL
→ AccommodationLocationSearchService
→ MySQL Bounding Box 검색

ACCOMMODATION_LOCATION_SEARCH_ENGINE=ELASTICSEARCH
→ AccommodationElasticsearchLocationSearchService
→ Elasticsearch 검색
```

Controller는 검색 엔진 구현을 직접 알지 않고 동일한 Facade 메서드만 호출한다.

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

| 필드 | Elasticsearch 타입 | 용도 |
| --- | --- | --- |
| `accommodationId` | long | MySQL 숙소 ID |
| `name` | text | 숙소명 검색 |
| `address` | text | 주소 검색 |
| `status` | keyword | ACTIVE 필터 |
| `location` | geo_point | 반경 검색 및 거리 정렬 |

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

좌표 컬럼은 Flyway `V9__add_location_to_accommodations.sql`에서 추가했다.

기존 숙소에는 좌표가 존재하지 않을 수 있으므로 nullable로 추가했다.

위치 검색에서는 다음 조건을 모두 만족하는 숙소만 대상으로 한다.

```text
latitude IS NOT NULL
AND
longitude IS NOT NULL
```

애플리케이션에서는 좌표 입력 시 다음 범위를 검증한다.

| 항목 | 허용 범위 |
| --- | ---: |
| 위도 | -90 ~ 90 |
| 경도 | -180 ~ 180 |
| 검색 반경 | 0 초과 ~ 100km |
| limit | 1 ~ 100 |

`NaN`, 양의 무한대, 음의 무한대도 Service에서 차단한다.

잘못된 값은 MySQL 또는 Elasticsearch에 전달하기 전에 검증하여 불필요한 검색 리소스 사용을 방지한다.

MySQL과 Elasticsearch 모두 동일한 API 응답 DTO를 사용하므로 검색 엔진이 달라져도 Controller 응답 형식은 유지된다.

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

## 7. 로컬 재색인 및 검색 엔진 선택 정책

전체 재색인을 공개 API로 노출하지 않았다.

대신 local 프로필에서 특정 설정이 활성화된 경우에만 실행되는 `ApplicationRunner`를 사용한다.

재색인 기본 설정은 다음과 같다.

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

위치 검색 엔진은 local 설정에서 별도로 선택할 수 있다.

```yaml
roompick:
  search:
    location-engine: ${ACCOMMODATION_LOCATION_SEARCH_ENGINE:ELASTICSEARCH}
```

실행 예시는 다음과 같다.

```bash
ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL \
./gradlew bootRun
```

```bash
ACCOMMODATION_LOCATION_SEARCH_ENGINE=ELASTICSEARCH \
./gradlew bootRun
```

production 설정은 `roompick.search.location-engine: MYSQL`로 고정한다. 운영 배포에서는 Elasticsearch
Repository·검색 Service·재색인 Bean을 활성화하지 않으며 Elasticsearch 컨테이너도 필요하지 않다.

성능 측정에서 검색 엔진 태그와 실제 애플리케이션 검색 경로가 뒤섞이지 않도록 `/actuator/info`에도 현재 검색 엔진을 노출한다.

```json
{
  "roompick": {
    "search": {
      "location-engine": "MYSQL"
    }
  }
}
```

측정 스크립트는 이 값을 확인한 뒤 기대하는 검색 엔진과 실제 실행 엔진이 다르면 측정을 시작하지 않는다.

---

## 8. 테스트 환경

### 8.1 애플리케이션 환경

| 항목 | 값 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.5.14 |
| 애플리케이션 실행 | `./gradlew bootRun` |
| 애플리케이션 주소 | `http://localhost:8080` |
| MySQL | MySQL 8.4 Docker |
| MySQL 로컬 포트 | `3307` |
| Elasticsearch | Elasticsearch 8.18.1 Docker |
| Elasticsearch 주소 | `http://localhost:9200` |
| Elasticsearch 인덱스 | `roompick-accommodations-v1` |
| 부하 테스트 도구 | k6 |
| 기존 MySQL / Elasticsearch 측정 | 2026-08-10 |
| Bounding Box 측정 | 2026-08-12 |

애플리케이션, k6, MySQL, Elasticsearch는 동일한 로컬 macOS 환경에서 실행했다.

따라서 이번 결과는 운영 서버의 최대 처리량이나 각 저장소의 절대적인 성능을 의미하지 않는다.

**동일한 RoomPick 로컬 환경과 동일 데이터·부하 조건에서 MySQL Baseline, MySQL Bounding Box + Index, Elasticsearch 검색 경로의 상대적인 차이를 확인하기 위한 결과**로 해석한다.

또한 MySQL Baseline과 Elasticsearch 결과는 2026-08-10에 측정했고 Bounding Box 결과는 2026-08-12에 측정했다. 동일한 로컬 구성과 테스트 데이터·부하 조건을 사용했지만 실행 시점이 완전히 같지는 않다는 점을 한계로 남긴다.

---

## 9. 성능 테스트 데이터

위치 검색 성능 비교를 위해 ACTIVE 숙소 성능 테스트 데이터 50,000개를 생성했다.

### 9.1 데이터 구성

| 항목 | 값 |
| --- | ---: |
| 생성한 성능 테스트 숙소 | 50,000건 |
| `룸픽` keyword 포함 성능 테스트 숙소 | 10,000건 |
| keyword 비율 | 20% |
| 최소 위도 | 37.450000 |
| 최대 위도 | 37.649200 |
| 최소 경도 | 126.800000 |
| 최대 경도 | 127.198000 |

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

따라서 위치만 검색하는 시나리오와 keyword를 함께 사용하는 시나리오를 같은 성능 테스트 데이터에서 비교할 수 있다.

성능 데이터 생성 스크립트는 기존 일반 숙소 데이터를 삭제하지 않는다.

Bounding Box `EXPLAIN ANALYZE`를 수행한 시점에는 기존 일반 숙소 데이터가 함께 존재하여 `accommodations` 테이블의 실제 Table Scan에서 50,040건이 관측됐다.

따라서 다음 두 수치는 구분해서 기록한다.

```text
성능 테스트로 생성한 숙소
50,000건

EXPLAIN ANALYZE 시 실제 Table Scan 행
50,040건
```

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

세 검색 구조의 비교에는 동일한 검색 조건을 사용했다.

```text
MySQL Baseline
MySQL Bounding Box + Index
Elasticsearch
```

| 항목 | 값 |
| --- | ---: |
| 검색 중심 | 서울시청 기준 좌표 |
| 위도 | 37.5665 |
| 경도 | 126.9780 |
| 검색 반경 | 5km |
| limit | 20 |
| VU | 10 |
| 측정 시간 | 30초 |
| Think time | 0초 |
| 반복 측정 | 3회 |
| 오류율 기준 | 1% 미만 |

각 검색 구조에서 본 측정 전에 1 VU, 5초 워밍업을 수행했다.

JIT, Connection Pool, MySQL 및 Elasticsearch의 최초 요청 비용이 본 측정 결과에 과도하게 포함되는 것을 줄이기 위한 목적이다.

기존 MySQL Baseline 결과는 Bounding Box 적용 전에 측정한 공식 결과를 그대로 보존했다.

Bounding Box와 Elasticsearch 결과도 각각 timestamp 기반 별도 디렉터리에 저장하여 기존 공식 결과를 덮어쓰지 않았다.

---

## 11. 테스트 시나리오

세 검색 구조에서 동일한 두 가지 시나리오를 측정했다.

```text
MySQL Baseline
├─ geo-only
└─ geo-keyword

MySQL Bounding Box + Index
├─ geo-only
└─ geo-keyword

Elasticsearch
├─ geo-only
└─ geo-keyword
```

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

성능 결과를 구분하기 위해 다음 `SEARCH_ENGINE` 값을 사용한다.

```text
mysql
→ 기존 MySQL Baseline 결과 식별

mysql-bounding-box
→ MySQL Bounding Box + 좌표 인덱스 결과

elasticsearch
→ Elasticsearch 결과
```

`SEARCH_ENGINE`은 API 요청 파라미터가 아니라 성능 결과를 구분하기 위한 값이다.

실제 애플리케이션 검색 엔진은 다음 환경변수로 결정한다.

```text
ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL

또는

ACCOMMODATION_LOCATION_SEARCH_ENGINE=ELASTICSEARCH
```

측정 스크립트는 본 측정을 시작하기 전에 `/actuator/health`와 `/actuator/info`를 확인한다.

```text
SEARCH_ENGINE=mysql-bounding-box
→ 기대 실제 엔진 MYSQL

SEARCH_ENGINE=elasticsearch
→ 기대 실제 엔진 ELASTICSEARCH
```

측정용 `SEARCH_ENGINE`과 실제 애플리케이션 검색 엔진이 다르면 측정을 즉시 중단한다.

이를 통해 결과 파일 이름만 MySQL이고 실제 검색은 Elasticsearch에서 수행되거나, 그 반대가 되는 잘못된 측정을 방지한다.

k6 스크립트는 다음 항목을 검증한다.

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

MySQL `Com_select` 증가량도 각 실행마다 별도로 저장한다.

단, `Com_select`는 API별 전용 지표가 아니라 MySQL 서버 전체의 글로벌 누적 값이므로 다른 SELECT가 측정 구간에 포함될 수 있다.

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

따라서 기존 MySQL 위치 검색 경로에서는 API 요청당 숙소 검색 SELECT가 1회 발생했다.

기준 구현은 별도의 위도/경도 선필터링 없이 전체 후보에 대해 `ST_Distance_Sphere()`를 적용한 뒤 반경과 거리순 정렬을 수행했다.

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

이번 데이터에서는 keyword 조건이 없는 geo-only보다 geo-keyword가 더 빠르게 측정됐다.

이는 keyword 조건이 항상 위치 검색을 빠르게 만든다는 의미가 아니라, **이번 테스트 데이터 분포와 MySQL 쿼리 구조에서 관측된 결과**다.

---

## 15. MySQL Bounding Box + 좌표 인덱스 최적화

Elasticsearch와 비교하기 전에 기존 MySQL 위치 검색 자체에서 줄일 수 있는 비용을 먼저 확인했다.

기존 검색은 많은 후보에 대해 `ST_Distance_Sphere()`를 계산했기 때문에, 정확한 거리 계산을 수행하기 전에 상대적으로 값싼 위도·경도 범위 조건으로 후보를 줄이는 Bounding Box를 추가했다.

### 15.1 검색 구조

최적화된 검색 흐름은 다음과 같다.

```text
사용자 위치 + radius
→ 요청값 검증
→ Bounding Box 계산
→ latitude / longitude 범위 선필터링
→ ST_Distance_Sphere 정확 거리 검증
→ 거리 오름차순 정렬
→ limit 적용
```

Bounding Box는 원형 검색 반경을 감싸는 사각형이기 때문에 Bounding Box 조건만으로 최종 결과를 결정하지 않는다.

사각형 모서리에는 실제 반경 밖의 데이터가 존재할 수 있으므로 최종적으로 `ST_Distance_Sphere()`를 유지한다.

```text
Bounding Box
= 최종 거리 판정 X

Bounding Box
= 정확 거리 계산 후보군 선필터 O
```

서울시청 기준 반경 5km 조건에서 실제 후보 수는 다음과 같았다.

```text
전체 조회 대상:       50,040건
Bounding Box 후보:     6,384건
실제 5km 반경 내부:    5,018건
```

Bounding Box 후보는 전체 50,040건의 약 12.8% 수준이었다.

따라서 정확 거리 계산 전에 후보군 자체를 크게 줄일 수 있음을 확인했다.

### 15.2 Bounding Box 계산과 경계 처리

Bounding Box 계산은 별도 값 객체인 `AccommodationLocationBoundingBox`에서 담당한다.

Service의 책임은 다음과 같이 유지했다.

```text
AccommodationLocationSearchService
├─ 입력값 검증
├─ keyword 정규화
├─ Bounding Box 계산 요청
├─ Repository 호출
└─ Projection → 응답 DTO 변환
```

일반적인 좌표뿐 아니라 다음 경계도 처리한다.

#### 날짜변경선

검색 범위가 경도 `+180° / -180°`를 넘으면 단순한 하나의 `BETWEEN` 조건으로 표현할 수 없다.

예:

```text
중심 경도 179.9°
→ Bounding Box가 +180°를 초과
→ 반대편 -180° 구간으로 이어짐
```

이 경우 Repository에서는 다음 형태로 두 구간을 처리한다.

```text
longitude >= minLongitude
OR
longitude <= maxLongitude
```

#### 극점

검색 반경이 북극 또는 남극을 포함하면 특정 경도 구간으로 안전하게 제한하지 않고 전체 경도 범위를 허용한다.

```text
minLongitude = -180
maxLongitude = 180
```

Bounding Box 단위 테스트에서는 다음 케이스를 검증했다.

* 서울시청 기준 일반 Bounding Box
* 날짜변경선을 넘는 경도
* 검색 반경이 북극을 포함하는 경우

### 15.3 정확 거리 재검증

Repository 통합 테스트에서는 Bounding Box 내부이지만 실제 원형 반경 밖에 위치하는 숙소를 별도로 생성했다.

```text
Bounding Box 사각형 내부
하지만
실제 반경 밖
```

이 숙소가 최종 결과에서 제외되는지 검증함으로써 Bounding Box가 최종 판정으로 잘못 사용되지 않고 `ST_Distance_Sphere()` 정확 거리 검증이 유지되는 것을 확인했다.

### 15.4 EXPLAIN ANALYZE - Bounding Box만 적용

Bounding Box 조건만 추가했을 때는 별도의 좌표 인덱스가 없어 여전히 전체 테이블 스캔이 발생했다.

#### 기존 MySQL Baseline

```text
Table scan rows: 50,040
실제 반경 내부: 5,018
actual time: 약 96.9ms
```

#### Bounding Box만 적용

```text
Table scan rows: 50,040
Bounding Box 후보: 6,384
실제 반경 내부: 5,018
actual time: 약 100ms
```

Bounding Box 조건 자체는 후보를 줄일 수 있었지만, 그 후보를 찾기 위해 여전히 50,040건 전체를 읽었기 때문에 실행계획상 성능 개선은 확인되지 않았다.

즉 다음 구조였다.

```text
50,040건 Table Scan
→ Bounding Box 조건 평가
→ 6,384건 후보
→ 정확 거리 검증
→ 5,018건 반경 내부
```

따라서 Bounding Box 효과를 실제 테이블 접근량 감소로 연결하려면 좌표 검색 인덱스가 필요하다고 판단했다.

### 15.5 인덱스 구조 비교

첫 번째로 다음 B-Tree 복합 인덱스를 추가했다.

```text
(status, latitude, longitude)
```

Flyway 마이그레이션:

```text
V10__add_location_search_index.sql
```

하지만 성능 테스트 데이터 대부분이 `ACTIVE`여서 `status` 선택도가 매우 낮았다.

`SHOW INDEX`에서도 `status`의 Cardinality가 1로 관측됐다.

MySQL 옵티마이저는 통계 갱신 후에도 이 인덱스를 자동 선택하지 않고 Full Table Scan을 유지했다.

```text
Bounding Box + (status, latitude, longitude)
자동 선택
→ Table Scan

FORCE INDEX
→ Index Range Scan
→ actual time 약 45.1ms
```

이후 Bounding Box의 핵심 범위 조건만 포함한 다음 인덱스를 추가로 실험했다.

```text
(latitude, longitude)
```

이 인덱스도 현재 데이터 분포에서는 옵티마이저가 자동 선택하지 않았지만 강제로 사용할 경우 더 좋은 단일 `EXPLAIN ANALYZE` 결과가 나타났다.

```text
Bounding Box + (latitude, longitude)
FORCE INDEX
→ Index Range Scan
→ actual time 약 37.9ms
```

단일 실행 결과를 정리하면 다음과 같다.

| 방식 | 실행 계획 | actual time |
| --- | --- | ---: |
| MySQL Baseline | Table Scan | 약 96.9ms |
| Bounding Box만 | Table Scan | 약 100ms |
| `(status, latitude, longitude)` 자동 | Table Scan | 약 75.3ms |
| `(status, latitude, longitude)` 강제 | Index Range Scan | 약 45.1ms |
| `(latitude, longitude)` 자동 | Table Scan | 약 91.6ms |
| `(latitude, longitude)` 강제 | Index Range Scan | 약 37.9ms |

위 수치는 각각 한 번의 `EXPLAIN ANALYZE` 실행값이므로 최종 성능 수치로 사용하지 않고 **실행계획과 인덱스 선택 판단 근거**로만 사용한다.

### 15.6 최종 인덱스와 Flyway 이력

최종 위치 검색 인덱스는 다음과 같이 결정했다.

```text
idx_accommodations_latitude_longitude
(latitude, longitude)
```

이미 로컬 DB에 적용된 V10 migration을 수정하지 않고 Flyway 이력을 보존하기 위해 다음 migration을 추가했다.

```text
V10
(status, latitude, longitude) 생성

V11
기존 V10 인덱스 제거
→ (latitude, longitude) 최종 인덱스 생성
```

최종 DB 인덱스 상태는 다음과 같다.

```text
PRIMARY
└─ accommodation_id

idx_accommodations_latitude_longitude
├─ latitude
└─ longitude
```

기존 성능 측정 당시 Repository 위치 검색 쿼리에서는 로컬 성능 비교 결과를 기준으로 해당 인덱스를
명시적으로 사용했다.

```sql
FORCE INDEX (
    idx_accommodations_latitude_longitude
)
```

단, `FORCE INDEX`는 당시 성능 테스트 데이터 분포와 실행계획을 기준으로 선택한 최적화다. 현재 production
쿼리에서는 특정 실행계획을 영구 강제하지 않고 MySQL 옵티마이저가 데이터 분포, 검색 반경과 keyword
선택도에 따라 계획을 선택하도록 힌트를 제거했다. 좌표 복합 인덱스와 Bounding Box 조건은 그대로 유지한다.

향후 실제 운영 데이터의 규모, 상태 분포, 지역 분포, 검색 반경이 달라지면 MySQL 옵티마이저의 비용 계산과 인덱스 효율도 달라질 수 있으므로 운영 환경에서는 `EXPLAIN ANALYZE`를 다시 검증해야 한다.

### 15.7 Bounding Box geo-only 개별 실행 결과

기존 MySQL 및 Elasticsearch 테스트와 동일하게 다음 조건으로 3회 측정했다.

```text
latitude = 37.5665
longitude = 126.9780
radius = 5km
limit = 20
keyword = 없음
VUs = 10
duration = 30s
runs = 3
```

| Run | 요청 수 | avg | p50 | p95 | p99 | RPS | 오류율 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 3,576 | 83.65ms | 82.05ms | 116.47ms | 173.85ms | 118.98 | 0% |
| 2 | 3,190 | 93.85ms | 93.76ms | 127.85ms | 198.04ms | 106.10 | 0% |
| 3 | 3,068 | 97.58ms | 97.12ms | 126.76ms | 170.85ms | 102.05 | 0% |

#### 3회 평균

| 지표 | 결과 |
| --- | ---: |
| 평균 응답시간 | 91.69ms |
| p50 | 90.98ms |
| p95 | 123.69ms |
| p99 | 180.91ms |
| 처리량 | 109.04 RPS |
| 오류율 | 0% |

MySQL SELECT 증가량과 요청 수가 각 실행에서 동일했다.

```text
Run 1: 3,576 requests / 3,576 SELECT
Run 2: 3,190 requests / 3,190 SELECT
Run 3: 3,068 requests / 3,068 SELECT
```

따라서 추가 조회를 통해 성능을 개선한 것이 아니라, **요청당 SELECT 1회 구조를 유지하면서 하나의 위치 검색 쿼리 내부 연산량을 줄인 결과**다.

기존 MySQL geo-only 기준선과 비교하면:

```text
avg
573.62ms → 91.69ms
약 84.01% 감소

p50
508.77ms → 90.98ms
약 82.12% 감소

p95
1,133.05ms → 123.69ms
약 89.08% 감소

p99
1,438.76ms → 180.91ms
약 87.43% 감소

RPS
17.75 → 109.04
약 6.14배 증가
```

### 15.8 Bounding Box geo-keyword 개별 실행 결과

동일한 조건에서 `keyword=룸픽`을 추가하여 측정했다.

| Run | 요청 수 | avg | p50 | p95 | p99 | RPS | 오류율 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 3,973 | 75.23ms | 73.27ms | 103.79ms | 178.10ms | 132.28 | 0% |
| 2 | 3,541 | 84.78ms | 83.68ms | 114.55ms | 163.90ms | 117.35 | 0% |
| 3 | 3,297 | 90.74ms | 89.97ms | 125.71ms | 182.36ms | 109.74 | 0% |

#### 3회 평균

| 지표 | 결과 |
| --- | ---: |
| 평균 응답시간 | 83.59ms |
| p50 | 82.31ms |
| p95 | 114.68ms |
| p99 | 174.79ms |
| 처리량 | 119.79 RPS |
| 오류율 | 0% |

기존 MySQL geo-keyword 기준선과 비교하면:

```text
avg
308.28ms → 83.59ms
약 72.89% 감소

p50
292.67ms → 82.31ms
약 71.88% 감소

p95
432.05ms → 114.68ms
약 73.46% 감소

p99
564.07ms → 174.79ms
약 69.01% 감소

RPS
32.53 → 119.79
약 3.68배 증가
```

MySQL 글로벌 SELECT 카운터는 다음과 같이 기록됐다.

```text
Run 1: 3,973 requests / 3,975 SELECT
Run 2: 3,541 requests / 3,543 SELECT
Run 3: 3,297 requests / 3,301 SELECT
```

위치 검색 Repository 자체는 요청당 하나의 검색 쿼리를 수행한다.

다만 측정에 사용한 `Com_select`는 MySQL 서버의 글로벌 누적 카운터이므로 측정 구간에서 다른 SELECT가 포함될 수 있다.

따라서 `+2`, `+2`, `+4`의 차이를 임의로 제거하지 않고 원본 측정값 그대로 보존한다.

---

## 16. Elasticsearch geo-only 결과

### 16.1 개별 실행 결과

| Run |   요청 수 |    avg |    p50 |     p95 |     p99 |      RPS | 오류율 |
| --- | -----: | -----: | -----: | ------: | ------: | -------: | --: |
| 1   | 45,273 | 6.42ms | 5.31ms | 12.87ms | 24.59ms | 1,508.94 |  0% |
| 2   | 51,166 | 5.67ms | 5.19ms |  9.10ms | 15.47ms | 1,705.23 |  0% |
| 3   | 46,404 | 6.26ms | 5.55ms | 10.37ms | 21.85ms | 1,546.56 |  0% |

### 16.2 3회 평균

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

## 17. Elasticsearch geo-keyword 결과

### 17.1 개별 실행 결과

| Run |   요청 수 |    avg |    p50 |    p95 |     p99 |      RPS | 오류율 |
| --- | -----: | -----: | -----: | -----: | ------: | -------: | --: |
| 1   | 56,517 | 5.11ms | 4.43ms | 8.78ms | 19.70ms | 1,883.74 |  0% |
| 2   | 56,026 | 5.16ms | 4.67ms | 8.18ms | 14.88ms | 1,867.34 |  0% |
| 3   | 52,545 | 5.51ms | 4.96ms | 8.88ms | 16.41ms | 1,751.35 |  0% |

### 17.2 3회 평균

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

현재 숙소 검색 API 경로가 Elasticsearch를 사용하는 경우 위치 검색 자체를 위해 MySQL을 조회하지 않는다.

다만 `Com_select`는 MySQL 서버의 글로벌 누적 카운터이므로 Run 3에서 관측된 2회는 측정 구간에 발생한 다른 조회 또는 백그라운드 요청이 포함된 값으로 해석한다.

따라서 원본 수치를 임의로 0으로 수정하지 않고 그대로 보존한다.

---

## 18. MySQL Baseline vs Bounding Box vs Elasticsearch 최종 비교

아래 값은 동일한 데이터와 검색 조건에서 각 경로를 3회 측정한 뒤 각 통계값을 단순 산술평균한 결과다.

이번 비교의 목적은 Elasticsearch와 최적화되지 않은 MySQL만 비교하는 것이 아니라, **MySQL 자체에서 수행할 수 있는 위치 검색 최적화를 먼저 적용한 뒤 검색 전용 엔진의 추가적인 효과를 확인하는 것**이다.

### 18.1 geo-only

| 지표      | MySQL Baseline | Bounding Box + Index | Elasticsearch |
| ------- | -------------: | -------------------: | ------------: |
| 평균 응답시간 |       573.62ms |              91.69ms |        6.12ms |
| p50     |       508.77ms |              90.98ms |        5.35ms |
| p95     |     1,133.05ms |             123.69ms |       10.78ms |
| p99     |     1,438.76ms |             180.91ms |       20.64ms |
| 처리량     |      17.75 RPS |           109.04 RPS |  1,586.91 RPS |
| 오류율     |             0% |                   0% |            0% |

#### MySQL Baseline → Bounding Box

```text
avg: 84.01% 감소
p50: 82.12% 감소
p95: 89.08% 감소
p99: 87.43% 감소
RPS: 약 6.14배 증가
```

#### Bounding Box → Elasticsearch

```text
avg: 91.69ms → 6.12ms
약 93.33% 추가 감소

p95: 123.69ms → 10.78ms
약 91.28% 추가 감소

p99: 180.91ms → 20.64ms
약 88.59% 추가 감소

RPS: 109.04 → 1,586.91
약 14.55배 증가
```

### 18.2 geo-keyword

| 지표      | MySQL Baseline | Bounding Box + Index | Elasticsearch |
| ------- | -------------: | -------------------: | ------------: |
| 평균 응답시간 |       308.28ms |              83.59ms |        5.26ms |
| p50     |       292.67ms |              82.31ms |        4.69ms |
| p95     |       432.05ms |             114.68ms |        8.61ms |
| p99     |       564.07ms |             174.79ms |       17.00ms |
| 처리량     |      32.53 RPS |           119.79 RPS |  1,834.14 RPS |
| 오류율     |             0% |                   0% |            0% |

#### MySQL Baseline → Bounding Box

```text
avg: 72.89% 감소
p50: 71.88% 감소
p95: 73.46% 감소
p99: 69.01% 감소
RPS: 약 3.68배 증가
```

#### Bounding Box → Elasticsearch

```text
avg: 83.59ms → 5.26ms
약 93.71% 추가 감소

p95: 114.68ms → 8.61ms
약 92.49% 추가 감소

p99: 174.79ms → 17.00ms
약 90.27% 추가 감소

RPS: 119.79 → 1,834.14
약 15.31배 증가
```

### 18.3 MySQL 자체 최적화 효과

이번 비교에서 중요한 점은 Elasticsearch를 도입하기 전에도 MySQL 위치 검색 자체에서 상당한 개선이 가능했다는 것이다.

기존 geo-only 검색은 평균 573.62ms였지만 Bounding Box와 좌표 인덱스를 적용한 뒤 평균 91.69ms로 감소했다.

```text
573.62ms
→ Bounding Box + Index
91.69ms
```

즉 Elasticsearch를 사용하지 않더라도 정확한 거리 계산 전에 후보군을 줄이는 것만으로 평균 응답시간이 약 84% 감소했다.

이를 통해 기존 MySQL과 Elasticsearch의 큰 성능 차이가 단순히 두 저장소의 절대적인 성능 차이만을 의미하는 것은 아니며, 기존 MySQL 쿼리에도 최적화 여지가 존재했다는 것을 확인했다.

### 18.4 Elasticsearch의 추가 성능 효과

MySQL을 Bounding Box와 좌표 인덱스로 최적화한 이후에도 Elasticsearch는 이번 로컬 테스트 조건에서 더 낮은 지연시간과 높은 처리량을 기록했다.

geo-only 평균 응답시간:

```text
MySQL Baseline
573.62ms

↓ Bounding Box + Index

91.69ms

↓ Elasticsearch

6.12ms
```

geo-keyword 평균 응답시간:

```text
MySQL Baseline
308.28ms

↓ Bounding Box + Index

83.59ms

↓ Elasticsearch

5.26ms
```

Bounding Box 최적화 경로와 비교해도 Elasticsearch는 이번 테스트에서 평균 응답시간 기준 약 15배 수준의 차이를 보였고, 처리량 역시 약 14~15배 높게 측정됐다.

다만 이 수치는 **RoomPick 로컬 환경, 50,000건의 성능 테스트 데이터, 서울시청 반경 5km, 10 VU라는 동일 조건에서 관측된 결과**이며 Elasticsearch가 모든 데이터와 모든 환경에서 동일한 배수로 빠르다는 의미는 아니다.

### 18.5 DB 부하와 운영 복잡도

MySQL Bounding Box 경로는 요청당 하나의 SELECT 구조를 유지한다.

```text
API 요청
→ MySQL
→ Bounding Box
→ 좌표 인덱스
→ ST_Distance_Sphere
→ 거리 정렬
```

별도의 검색 인프라가 필요 없고 MySQL 데이터와 검색 데이터 사이의 동기화 문제도 발생하지 않는다는 장점이 있다.

반면 Elasticsearch 경로는 다음과 같이 검색 부하를 MySQL과 분리한다.

```text
API 요청
→ Elasticsearch
→ geo_distance
→ 거리 정렬
```

읽기 성능과 검색 기능 확장성에서는 이점이 있지만 다음 운영 비용이 추가된다.

* Elasticsearch 인프라 운영
* 검색 인덱스 저장 공간
* MySQL과 Elasticsearch 데이터 동기화
* 재색인 운영
* 장애 및 모니터링 대상 증가
* 인프라 비용 증가 가능성

따라서 기술 선택은 단순히 가장 빠른 수치만으로 결정할 수 없다.

### 18.6 최종 기술 선택

현재 RoomPick 위치 검색에서는 **Elasticsearch를 검색 전용 Read Model로 사용하는 구조를 최종 목표 경로로 유지한다.**

근거는 다음과 같다.

1. MySQL을 Bounding Box와 좌표 인덱스로 최적화한 이후에도 Elasticsearch가 이번 테스트에서 더 낮은 평균 및 Tail Latency를 기록했다.
2. 동일한 10 VU 조건에서 Elasticsearch의 처리량이 Bounding Box MySQL보다 크게 높았다.
3. 위치 검색뿐 아니라 향후 숙소명·주소 검색, 검색 분석기, 자동완성 등 검색 기능을 확장할 수 있다.
4. 검색 부하를 예약·결제 등 트랜잭션 데이터를 처리하는 MySQL과 분리할 수 있다.

이번 비교를 통해 **현재 수준의 단순 반경 검색에는 Bounding Box + MySQL만으로도 충분한 응답 성능을 확보할 수 있음**을 확인했다. Elasticsearch의 추가 성능 효과보다 현재 규모의 운영 복잡도와 비용을 줄이는 편이 적절하다고 판단했다.

따라서 RoomPick의 선택은 다음과 같이 정리한다.

```text
Source of Truth
MySQL

현재 production 위치 검색
MySQL Bounding Box + 좌표 인덱스

local 성능 비교 및 향후 재도입 후보
Elasticsearch Read Model
```

Elasticsearch 구현과 성능 결과는 보존한다. 향후 운영 검색 경로로 재도입하려면 숙소 생성·좌표 변경·비활성화 시 인덱스를 일관되게 동기화하는 구조와 장애 대응 및 재색인 운영 정책이 추가로 필요하다.


---

## 19. 검색 결과 정확성 확인

성능뿐 아니라 실제 검색 결과와 Bounding Box 적용 후 정확성도 확인했다.

### 19.1 거리 오름차순

서울시청 좌표를 기준으로 다음과 같이 가까운 숙소부터 반환됐다.

```text
0.033km
0.055km
0.122km
0.144km
0.179km
```

k6에서도 각 응답의 `distanceKm`이 오름차순인지 지속적으로 검사했다.

### 19.2 keyword

`keyword=룸픽` 요청 시 `룸픽`에 매칭되는 숙소만 반환됐다.

예:

```text
룸픽 위치 성능 숙소 22395
룸픽 위치 성능 숙소 22645
룸픽 위치 성능 숙소 22145
...
```

### 19.3 Bounding Box 모서리 오탐 제거

Bounding Box는 원이 아니라 사각형이므로 사각형 내부이면서 실제 반경 밖인 숙소가 존재할 수 있다.

Repository 통합 테스트에서는 이 케이스를 별도로 생성했다.

```text
Bounding Box 내부
+
실제 radius 밖
→ 최종 결과에서 제외
```

이를 통해 최종 `ST_Distance_Sphere()` 반경 검증이 유지되는 것을 확인했다.

### 19.4 날짜변경선과 극점

Bounding Box 계산 단위 테스트에서 다음 경계를 검증했다.

```text
일반 서울 좌표
→ min/max latitude, longitude 계산

경도 +180° / -180° 경계
→ 두 경도 구간으로 처리

검색 반경이 극점을 포함
→ 전체 경도 범위 허용
```

따라서 일반 서울 데이터에만 맞춘 단순 `longitude BETWEEN` 구현에 의존하지 않는다.

### 19.5 MySQL과 Elasticsearch 거리값 차이

동일 숙소의 거리값은 MySQL과 Elasticsearch에서 매우 미세한 차이가 존재했다.

예:

```text
MySQL
0.0333584046 km

Elasticsearch
0.0333565325 km
```

각 검색 엔진의 거리 계산 방식에 따른 미세한 차이는 존재하지만 이번 반경 검색과 거리 정렬 요구사항에는 영향을 주지 않았다.

---

## 20. 테스트 검증

성능 측정 전후로 단위 테스트와 실제 저장소 통합 테스트를 수행했다.

### 20.1 Bounding Box 단위 테스트

`AccommodationLocationBoundingBoxTest`에서 다음 항목을 검증했다.

* 서울시청 기준 일반 Bounding Box 계산
* 최소/최대 위도·경도 계산
* 날짜변경선을 넘는 경우
* 검색 반경이 북극을 포함하는 경우

### 20.2 MySQL 위치 검색 Service 단위 테스트

`AccommodationLocationSearchServiceTest`에서 다음 항목을 검증했다.

* latitude 유효성 검증
* longitude 유효성 검증
* radius 유효성 검증
* limit 유효성 검증
* `NaN` / 무한대 차단
* keyword trim
* 공백 keyword를 `null`로 정규화
* Bounding Box 계산값을 Repository에 전달
* Projection 거리 단위를 meter → km로 변환
* 잘못된 입력에서 Repository를 호출하지 않음

### 20.3 MySQL Repository 통합 테스트

실제 MySQL 8.4 Testcontainer에서 다음 항목을 검증했다.

* Flyway migration 적용
* V11 최종 좌표 인덱스가 적용된 스키마에서 위치 검색 쿼리 실행
* Bounding Box 위도·경도 필터
* `ST_Distance_Sphere()` 실행
* Bounding Box 내부지만 실제 radius 밖인 숙소 제외
* ACTIVE 필터
* 좌표 없는 숙소 제외
* 거리 오름차순
* keyword 검색
* limit

테스트 DB는 Hibernate `create-drop`으로 임의 스키마를 만드는 대신 Flyway migration을 적용하고 Hibernate `validate`를 사용하여 실제 애플리케이션 스키마와 동일한 인덱스 구조를 검증한다.

### 20.4 위치 검색 엔진 라우팅 단위 테스트

`AccommodationFacadeTest`에서 설정값에 따른 검색 엔진 분기를 검증했다.

```text
MYSQL
→ AccommodationLocationSearchService 호출
→ Elasticsearch Service 호출하지 않음

ELASTICSEARCH
→ AccommodationElasticsearchLocationSearchService 호출
→ MySQL 위치 검색 Service 호출하지 않음
```

이를 통해 동일한 API에서 성능 측정 대상 엔진을 명확하게 전환할 수 있도록 했다.

### 20.5 Elasticsearch Service 단위 테스트

다음 항목을 검증했다.

* latitude 검증
* longitude 검증
* radius 검증
* limit 검증
* keyword trim
* 빈 keyword 정규화
* Elasticsearch 검색 결과 → API DTO 변환

### 20.6 재색인 Service 단위 테스트

다음 항목을 검증했다.

* 기존 인덱스 삭제
* 신규 인덱스 생성
* Keyset Pagination
* 다음 배치 ID 전달
* Bulk 저장
* 마지막 refresh
* 인덱스 삭제 실패
* 인덱스 생성 실패

### 20.7 실제 MySQL + Elasticsearch 통합 테스트

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

### 20.8 MySQL·Elasticsearch geo-only 결과 동등성 검증

성능 비교가 서로 다른 검색 결과를 대상으로 수행되는 문제를 방지하기 위해, MySQL과 Elasticsearch를 한
Testcontainers 통합 테스트에서 함께 실행하고 결과 동등성을 검증한다. 동일한 MySQL fixture를 Elasticsearch에
전체 재색인한 뒤 다음 조건을 두 엔진에 동일하게 전달한다.

```text
keyword 없음
동일 latitude / longitude
동일 radiusKm
동일 limit
```

fixture에는 반경 내부의 서로 다른 거리인 ACTIVE 숙소, 반경 밖 숙소, 좌표 없는 숙소와 INACTIVE 숙소를
포함한다. 두 검색 결과에서 `accommodationId` 목록을 추출하여 포함 대상뿐 아니라 distance ASC 순서와 limit
적용 결과까지 직접 비교한다. MySQL과 Elasticsearch의 거리 계산값에는 미세한 차이가 있을 수 있으므로
`distanceKm`의 완전 일치는 parity 조건으로 사용하지 않는다.

이 검증은 검색어 의미 차이가 개입하지 않는 geo-only 조건을 대상으로 한다. MySQL keyword 검색은 `LIKE`
substring 방식이고 Elasticsearch는 analyzer와 `multi_match`를 사용하므로 keyword 결과의 완전한 동등성을
보장하지 않는다. 따라서 geo-only 성능 수치가 가장 직접적인 동일 workload 비교이며, geo-keyword 성능 수치는
각 검색 엔진의 검색 semantics를 포함한 비교로 해석해야 한다.

이번 parity 테스트 추가 과정에서는 기존 k6 성능 측정을 다시 실행하지 않았고 공식 측정 결과도 변경하지
않았다. 성능 수치만으로 운영 엔진을 결정한 것이 아니며 production 검색 엔진은 계속 MySQL Bounding Box를
사용한다.

### 20.9 실행 결과

이번 Bounding Box 고도화 과정에서 다음 테스트를 각각 실행해 정상 통과를 확인했다.

```text
AccommodationLocationBoundingBoxTest
→ BUILD SUCCESSFUL

AccommodationLocationSearchServiceTest
→ BUILD SUCCESSFUL

AccommodationLocationSearchRepositoryIntegrationTest
→ BUILD SUCCESSFUL

AccommodationFacadeTest
→ BUILD SUCCESSFUL
```

---

## 21. 성능 테스트 재현 준비

### 21.1 인프라 실행

```bash
docker compose up -d
```

MySQL, Redis, Elasticsearch가 정상 실행되는지 확인한다.

```bash
docker compose ps
```

현재 로컬 MySQL은 호스트 `3307` 포트를 사용한다.

### 21.2 위치 검색 데이터 생성

```bash
bash performance/scripts/setup-accommodation-location-search-data.sh
```

생성 결과에서 다음 값을 확인한다.

```text
total_count=50000
keyword_match_count=10000
```

스크립트는 위치 검색 성능 데이터만 교체하고 기존 일반 숙소 데이터는 삭제하지 않는다.

### 21.3 Elasticsearch 전체 재색인

Elasticsearch 측정 전에 성능 테스트 숙소를 검색 인덱스에 반영한다.

```bash
ACCOMMODATION_SEARCH_REINDEX_ENABLED=true \
./gradlew bootRun
```

다음 로그를 확인한다.

```text
숙소 검색 Elasticsearch 전체 재색인을 완료했습니다.
indexedCount=50000
```

재색인이 완료되면 해당 서버 프로세스를 종료한다.

```text
Ctrl + C
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

### 21.6 측정 대상 서버 실행

MySQL Bounding Box를 측정할 때:

```bash
ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL \
./gradlew bootRun
```

Elasticsearch를 측정할 때:

```bash
ACCOMMODATION_LOCATION_SEARCH_ENGINE=ELASTICSEARCH \
./gradlew bootRun
```

### 21.7 실제 검색 엔진 확인

```bash
curl -s \
  http://localhost:8080/actuator/info
```

MYSQL 모드 예:

```json
{
  "roompick": {
    "search": {
      "location-engine": "MYSQL"
    }
  }
}
```

ELASTICSEARCH 모드 예:

```json
{
  "roompick": {
    "search": {
      "location-engine": "ELASTICSEARCH"
    }
  }
}
```

### 21.8 실제 API 검색 확인

geo-only:

```bash
curl -s \
  "http://localhost:8080/api/v1/accommodations/search?latitude=37.5665&longitude=126.9780&radiusKm=5&limit=5"
```

geo-keyword:

```bash
curl -s \
  "http://localhost:8080/api/v1/accommodations/search?keyword=%EB%A3%B8%ED%94%BD&latitude=37.5665&longitude=126.9780&radiusKm=5&limit=5"
```

성능 측정 명령은 다음 `22. 성능 측정 재현`에서 구분한다.

---

## 22. 성능 측정 재현

공통 측정 스크립트는 다음 파일이다.

```text
performance/scripts/measure-accommodation-location-search.sh
```

위치 검색 API는 동일하게 유지하고, 실행 시 환경변수로 실제 검색 엔진을 선택한다.

```text
ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL
→ MySQL Bounding Box 검색

ACCOMMODATION_LOCATION_SEARCH_ENGINE=ELASTICSEARCH
→ Elasticsearch 검색
```

성능 결과를 구분하는 `SEARCH_ENGINE` 값은 다음과 같다.

```text
mysql
→ 기존 MySQL Baseline 결과 식별용

mysql-bounding-box
→ Bounding Box + 좌표 인덱스 결과

elasticsearch
→ Elasticsearch 결과
```

측정 스크립트는 `/actuator/info`를 확인하여 `SEARCH_ENGINE`에 대응하는 실제 애플리케이션 검색 엔진이 실행 중인지 검증한다.

예를 들어:

```text
SEARCH_ENGINE=mysql-bounding-box
expected application engine=MYSQL

실제 /actuator/info
location-engine=ELASTICSEARCH

→ 측정 중단
```

따라서 결과 파일명만 MySQL이고 실제 요청은 Elasticsearch로 처리되는 잘못된 측정을 사전에 차단한다.

### 22.1 기존 MySQL Baseline 결과

기존 MySQL Baseline은 Bounding Box 적용 전 다음 검색 구조에서 측정했다.

```text
MySQL
→ ACTIVE / 좌표 필터
→ ST_Distance_Sphere
→ radius 필터
→ 거리 정렬
→ limit
```

공식 결과는 이미 별도 timestamp 디렉터리에 보존되어 있다.

현재 브랜치의 MySQL 검색 경로에는 Bounding Box와 좌표 인덱스 최적화가 적용되어 있으므로 현재 코드에서 `SEARCH_ENGINE=mysql`만 지정하여 다시 실행하면 기존 Baseline을 재현할 수 없다.

정확한 Baseline 재현이 필요한 경우 **Bounding Box 적용 전 커밋의 검색 구현으로 체크아웃한 뒤** 기존과 동일한 데이터와 부하 조건으로 측정해야 한다.

기존 Baseline 결과는 이후 최적화 결과와 비교하기 위해 수정하거나 덮어쓰지 않는다.

### 22.2 MySQL Bounding Box 서버 실행

Bounding Box + 좌표 인덱스 경로를 측정할 때는 애플리케이션을 MYSQL 모드로 실행한다.

```bash
ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL \
./gradlew bootRun
```

서버 실행 후 다음 명령으로 실제 검색 엔진을 확인할 수 있다.

```bash
curl -s \
  http://localhost:8080/actuator/info
```

예상 응답:

```json
{
  "roompick": {
    "search": {
      "location-engine": "MYSQL"
    }
  }
}
```

### 22.3 MySQL Bounding Box geo-only

MYSQL 모드 서버를 실행한 상태에서 다음과 같이 측정한다.

```bash
SEARCH_ENGINE=mysql-bounding-box \
KEYWORD="" \
LATITUDE=37.5665 \
LONGITUDE=126.9780 \
RADIUS_KM=5 \
LIMIT=20 \
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
RUNS=3 \
./performance/scripts/measure-accommodation-location-search.sh
```

측정 스크립트는 실제 애플리케이션 엔진이 `MYSQL`인지 확인한 뒤 워밍업과 본 측정을 진행한다.

### 22.4 MySQL Bounding Box geo-keyword

```bash
SEARCH_ENGINE=mysql-bounding-box \
KEYWORD="룸픽" \
LATITUDE=37.5665 \
LONGITUDE=126.9780 \
RADIUS_KM=5 \
LIMIT=20 \
VUS=10 \
DURATION=30s \
THINK_TIME=0 \
RUNS=3 \
./performance/scripts/measure-accommodation-location-search.sh
```

### 22.5 Elasticsearch 서버 실행

Elasticsearch 성능을 측정할 때는 애플리케이션을 ELASTICSEARCH 모드로 실행한다.

```bash
ACCOMMODATION_LOCATION_SEARCH_ENGINE=ELASTICSEARCH \
./gradlew bootRun
```

실제 엔진은 동일하게 `/actuator/info`에서 확인한다.

```bash
curl -s \
  http://localhost:8080/actuator/info
```

예상 응답:

```json
{
  "roompick": {
    "search": {
      "location-engine": "ELASTICSEARCH"
    }
  }
}
```

### 22.6 Elasticsearch geo-only

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
./performance/scripts/measure-accommodation-location-search.sh
```

### 22.7 Elasticsearch geo-keyword

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
./performance/scripts/measure-accommodation-location-search.sh
```

각 실행은 timestamp 기반의 새로운 결과 디렉터리를 생성한다.

```text
performance/results/location-search/
YYYYMMDD-HHMMSS-{search-engine}-{scenario}/
```

따라서 기존 공식 결과를 덮어쓰지 않는다.

---

## 23. 원본 결과 파일

이번 비교에서 사용한 공식 원본 측정 결과는 다음 디렉터리에 보관했다.

### 23.1 MySQL Baseline geo-only

```text
performance/results/location-search/
20260810-144307-mysql-geo-only/
```

### 23.2 MySQL Baseline geo-keyword

```text
performance/results/location-search/
20260810-144737-mysql-geo-keyword/
```

### 23.3 MySQL Bounding Box geo-only

```text
performance/results/location-search/
20260812-163539-mysql-bounding-box-geo-only/
```

### 23.4 MySQL Bounding Box geo-keyword

```text
performance/results/location-search/
20260812-164413-mysql-bounding-box-geo-keyword/
```

### 23.5 Elasticsearch geo-only

```text
performance/results/location-search/
20260810-175101-elasticsearch-geo-only/
```

### 23.6 Elasticsearch geo-keyword

```text
performance/results/location-search/
20260810-190138-elasticsearch-geo-keyword/
```

각 정상 측정 결과 디렉터리에는 다음 자료가 포함된다.

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

현재 측정 스크립트로 새로 생성하는 결과의 `conditions.txt`에는 결과 구분값과 실제 애플리케이션이 사용한 위치 검색 엔진도 함께 기록한다.

예:

```text
search_engine=mysql-bounding-box
application_location_engine=MYSQL
scenario=geo-only
```

이를 통해 현재 측정 방식에서는 결과 파일만으로도 실제 사용된 검색 경로를 확인할 수 있다.

기존 공식 결과는 성능 비교의 원본 데이터이므로 이후 테스트에서 수정하거나 덮어쓰지 않는다.


---

## 24. 운영 구조에서 고려할 사항

### 24.1 MySQL은 Source of Truth

숙소 생성·수정·상태 변경의 원본 데이터는 MySQL에 유지한다.

Elasticsearch는 local 성능 비교와 향후 재도입을 위해 보존하는 검색용 Read Model이다.

```text
Write
→ MySQL

Production Search
→ MySQL Bounding Box

Local Elasticsearch Experiment
→ Elasticsearch Read Model
```

Elasticsearch 장애 또는 데이터 유실 시 MySQL을 기준으로 검색 인덱스를 다시 생성할 수 있어야 한다.

현재 production은 별도 검색 인프라가 필요하지 않은 MySQL Bounding Box 검색 경로를 사용한다.

### 24.2 실시간 Elasticsearch 인덱스 동기화

이번 작업에서는 전체 재색인 경로를 구현했지만 숙소 생성·좌표 변경·비활성화 시 Elasticsearch Document를 실시간으로 동기화하는 운영 흐름은 아직 구현하지 않았다.

향후 Elasticsearch를 production 검색 엔진으로 재도입하려면 다음 흐름이 필요하다.

```text
숙소 생성 / 수정 / 좌표 변경 / 비활성화
→ MySQL Transaction Commit
→ Elasticsearch Document 동기화
```

MySQL 트랜잭션과 Elasticsearch 작업은 하나의 DB 트랜잭션으로 묶을 수 없으므로 다음을 별도로 설계해야 한다.

* 동기화 실패 처리
* 재시도
* 누락 데이터 탐지
* 재동기화
* 전체 재색인 복구 경로

현재 production 검색 엔진은 MySQL이므로 이 실시간 동기화는 구현하지 않는다. 향후 Elasticsearch를 운영
경로로 재도입할 경우에는 **검색 성능뿐 아니라 데이터 일관성 운영 구조가 필수**다.

### 24.3 전체 재색인과 인덱스 버전

현재 검색 인덱스명은 다음과 같다.

```text
roompick-accommodations-v1
```

현재 전체 재색인은 기존 인덱스를 삭제하고 다시 생성하는 로컬 검증용 구조다.

향후 검색 매핑이 크게 변경되면 다음처럼 새 버전 인덱스를 생성할 수 있다.

```text
roompick-accommodations-v1
→
roompick-accommodations-v2
```

운영 환경에서는 alias 기반 무중단 전환이나 새 인덱스 생성 후 전환 방식 등을 별도 과제로 검토할 수 있다.

이번 고도화 범위에는 운영용 다중 노드 Elasticsearch 클러스터와 무중단 alias 재색인까지 포함하지 않았다.

### 24.4 MySQL 좌표 인덱스 운영

현재 MySQL Bounding Box 검색은 다음 복합 인덱스를 사용한다.

```text
idx_accommodations_latitude_longitude
(latitude, longitude)
```

기존 성능 측정 데이터에서는 MySQL 옵티마이저가 해당 인덱스를 자동 선택하지 않아 당시 Repository에서
`FORCE INDEX`를 사용했다. 현재 production 쿼리는 운영 데이터의 분포와 검색 반경 변화에 대응할 수 있도록
힌트를 사용하지 않으며, 인덱스 선택은 옵티마이저에 맡긴다.

하지만 실제 운영 데이터에서는 다음 요소가 달라질 수 있다.

* 전체 숙소 수
* ACTIVE / INACTIVE 비율
* 지역별 좌표 밀도
* 검색 반경
* keyword 선택도
* 조회 컬럼 수

따라서 운영 데이터가 쌓이면 다음을 다시 확인해야 한다.

```text
ANALYZE TABLE
→ EXPLAIN ANALYZE
→ 실제 처리 행 수
→ Index Range Scan / Table Scan 비교
→ 인덱스 힌트 필요 여부 재판단
```

### 24.5 검색 부하 분리

MySQL Bounding Box 구조는 다음과 같다.

```text
MySQL
├─ 숙소 관리
├─ 예약
├─ 결제
└─ 위치 검색
```

구조가 단순하고 추가 동기화가 없다는 장점이 있지만 검색 트래픽이 MySQL 리소스를 함께 사용한다.

Elasticsearch 구조는 다음과 같이 읽기 검색 부하를 분리한다.

```text
MySQL
├─ 숙소 원본
├─ 예약
└─ 결제

Elasticsearch
└─ 숙소 검색
```

검색 트래픽이 커질수록 이 분리의 가치가 커질 수 있다.

### 24.6 운영 복잡도와 인프라 비용

Elasticsearch를 추가하면 다음 운영 대상이 늘어난다.

* Elasticsearch 프로세스 또는 노드
* CPU / Memory
* 검색 인덱스 저장 공간
* 모니터링
* 장애 대응
* 백업 및 재색인
* MySQL과 Elasticsearch 동기화
* 운영 인프라 비용

반면 MySQL Bounding Box는 기존 DB만 사용하므로 구조가 단순하다.

따라서 기술 선택은 다음 두 축을 함께 봐야 한다.

```text
검색 성능 / 검색 기능 확장성

vs

운영 복잡도 / 인프라 비용
```

이번 로컬 성능 수치만으로 운영 비용을 정량화하지는 않았다.

### 24.7 한국어 검색 품질

이번 keyword 검색은 위치 검색 성능 비교에 초점을 맞췄다.

다음 검색 품질 기능은 이번 범위에서 제외했다.

* 한국어 형태소 분석기
* 자동완성
* 초성 검색
* 오타 보정
* 검색어 추천
* 동의어
* 검색 점수 튜닝

이 항목들은 별도 검색 품질 고도화 과제로 진행할 수 있다.

### 24.8 장소 검색 / Geocoding

이번 API는 사용자가 위도와 경도를 직접 입력해야 한다는 UX를 의미하지 않는다.

현재 서비스에서는 다음처럼 장소명으로 후보 좌표를 조회한 뒤 선택된 좌표를 숙소 검색 API에 전달한다.

```text
"서울역" 입력
→ GET /api/v1/places/search?query=서울역&limit=5
→ Kakao Local API
→ 장소 후보 목록
→ 사용자가 후보 선택
→ 선택한 latitude / longitude
→ GET /api/v1/accommodations/search
→ MySQL Bounding Box 또는 Elasticsearch 위치 검색
```

장소 검색 API와 숙소 위치 검색 API는 별도 요청으로 유지한다. 따라서 사용자가 후보를 선택하기 전에
숙소 DB나 Elasticsearch를 조회하지 않으며, 장소 후보가 여러 개인 경우 서버가 임의로 하나를 선택하지 않는다.

이 절의 Kakao 연계는 현재 완료된 사용자 입력 흐름을 설명한다. 다만 Kakao 응답시간은 이 문서의
MySQL·Elasticsearch 성능 비교에 포함되지 않으므로, 아래 측정 수치를 장소 검색부터 숙소 검색까지의
end-to-end 응답시간으로 해석하면 안 된다.

---

## 25. 테스트 한계

이번 테스트에는 다음 한계가 있다.

* 애플리케이션과 k6가 동일한 로컬 macOS 장비에서 실행됐다.
* MySQL과 Elasticsearch 모두 로컬 Docker 환경이다.
* 실제 운영 네트워크 지연이 포함되지 않았다.
* Kakao Local API 호출 시간과 네트워크 지연은 포함되지 않았다. 모든 검색 엔진 비교는 동일한 `latitude / longitude` 입력에서 시작했다.
* 단일 애플리케이션 인스턴스에서 측정했다.
* 10 VU, 30초의 비교적 짧은 부하 테스트다.
* JVM GC와 JIT 상태가 실행마다 완전히 동일하지 않을 수 있다.
* Elasticsearch는 단일 노드 구성이다.
* Elasticsearch 운영 클러스터의 replica 및 shard 분산 효과가 포함되지 않았다.
* MySQL Baseline / Elasticsearch 공식 측정은 2026-08-10, Bounding Box 공식 측정은 2026-08-12에 수행되어 실행 시점이 완전히 같지는 않다.
* 성능 테스트 데이터는 ACTIVE 숙소 50,000건을 서울 권역의 결정적인 격자 형태로 생성한 데이터다.
* 데이터 생성 스크립트가 기존 일반 숙소를 보존하므로 `EXPLAIN ANALYZE` 당시 실제 스캔 행은 50,040건이었다.
* geo-keyword 데이터는 생성한 50,000건 중 20%인 10,000건에 `룸픽`이 포함된 인위적인 분포다.
* MySQL Bounding Box 성능은 이번 데이터의 지역 분포와 반경 5km 조건에서 측정한 결과다.
* `(latitude, longitude)` B-Tree 복합 인덱스의 효율은 실제 운영 데이터의 위치 분포와 검색 반경에 따라 달라질 수 있다.
* 기존 측정 데이터에서는 MySQL 옵티마이저가 좌표 인덱스를 자동 선택하지 않아 당시 위치 검색 쿼리에서 인덱스를 명시적으로 사용했다.
* 현재 production 쿼리는 `FORCE INDEX`를 제거했으며, 기존 측정 결과는 당시 조건의 상대 비교 결과로 보존한다.
* 운영 데이터 규모와 분포가 달라지면 반드시 `ANALYZE TABLE`과 `EXPLAIN ANALYZE`를 다시 확인해야 한다.
* MySQL Spatial Index 또는 다른 공간 검색 자료구조와의 성능 비교는 이번 범위에 포함하지 않았다.
* `Com_select`는 API 단위 카운터가 아닌 MySQL 서버의 글로벌 누적 카운터이므로 다른 조회가 함께 포함될 수 있다.
* Elasticsearch 검색 Document의 실시간 동기화 구조는 문서 작성 시점에는 아직 구현되지 않았다.
* Elasticsearch 운영 클러스터 구축 비용과 실제 클라우드 인프라 비용을 정량적으로 측정하지 않았다.
* 한국어 형태소 분석기, 자동완성, 오타 보정 등 검색 품질 기능은 이번 측정 범위에 포함하지 않았다.
* 장소명 → 후보 좌표 변환은 별도 API로 구현되어 있으나, 이 성능 측정의 범위에는 포함하지 않았다.

따라서 이번 결과를 다음과 같이 일반화해서는 안 된다.

```text
Elasticsearch는 항상 MySQL보다 일정 배수만큼 빠르다.

또는

Bounding Box + FORCE INDEX는
모든 MySQL 위치 검색에서 항상 최적이다.
```

이번 결과의 정확한 해석은 다음과 같다.

```text
동일한 RoomPick 로컬 환경,
성능 테스트용 ACTIVE 숙소 50,000건,
서울시청 중심 5km,
limit=20,
10 VU,
30초 조건에서

1. MySQL ST_Distance_Sphere Baseline

2. MySQL Bounding Box
   + latitude / longitude 복합 인덱스
   + ST_Distance_Sphere 정확 거리 검증

3. Elasticsearch geo_distance

세 구조를 비교했을 때

MySQL 자체 최적화만으로도
응답시간과 처리량이 크게 개선됐고,

Elasticsearch 검색 경로에서는
그보다 추가적인 성능 개선이 확인됐다.
```

따라서 최종 기술 선택은 이번 로컬 성능 수치뿐 아니라 검색 기능 확장성, 데이터 동기화, 운영 복잡도, 장애 대응, 인프라 비용을 함께 고려해 판단한다.

---

## 26. 전체 결과 요약

동일한 50,000건 데이터와 동일한 부하 조건에서 MySQL Baseline, MySQL Bounding Box + 좌표 인덱스, Elasticsearch를 비교한 결과는 다음과 같다.

| 시나리오        | 검색 방식                |       평균 |      p50 |        p95 |        p99 |          처리량 | 오류율 |
| ----------- | -------------------- | -------: | -------: | ---------: | ---------: | -----------: | --: |
| geo-only    | MySQL Baseline       | 573.62ms | 508.77ms | 1,133.05ms | 1,438.76ms |    17.75 RPS |  0% |
| geo-only    | Bounding Box + Index |  91.69ms |  90.98ms |   123.69ms |   180.91ms |   109.04 RPS |  0% |
| geo-only    | Elasticsearch        |   6.12ms |   5.35ms |    10.78ms |    20.64ms | 1,586.91 RPS |  0% |
| geo-keyword | MySQL Baseline       | 308.28ms | 292.67ms |   432.05ms |   564.07ms |    32.53 RPS |  0% |
| geo-keyword | Bounding Box + Index |  83.59ms |  82.31ms |   114.68ms |   174.79ms |   119.79 RPS |  0% |
| geo-keyword | Elasticsearch        |   5.26ms |   4.69ms |     8.61ms |    17.00ms | 1,834.14 RPS |  0% |

전체 흐름을 단순화하면 다음과 같다.

```text
MySQL Baseline
전체 후보에 ST_Distance_Sphere 적용

        ↓ Bounding Box + 좌표 인덱스

MySQL 최적화
정확한 거리 계산 전에 후보군 축소

        ↓ 검색 전용 Read Model

Elasticsearch
geo_distance 기반 검색
```

geo-only 기준 평균 응답시간은:

```text
573.62ms
→
91.69ms
→
6.12ms
```

geo-keyword 기준 평균 응답시간은:

```text
308.28ms
→
83.59ms
→
5.26ms
```

따라서 이번 테스트에서는 MySQL 자체 최적화만으로도 큰 성능 개선이 가능했고, 그 이후 Elasticsearch로 검색 경로를 분리했을 때 추가적인 성능 향상이 확인됐다.

---

## 27. 결론

### 27.1 MySQL 자체 최적화 효과

기존 MySQL 위치 검색은 정확한 거리 계산 전에 별도의 후보군 선필터링이 없었다.

```text
API 요청
→ MySQL
→ ST_Distance_Sphere
→ 반경 필터
→ 거리 정렬
```

50,000건 성능 테스트 데이터에서 geo-only 평균 응답시간은 573.62ms였다.

이에 Bounding Box와 `(latitude, longitude)` 복합 인덱스를 적용했다.

```text
API 요청
→ Bounding Box 계산
→ latitude / longitude 범위 검색
→ 좌표 인덱스
→ ST_Distance_Sphere 정확 거리 검증
→ 거리 정렬
```

그 결과 geo-only 평균 응답시간은:

```text
573.62ms
→
91.69ms
```

약 84.01% 감소했다.

처리량은:

```text
17.75 RPS
→
109.04 RPS
```

약 6.14배 증가했다.

geo-keyword에서도 평균 응답시간은:

```text
308.28ms
→
83.59ms
```

약 72.89% 감소했고, 처리량은:

```text
32.53 RPS
→
119.79 RPS
```

약 3.68배 증가했다.

따라서 위치 검색 성능 문제를 해결하기 위해 처음부터 별도 검색 엔진을 도입해야 하는 것은 아니며, **기존 MySQL 쿼리에서 정확한 거리 계산 대상 자체를 줄이는 것만으로도 상당한 개선이 가능함을 확인했다.**

### 27.2 Bounding Box 적용 과정에서 확인한 실행계획

Bounding Box 조건만 추가했을 때는 전체 50,040건 중 실제 Bounding Box 후보가 6,384건으로 줄었지만 좌표 인덱스가 없어 여전히 전체 테이블 스캔이 발생했다.

```text
전체 조회 대상
50,040건

↓ Bounding Box

후보
6,384건

↓ 정확한 거리 검증

5km 반경 내부
5,018건
```

즉 Bounding Box는 후보군을 줄일 수 있었지만, 해당 후보를 찾기 위해 전체 테이블을 읽는 구조에서는 성능 개선이 제한적이었다.

이후 인덱스를 추가하여 다음 두 구조를 비교했다.

```text
(status, latitude, longitude)

vs

(latitude, longitude)
```

성능 테스트 데이터에서는 대부분의 숙소가 `ACTIVE` 상태였기 때문에 `status`의 선택도가 낮았다.

단일 `EXPLAIN ANALYZE` 비교에서는 `(latitude, longitude)` 인덱스를 사용했을 때 더 좋은 결과가 나타났다.

```text
기존 MySQL Table Scan
약 96.9ms

Bounding Box + (status, latitude, longitude)
Index Range Scan
약 45.1ms

Bounding Box + (latitude, longitude)
Index Range Scan
약 37.9ms
```

이에 최종적으로 `(latitude, longitude)` 복합 인덱스를 사용했다.

기존 측정 데이터 분포에서는 MySQL 옵티마이저가 해당 인덱스를 자동 선택하지 않아 당시 위치 검색
Repository에서 명시적으로 인덱스를 사용했다. 현재 production 쿼리에서는 데이터 분포, 반경과 keyword
선택도 변화에 따라 옵티마이저가 더 적절한 계획을 고를 수 있도록 `FORCE INDEX`를 제거했다.

다만 실행계획은 데이터 규모와 분포에 따라 달라질 수 있으므로 운영 환경에서는 실제 데이터 기준으로 다시 검증할 필요가 있다.

### 27.3 Elasticsearch의 추가 성능 효과

MySQL을 Bounding Box와 좌표 인덱스로 최적화한 이후에도 Elasticsearch가 더 낮은 응답시간을 기록했다.

geo-only 평균 응답시간:

```text
MySQL Baseline
573.62ms

↓ MySQL 최적화

Bounding Box + Index
91.69ms

↓ Elasticsearch

6.12ms
```

Bounding Box 대비 Elasticsearch는 평균 응답시간이 약 93.33% 추가 감소했다.

처리량은:

```text
109.04 RPS
→
1,586.91 RPS
```

약 14.55배 증가했다.

geo-keyword 평균 응답시간도:

```text
MySQL Baseline
308.28ms

↓ MySQL 최적화

Bounding Box + Index
83.59ms

↓ Elasticsearch

5.26ms
```

으로 감소했다.

Bounding Box 대비 약 93.71% 추가 감소했고, 처리량은:

```text
119.79 RPS
→
1,834.14 RPS
```

약 15.31배 증가했다.

이는 이번 RoomPick 로컬 환경과 테스트 조건에서 관측된 결과이며, Elasticsearch가 모든 환경에서 동일한 배수로 빠르다는 의미는 아니다.

### 27.4 Tail Latency

Bounding Box 최적화는 평균 응답시간뿐 아니라 느린 요청의 지연시간도 크게 줄였다.

geo-only p95:

```text
1,133.05ms
→
123.69ms
→
10.78ms
```

geo-only p99:

```text
1,438.76ms
→
180.91ms
→
20.64ms
```

geo-keyword p95:

```text
432.05ms
→
114.68ms
→
8.61ms
```

geo-keyword p99:

```text
564.07ms
→
174.79ms
→
17.00ms
```

따라서 MySQL 자체 최적화에서도 Tail Latency가 크게 개선됐고, Elasticsearch에서는 그보다 추가적으로 낮은 지연시간이 확인됐다.

### 27.5 DB 리소스와 운영 복잡도

MySQL Bounding Box 검색은 별도의 검색 시스템 없이 기존 MySQL에서 처리할 수 있다는 장점이 있다.

```text
MySQL
├─ 숙소 원본 데이터
└─ 위치 검색
```

또한 검색 데이터 동기화나 별도 검색 클러스터 운영이 필요하지 않는다.

반면 Elasticsearch를 적용하면 검색 트래픽을 MySQL의 트랜잭션 처리와 분리할 수 있다.

```text
MySQL
├─ 숙소
├─ 예약
└─ 결제

Elasticsearch
└─ 숙소 검색
```

하지만 다음과 같은 추가 운영 요소가 발생한다.

* Elasticsearch 인프라 운영
* MySQL과 Elasticsearch 데이터 동기화
* 재색인
* 검색 인덱스 저장 공간
* 장애 감지 및 복구
* 모니터링 대상 증가
* 추가 인프라 비용 가능성

따라서 **가장 빠른 검색 방식이 항상 가장 적절한 기술 선택을 의미하지는 않는다.**

### 27.6 최종 기술 선택

이번 고도화에서는 다음 세 단계를 순차적으로 비교했다.

```text
1. MySQL ST_Distance_Sphere Baseline

2. MySQL Bounding Box
   + latitude / longitude 복합 인덱스
   + 정확 거리 검증

3. Elasticsearch geo_distance
```

비교 결과 MySQL 자체에서도 Bounding Box와 인덱스를 이용해 큰 폭의 성능 개선이 가능했고, 현재 RoomPick
규모에서 필요한 응답 성능을 충분히 확보했다고 판단했다.

따라서 단순한 반경 검색만 필요하고 별도 검색 인프라 운영 비용을 최소화하는 것이 중요하다면:

```text
MySQL
+
Bounding Box
+
좌표 인덱스
```

구조를 production 검색 엔진으로 최종 선택했다.

RoomPick은 현재 숙소명·주소 keyword 검색과 Kakao 장소 검색 연계를 제공한다. 다음 검색 품질·운영 기능은
여전히 별도 고도화 범위다.

* 검색 분석기
* 한국어 검색 품질 개선
* 자동완성
* 검색 트래픽 증가 대응

또한 이번 동일 조건 테스트에서 Bounding Box로 최적화한 MySQL보다 Elasticsearch가 더 낮은 응답시간과 높은 처리량을 기록했다.

Elasticsearch의 측정 성능이 가장 빨랐지만, 추가 검색 노드 인프라, MySQL과 Elasticsearch 사이의 데이터
동기화, 장애 지점 증가, 모니터링과 운영 복잡도를 함께 고려했다. 따라서 RoomPick의 최종 방향은 다음과 같이
정리한다.

```text
Source of Truth
→ MySQL

현재 production 위치 검색
→ MySQL Bounding Box + 좌표 인덱스

local 성능 비교 및 향후 재도입 후보
→ Elasticsearch Read Model
```

Elasticsearch 구현과 기존 성능 측정 결과는 삭제하지 않는다. 검색 트래픽 증가나 검색 기능 고도화로 재도입할
때 활용하되, 숙소 생성·수정·비활성화 시 검색 Document를 안정적으로 동기화하는 구조와 장애 대응 정책을
먼저 마련해야 한다. 현재는 production 검색 엔진으로 사용하지 않으므로 실시간 동기화를 구현하지 않는다.

이번 테스트를 통해 단순히 `MySQL보다 Elasticsearch가 빠르다`는 결론이 아니라,

```text
기존 MySQL의 병목 확인
→ Bounding Box로 후보군 축소
→ 인덱스 실행계획 검증
→ MySQL 자체 성능 개선
→ Elasticsearch와 재비교
→ 성능과 운영 비용을 함께 고려한 기술 선택
```

과정을 통해 위치 기반 숙소 검색 구조를 단계적으로 검증했다.
