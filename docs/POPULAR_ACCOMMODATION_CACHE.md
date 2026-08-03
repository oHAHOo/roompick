# 인기 숙소 Redis 캐시 정책

## 1. 목적

인기 숙소 조회 결과에 Redis Cache-Aside 전략을 적용하여
동일 요청이 반복될 때 Redis 인기 랭킹 조회와 DB 조회 횟수를 줄입니다.

또한 Redis 장애가 발생하더라도 숙소 상세 조회와 인기 숙소 조회 API가
전체 장애로 이어지지 않도록 장애 대응 정책을 적용합니다.

---

## 2. 캐시 기본 정책

- 캐시 이름: `popularAccommodations`
- 캐시 저장소: Redis
- 기본 TTL: 60초
- 저장 대상: 인기 숙소 조회 응답 DTO 목록
- 저장하지 않는 대상: JPA Entity
- null 결과: 캐시하지 않음
- 지원 기간: `DAILY`, `WEEKLY`
- `period` 생략 시 기본값: `DAILY`

캐시 TTL은 다음 설정으로 변경할 수 있습니다.

```yaml
roompick:
  cache:
    popular-accommodations-ttl: 60s
```

별도의 설정이 없으면 기본값으로 60초를 사용합니다.

통합 테스트에서는 긴 대기 없이 만료 동작을 검증하기 위해
테스트 환경에서만 TTL을 `1s`로 재정의합니다.

---

## 3. 캐시 Key

캐시 Key에는 다음 값이 포함됩니다.

- 기간과 인기 랭킹 기준 날짜
- 요청한 조회 개수 `limit`

예시:

```text
popularAccommodations::roompick:popular:accommodations:daily:2026-08-03:10
popularAccommodations::roompick:popular:accommodations:weekly:2026-08-03:10
```

기간, 날짜 또는 `limit`이 다르면 서로 다른 캐시 데이터로 관리됩니다.

`DAILY`는 Asia/Seoul의 현재 날짜를 사용합니다. `WEEKLY`는 최근 7일
이동 구간이 아니라 월요일 00:00부터 다음 월요일 직전까지의 캘린더 주이며,
해당 주 월요일 날짜를 기준 날짜로 사용합니다.

```text
roompick:popular:accommodations:daily:2026-08-03
roompick:popular:accommodations:weekly:2026-08-03
```

이를 통해 다른 날짜의 인기 숙소 결과나
서로 다른 조회 개수의 결과가 섞이지 않도록 합니다.

---

## 4. Cache-Aside 조회 흐름

```text
인기 숙소 조회 요청
→ Redis 캐시 조회
→ 캐시 HIT
    → 저장된 인기 숙소 응답 DTO 목록 반환
→ 캐시 MISS
    → Redis 인기 랭킹 첫 구간 조회
    → 해당 구간의 ACTIVE 숙소 공개 정보 DB 조회
    → Redis 인기 순서대로 응답 조합
    → limit이 부족하면 다음 Redis 구간 조회
    → limit 충족 또는 Redis 랭킹 끝에서 종료
    → Redis 캐시에 응답 DTO 목록 저장
    → 응답 반환
```

동일한 날짜와 `limit` 요청은 두 번째 호출부터
Redis 캐시에 저장된 결과를 사용합니다.

캐시 HIT 시 다음 내부 작업은 실행하지 않습니다.

- Redis 인기 랭킹 조회
- 숙소 공개 정보 DB 조회
- 인기 순서 재조합

TTL이 만료되면 다음 요청에서
최신 Redis 랭킹과 ACTIVE 숙소 정보를 다시 조회합니다.

---

## 5. 캐시 저장 데이터

인기 숙소 캐시에는 JPA Entity를 저장하지 않습니다.

다음과 같은 조회 전용 응답 DTO 목록만 저장합니다.

```text
List<PopularAccommodationResponseDto>
```

Entity를 직접 캐시하지 않으므로 다음 문제를 방지합니다.

- 영속성 컨텍스트와 캐시 데이터의 상태 불일치
- 지연 로딩 프록시 직렬화 문제
- Entity 구조 변경에 따른 캐시 결합도 증가
- 불필요한 내부 필드 노출

