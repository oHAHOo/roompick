# CI 통합 테스트 실행 시간 단축

## 1. 문서 목적

이 문서는 CI test 작업이 24분 넘게 걸리던 이유와, 무엇을 바꿔서 얼마나
줄었는지를 적는다.

- 관련 이슈: `#137`
- 관련 PR: `#139`

## 2. 문제

2026-08-17 develop 브랜치 기준 CI 실행 시간은 다음과 같았다.

```text
test 작업 (24분 52초)
├── 단위 테스트 실행            9분 46초
└── MySQL 통합 테스트 실행       14분 42초  (같은 작업 안에서 순서대로 실행)

flyway-check 작업 (51초, test와 동시 실행)
```

PR 하나를 올릴 때마다 CI가 끝나는 데 25분 가까이 걸렸다.

## 3. 왜 오래 걸렸는가

### 3.1 단위 테스트와 통합 테스트가 순서대로 실행됨

단위 테스트와 통합 테스트는 서로 결과를 주고받지 않는 독립된
작업이다. 그런데 두 작업이 하나의 작업 안에 함께 들어 있어서,
통합 테스트는 단위 테스트가 끝날 때까지 기다린 뒤에 시작했다.

### 3.2 통합 테스트 클래스마다 MySQL·Redis를 새로 띄우고 끔

통합 테스트는 실제로 동작하는 MySQL과 Redis가 있어야 결과를 확인할
수 있다. 그래서 테스트 코드가 Testcontainers라는 도구로 테스트용
MySQL과 Redis를 직접 띄워서 사용하고 있었다.

문제는 이 작업을 테스트 클래스마다 따로 반복했다는 점이다. 테스트
클래스 하나가 실행될 때 MySQL 또는 Redis를 새로 띄우고, 그 클래스의
테스트가 끝나면 다시 껐다. 이 방식으로 MySQL을 11번, Redis를 6번,
합쳐서 17번을 새로 띄우고 껐다. MySQL과 Redis를 새로 띄우고 끄는
과정이 17번 반복된 것이 통합 테스트 시간에 영향을 준 것으로 판단해,
이를 줄이는 방향으로 변경했다. 실제로 얼마나 줄었는지는 5절 측정
결과에 있다.

## 4. 무엇을 바꿨는가

### 4.1 단위 테스트와 통합 테스트를 별개 작업으로 분리

`.github/workflows/ci.yml`의 `test` 작업을 `unit-test`와
`integration-test` 두 개의 작업으로 나눴다. 두 작업은 서로 의존하지
않으므로, GitHub Actions가 두 작업을 동시에 실행한다.

### 4.2 MySQL·Redis를 하나씩만 띄우고 모든 테스트 클래스가 함께 사용

`SharedMySqlTestContainer`, `SharedRedisTestContainer`
(`src/test/java/com/roompick/testsupport`)를 추가했다. 테스트
클래스마다 MySQL과 Redis를 새로 띄우는 대신, MySQL 하나와 Redis
하나만 띄워두고 모든 테스트 클래스가 이 두 개를 함께 사용하도록
바꿨다.

```text
기존: 테스트 클래스마다 MySQL·Redis를 새로 띄우고 끔 (총 17번)
변경: MySQL 하나, Redis 하나만 띄운 채로 계속 사용
```

MySQL을 함께 사용해도 테스트 데이터가 섞이지 않도록, 각 테스트
클래스가 사용하는 데이터베이스 이름은 이전과 동일하게 유지했다.
MySQL 하나 안에는 데이터베이스를 여러 개 만들 수 있으므로, MySQL은
하나만 사용하지만 각 테스트 클래스가 접근하는 데이터베이스는 서로
다르다.

```java
@Tag("integration")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
class ReservationConcurrencyMySqlIntegrationTest {

    private static final String DATABASE_NAME = "roompick_reservation_lock_test";

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        // 이 클래스가 사용할 데이터베이스가 없으면 만든다
        SharedMySqlTestContainer.createDatabaseIfAbsent(DATABASE_NAME);
        // 그 데이터베이스로 접속하는 주소를 만든다
        registry.add("spring.datasource.url", () -> SharedMySqlTestContainer.jdbcUrl(DATABASE_NAME));
        registry.add("spring.datasource.username", () -> SharedMySqlTestContainer.USERNAME);
        registry.add("spring.datasource.password", () -> SharedMySqlTestContainer.PASSWORD);
    }
}
```

Redis는 데이터베이스를 나눠 쓸 필요가 없다. 각 테스트가 자신이 사용한
키를 테스트가 끝날 때 직접 삭제하고 있어서, 접속 주소(호스트와
포트)만 알려주면 된다.

```java
@DynamicPropertySource
static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", SharedRedisTestContainer::host);
    registry.add("spring.data.redis.port", SharedRedisTestContainer::port);
}
```