Redis Value는 JSON 형식으로 직렬화합니다.

Redis Key는 문자열 형식으로 직렬화합니다.

---

## 6. Redis 인기 순서 조합

Redis Sorted Set 전체를 한 번에 조회하지 않고
`limit × 5` 크기의 구간으로 나누어 조회합니다.

`limit`의 최대값은 20이므로 한 번의 Redis 조회에서
가져오는 후보는 최대 100개입니다.

Redis `reverseRange`의 종료 인덱스는 inclusive이므로
각 구간은 다음과 같이 계산합니다.

```text
batchSize = limit × 5

첫 구간: start=0, end=batchSize-1
다음 구간: start=batchSize, end=(batchSize×2)-1
```

각 구간에서 조회한 숙소 ID 순서를
최종 인기 숙소 응답 순서로 사용합니다.

DB에서는 현재 Redis 구간에서 조회한 숙소 ID 중
현재 `ACTIVE` 상태인 숙소의 공개 정보만 조회합니다.

DB 반환 순서와 Redis 인기 순서가 다를 수 있으므로
조회 결과를 숙소 ID 기준 Map으로 변환한 뒤
Redis ID 순서대로 다시 조합합니다.

```text
Redis 순위
3번 → 2번 → 1번

DB ACTIVE 조회 결과
1번 → 3번

최종 응답
3번 → 1번
```

삭제되었거나 `INACTIVE` 상태인 숙소는 결과에서 제외합니다.

숙소가 제외된 경우 남은 숙소의 순위는 1부터 다시 계산합니다.

```text
Redis 순위
1위: 3번
2위: 2번
3위: 1번

2번 숙소가 INACTIVE인 경우

최종 응답
1위: 3번
2위: 1번
```

상위 랭킹에 비공개 숙소가 포함되어 있어도
하위 ACTIVE 숙소를 사용해 요청한 `limit`까지 결과를 채웁니다.

현재 구간에서 ACTIVE 숙소가 부족하면 다음 Redis 구간을 조회합니다.
최종 결과가 `limit`을 채우면 이후 Redis와 DB 조회를 즉시 생략하고,
Redis 랭킹 끝에 도달하면 존재하는 ACTIVE 숙소만 반환합니다.

이미 처리한 숙소 ID는 다음 구간에 다시 나타나더라도
DB 조회와 결과 조합에서 제외합니다.

역할은 다음과 같이 분리합니다.

```text
PopularAccommodationRankingRepository
→ Redis Sorted Set의 지정 범위 ID 조회

PopularAccommodationRankingService
→ limit 검증, 기간별 랭킹 Key 생성, Redis 범위 조회 위임

PopularAccommodationQueryService
→ batch 반복, ACTIVE 숙소 조합, 순서 복원, rank와 limit 처리
```

---

## 7. 조회 점수 증가와 캐시 최신성

숙소 상세 조회가 정상적으로 완료되면
해당 숙소의 DAILY와 WEEKLY Redis 인기 점수를 각각 한 번 증가시킵니다.
한 기간의 Redis 기록 실패는 다른 기간의 기록 시도를 막지 않으며,
두 기록 모두 상세 조회 응답과 분리된 best-effort 방식으로 처리합니다.

```text
숙소 상세 DB 조회
→ 상세 응답 DTO 생성
→ Redis 인기 점수 증가
→ 상세 응답 반환
```

조회 점수가 증가할 때마다 인기 숙소 캐시를 삭제하지는 않습니다.

매 조회마다 캐시를 삭제하면 다음 문제가 발생합니다.

- 캐시 HIT 비율 감소
- 반복 요청마다 DB 조회 발생
- Redis 캐시 적용 효과 감소
- 인기 숙소 API 부하 증가

인기 점수 변화는 최대 60초 뒤
캐시 TTL이 만료된 후 새 인기 숙소 결과에 반영됩니다.

즉, 인기 점수 증가에 대해서는 TTL을 통해
최신성과 조회 성능의 균형을 유지합니다.

---

## 8. 숙소 정보 변경 시 캐시 무효화

다음 변경이 정상적으로 완료되면
인기 숙소 캐시 전체 삭제를 요청합니다.