### 4.3 구현 중 확인한 문제 두 가지

MySQL을 하나만 띄우고 여러 데이터베이스를 새로 만들려면, 접속 계정에
데이터베이스를 새로 만들 수 있는 권한이 있어야 한다. 기본으로
만들어지는 접속 계정은 정해진 데이터베이스 하나에만 권한이 있어서,
MySQL이 켜진 직후 관리자 계정(root)으로 접속해 모든 권한을 부여하는
명령(`GRANT ALL PRIVILEGES`)을 한 번 실행하도록 했다. 이 과정에서
두 가지 문제를 확인하고 고쳤다.

- **root 계정으로 접속이 거부됨**: MySQL은 기본 설정에서 root 계정을
  컨테이너 내부에서만 접속할 수 있게 만든다. 테스트 코드는 컨테이너
  외부(실행 중인 컴퓨터)에서 접속하므로 거부당했다. `MYSQL_ROOT_HOST=%`
  설정을 추가해 컨테이너 외부에서도 root로 접속할 수 있게 했다.
- **root 비밀번호가 다른 값으로 바뀜**: 컨테이너를 만드는 도구
  (Testcontainers)가 애플리케이션 접속 계정의 비밀번호를 root
  비밀번호에도 그대로 적용하는 동작을 가지고 있었다. 그래서 별도로
  지정한 root 비밀번호로는 접속할 수 없었다. root 접속에 애플리케이션
  접속 계정과 같은 비밀번호를 사용하도록 고쳤다.

두 문제 모두 실제로 테스트를 실행했을 때 나온 오류 메시지
(`Access denied for user 'root'@'...'`)를 확인하고 원인을 찾아
수정했다.

## 5. 측정 결과

같은 워크플로 기준, 변경 전(2026-08-17 develop 실행)과 변경 후(PR
#139 CI 실행, 2026-08-19)를 비교했다.

| 항목 | 변경 전 | 변경 후 | 차이 |
| --- | --- | --- | --- |
| **CI 전체 소요 시간** | 24분 52초 | **10분 0초** | **14분 52초 감소 (약 60%)** |
| 단위 테스트 | 9분 46초 | 9분 56초 | 10초 증가 (변화 없음 수준) |
| 통합 테스트 | 14분 42초 | 8분 27초 | 6분 15초 감소 (약 43%) |
| flyway-check | 51초 | 46초 | 5초 감소 (변화 없음 수준) |

변경 후 `unit-test`, `integration-test`, `flyway-check` 세 작업이
모두 같은 시각에 시작한 것을 로그에서 확인했다. 세 작업이 실제로
동시에 실행된다는 뜻이다.

전체 시간이 감소한 이유는 두 가지다.

1. 통합 테스트 자체 시간이 6분 15초 줄었다 — MySQL·Redis를 새로 띄우고
   끄는 횟수가 17번에서 2번(각 1개씩)으로 줄어든 결과다.
2. 단위 테스트와 통합 테스트가 더 이상 순서대로 실행되지 않는다.
   이전에는 두 작업 시간을 더한 만큼(9분46초+14분42초=24분28초)
   걸렸는데, 변경 후에는 두 작업이 동시에 시작해 더 오래 걸리는 쪽인
   통합 테스트(8분27초) 기준으로 끝난다. 전체 시간이 감소한 14분
   52초 중, 통합 테스트 자체가 줄어든 6분 15초를 제외한 나머지는 이
   동시 실행 효과다.

로컬 컴퓨터에서 `./gradlew integrationTest`만 실행했을 때는 14분42초
→ 7분9초로 줄었다(감소폭 7분33초). GitHub Actions에서는 6분15초가
줄어 로컬보다 감소폭이 1분18초 작다. GitHub Actions는 실행할 때마다
새 컴퓨터에서 시작해 Docker 이미지도 매번 새로 내려받는데, 로컬
컴퓨터는 이미 받아둔 이미지를 그대로 쓸 수 있어서 나타나는 차이로
판단된다.

## 6. 한계와 후속 과제

- 앞으로 MySQL·Redis가 필요한 통합 테스트를 새로 만들 때 4.2절 방식을
  따르지 않고 테스트 클래스마다 컨테이너를 다시 만들면, 컨테이너
  개수가 다시 늘어나면서 이번에 줄인 시간이 다시 늘어난다. 이를 막는
  자동화된 장치는 없으므로 코드 리뷰에서 확인해야 한다.
- 이제는 단위 테스트(9분56초)가 CI 전체 시간을 결정하는 가장 오래
  걸리는 작업이다. 더 줄이려면 단위 테스트가 오래 걸리는 원인(예:
  `@SpringBootTest`가 테스트 클래스마다 애플리케이션을 새로 실행하는
  데 걸리는 시간)을 별도로 확인해야 한다.