- 숙소 공개 정보 수정
- 숙소 상태를 `INACTIVE`로 변경

기간, 날짜와 `limit`별로 여러 캐시 Key가 생성될 수 있으므로
`popularAccommodations` 캐시의 전체 항목을 삭제합니다.

따라서 전체 삭제 한 번으로 DAILY와 WEEKLY 응답 캐시가 모두 제거됩니다.

```text
popularAccommodations::...:1
popularAccommodations::...:5
popularAccommodations::...:10
popularAccommodations::...:20
```

특정 Key 하나만 삭제하면 다른 `limit` 캐시에
이전 숙소 정보가 남을 수 있으므로 전체 삭제를 사용합니다.

캐시 삭제는 다음 전용 Service가 담당합니다.

```text
PopularAccommodationCacheEvictionService.evictAll()
```

---

## 9. 트랜잭션과 캐시 삭제 시점

Redis CacheManager는 트랜잭션 연동 방식으로 설정합니다.

```text
RedisCacheManager.transactionAware()
```

숙소 정보 변경 트랜잭션 안에서 캐시 삭제를 요청하더라도
실제 캐시 삭제는 트랜잭션 완료 시점에 처리합니다.

DB 커밋 전에 캐시를 먼저 삭제하면 다른 요청이 아직 커밋되지 않은
이전 DB 데이터로 캐시를 다시 생성할 수 있으므로 커밋 이후에 삭제합니다.

### 트랜잭션 커밋

```text
숙소 정보 변경
→ 캐시 삭제 요청
→ DB 트랜잭션 커밋
→ 인기 숙소 캐시 삭제
```

### 트랜잭션 롤백

```text
숙소 정보 변경
→ 캐시 삭제 요청
→ DB 트랜잭션 롤백
→ 인기 숙소 캐시 유지
```

DB 변경이 롤백됐는데 캐시만 삭제되는 상황을 방지하여
DB 데이터와 캐시의 처리 시점을 일치시킵니다.

현재 관리자 숙소 API에는 숙소 등록 기능만 존재합니다.

숙소 공개 정보 수정 및 비공개 전환 API가 추가되면
다음 Service 메서드를 호출해야 합니다.

```text
AccommodationService.updatePublicInformation()
AccommodationService.inactivateAccommodation()
```

두 메서드는 숙소 Entity 변경 후
인기 숙소 캐시 삭제를 요청하도록 구현되어 있습니다.

---

## 10. Redis 캐시 오류 처리

Redis 캐시 작업 중 오류가 발생해도
원래 비즈니스 로직을 계속 실행합니다.

다음 캐시 작업의 예외를 로그로 기록하고 외부로 전달하지 않습니다.

- 캐시 조회 실패
- 캐시 저장 실패
- 캐시 단건 삭제 실패
- 캐시 전체 삭제 실패

```text
Redis 캐시 조회 실패
→ 경고 로그 기록
→ 실제 인기 숙소 조회 로직 실행
```

```text
Redis 캐시 저장 실패
→ 경고 로그 기록
→ 조회한 인기 숙소 결과 정상 반환
```

캐시는 성능 최적화 수단이므로
캐시 장애가 사용자 API 전체 장애로 이어지지 않도록 합니다.

### 정상 상황의 캐시 무효화

Redis가 정상이면 숙소 공개 정보 수정 또는 `INACTIVE` 전환의
DB 트랜잭션이 커밋된 직후 인기 숙소 캐시 전체가 삭제됩니다.
다음 인기 숙소 요청은 최신 ACTIVE 숙소를 기준으로 캐시를 재생성합니다.

### 캐시 무효화 실패와 stale 허용 범위

Redis 장애로 커밋 이후 캐시 전체 삭제가 실패해도
CacheErrorHandler는 경고 로그를 남기고 DB 트랜잭션 결과를 되돌리지 않습니다.

이 경우 Redis 연결이 복구된 뒤 기존 캐시가 아직 만료되지 않았다면
변경 전 응답 DTO가 일시적으로 반환될 수 있습니다. 현재 MVP에서는 이를
기존 캐시에 남아 있던 TTL 범위에서 허용합니다.

- stale 응답 허용 범위는 해당 캐시 Key에 남아 있는 TTL까지입니다.
- 운영 설정의 기본 TTL은 60초이며, TTL 설정을 변경하면 최대 stale 허용 범위도 함께 변경됩니다.
- 숙소 변경 시점부터 새로운 TTL이 부여되는 것이 아니라 기존 Key의 남은 TTL만 적용됩니다.
- 캐시 HIT마다 ACTIVE 상태를 DB에서 다시 검증하지 않습니다.

캐시 HIT마다 DB를 조회하면 HIT에서도 Redis 랭킹과 DB 조회를 생략한다는
Cache-Aside 목적이 훼손되므로, 장애 중 짧은 stale 허용을 선택합니다.

이번 MVP에서는 캐시 삭제 재시도나 Redis 복구 감지 기능을 구현하지 않습니다.
운영의 즉시 제거 요구가 강화되면 다음 방식을 검토합니다.

- 캐시 전체 삭제 재시도
- Redis 복구 감지 후 인기 숙소 캐시 전체 삭제
- 이벤트 또는 메시지 기반 보상 처리

---

## 11. 숙소 상세 조회 중 Redis 장애

숙소 상세 조회 과정에서 Redis 인기 점수 기록에 실패해도
숙소 상세 응답은 정상적으로 반환합니다.

```text
숙소 DB 조회 성공
→ 상세 응답 DTO 생성
→ Redis 인기 점수 기록 시도
→ Redis 연결 장애 발생
→ 경고 로그 기록
→ 숙소 상세 응답 정상 반환
```

인기 점수 기록은 부가 기능이므로
숙소 상세 조회 성공 여부에 영향을 주지 않습니다.

Redis 점수 기록 실패 시
다음 형식의 경고 로그를 남깁니다.

```text
인기 숙소 조회 점수 기록에 실패했습니다. accommodationId={숙소 ID}
```

---

## 12. 인기 숙소 조회 중 Redis 장애

Redis 캐시 조회에 실패하면 실제 인기 숙소 조회 로직을 계속 실행합니다.
이후 Redis 인기 랭킹 조회까지 실패한 경우에만
DB에서 최신 ACTIVE 숙소를 조회하여 임시 응답을 반환합니다.

```text
인기 숙소 조회 요청
→ Redis 캐시 조회 실패 또는 캐시 MISS
→ Redis 인기 랭킹 조회 실패
→ DB에서 최신 ACTIVE 숙소 조회
→ 인기 숙소 응답 형식으로 변환
→ API 정상 응답
```

DB fallback은 다음 조건으로 조회합니다.

- 숙소 상태: `ACTIVE`
- 조회 개수: 요청한 `limit`
- 첫 번째 정렬 기준: `createdAt` 내림차순
- 두 번째 정렬 기준: `accommodationId` 내림차순

```text
ORDER BY createdAt DESC, accommodationId DESC
```

생성 시각이 같은 숙소가 여러 개 존재하더라도
ID 내림차순을 추가하여 정렬 결과가 일정하게 유지되도록 합니다.

Redis 장애 시 다음 형식의 경고 로그를 남깁니다.

```text
Redis 인기 숙소 랭킹 조회 실패로 최신 숙소 fallback을 반환합니다. limit={limit}
```

Redis Ranking Repository의 `DataAccessException`은
`PopularAccommodationRankingService`에서
`PopularAccommodationRankingUnavailableException`으로 변환합니다.
Facade는 이 전용 예외만 catch하여 DB fallback을 실행합니다.

ACTIVE 숙소 공개 정보 DB 조회에서 발생한 `DataAccessException`은
Redis 장애로 처리하지 않고 상위로 그대로 전달합니다. 장애가 발생한 DB를
fallback 명목으로 다시 조회하지 않습니다.

---

## 13. fallback 결과 주의사항

DB fallback 결과는 실제 인기 순위가 아닙니다.

Redis 장애 중 사용자 API 응답을 유지하기 위해
최신 ACTIVE 숙소를 임시로 제공하는 장애 대응 결과입니다.

fallback 응답의 `rank` 값은 실제 조회 점수 순위가 아니라
응답 형식을 유지하기 위해 1부터 순서대로 부여한 임시 번호입니다.

```text
최신 ACTIVE 숙소 첫 번째 → 임시 rank 1
최신 ACTIVE 숙소 두 번째 → 임시 rank 2
```

fallback 결과는 인기 숙소 캐시에 저장하지 않습니다.

```text
Redis 랭킹 조회 실패
→ Facade에서 DB fallback 실행
→ 사용자에게 임시 결과 반환
→ popularAccommodations 캐시에는 저장하지 않음
```

Redis 장애가 복구된 뒤 다음 요청에서는
다시 Redis 인기 랭킹 기반 조회를 시도합니다.

---

## 14. 캐시 성능 확인 결과

동일한 인기 숙소 요청을 반복하여
캐시 적용 전후 DB 조회와 응답 시간을 확인했습니다.

### 캐시 적용 전

동일 요청마다 숙소 공개 정보 DB 조회가 실행되었습니다.

```text
동일 요청 1회당 DB SELECT: 1회
안정 구간 평균 응답 시간: 약 14.093ms
```

### 캐시 적용 후

첫 요청은 캐시 MISS로 DB 조회가 실행되고,
이후 동일 요청은 Redis 캐시 HIT로 처리되었습니다.

```text
첫 번째 요청 DB SELECT: 1회
두 번째 이후 동일 요청 DB SELECT: 0회
캐시 HIT 안정 구간 평균 응답 시간: 약 5.308ms
```

### 확인 결과

```text
동일 요청 DB 조회
1회 → 0회

평균 응답 시간
약 14.093ms → 약 5.308ms
```

측정값은 로컬 개발 환경 기준이며
실제 운영 환경에서는 네트워크와 데이터 규모에 따라 달라질 수 있습니다.

---

## 15. 테스트 검증 항목

다음 항목을 단위 테스트와 통합 테스트로 검증합니다.

### 캐시 조회

- 캐시 MISS 후 결과 저장
- 동일 요청의 캐시 HIT
- 캐시 HIT 시 Redis 랭킹 조회 생략
- 캐시 HIT 시 DB 조회 역할의 Service 호출 생략
- 캐시 응답과 최초 조회 응답의 내용 일치

### TTL

- 테스트 전용 TTL 적용
- TTL 만료 전 캐시 존재
- TTL 만료 후 캐시 제거
- TTL 만료 후 내부 조회 재실행
- 만료 전후 응답 내용 일치

### 인기 숙소 조합

- Redis 인기 순서 유지
- DB 반환 순서와 관계없이 Redis 순서로 재조합
- INACTIVE 또는 삭제 숙소 제외
- 제외된 숙소 이후 순위 재계산
- 하위 ACTIVE 숙소로 `limit` 충족
- 인기 랭킹이 없으면 빈 목록 반환

### 캐시 무효화

- 숙소 공개 정보 수정 시 캐시 삭제 요청
- 숙소 비공개 전환 시 캐시 삭제 요청
- 트랜잭션 커밋 후 실제 Redis 캐시 삭제
- 트랜잭션 롤백 시 Redis 캐시 유지
- 롤백 시 숙소 DB 변경사항도 유지되지 않음
- 캐시 전체 삭제 실패를 CacheErrorHandler가 외부로 전달하지 않음
- TTL 만료 후 최신 조회 결과로 캐시 재생성

### Redis 장애

- Redis Ranking Repository 예외를 전용 예외로 변환
- Redis 인기 랭킹 조회 실패 시 DB fallback
- Redis 장애 시 인기 숙소 API 정상 응답
- Redis 인기 점수 기록 실패 시 상세 조회 정상 응답
- fallback 결과를 인기 숙소 캐시에 저장하지 않음
- 숙소 DB `DataAccessException`은 fallback하지 않고 그대로 전달

### DB fallback

- ACTIVE 숙소만 조회
- `createdAt` 내림차순 정렬
- 생성 시각이 같으면 ID 내림차순 정렬
- 요청한 `limit`만큼만 조회
- INACTIVE 숙소 제외
- fallback 임시 순번 생성

### 전체 검증

- 숙소 및 캐시 관련 대상 테스트 성공
- 전체 `./gradlew test` 성공
- `git diff --check` 성공
- 성능 측정용 임시 `show-sql` 설정 제거
