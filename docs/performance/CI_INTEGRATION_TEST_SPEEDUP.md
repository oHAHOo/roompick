# CI 통합 테스트 실행 시간 단축

## 1. 문서 목적

이 문서는 CI `test` job이 24분 넘게 걸리던 문제를 왜 그렇게 오래
걸렸는지, 무엇을 바꿔서 얼마나 줄었는지 정리한다.

- 관련 이슈: `#137`
- 관련 PR: `#139`

## 2. 문제

2026-08-17 develop 브랜치 기준 CI 실행 시간은 다음과 같았다.

```text
test job (24분 52초)
├── Run unit tests            9분 46초
└── Run MySQL integration tests  14분 42초  (같은 job에서 순차 실행)

flyway-check job (51초, test와 병렬)
```

PR 하나 올릴 때마다 CI가 끝나는 데 25분 가까이 걸렸다.

## 3. 왜 오래 걸렸는가

### 3.1 두 작업이 한 줄로 서서 순서를 기다림

`test` job 안에서 "단위 테스트 실행 → 통합 테스트 실행"이 순서대로
돌고 있었다. 이 둘은 서로 결과를 주고받을 필요가 없는, 완전히 독립된
작업이다. 그런데도 한 job 안에 같이 넣어놨기 때문에 뒤 작업(통합
테스트)이 앞 작업(단위 테스트)이 끝날 때까지 그냥 기다렸다. 줄을 한
줄로만 세워놓고 창구는 하나만 연 셈이다.

### 3.2 테스트마다 서버를 새로 사고 버림

통합 테스트는 진짜 MySQL과 Redis가 있어야 동작을 확인할 수 있다.
그래서 테스트 코드가 Testcontainers라는 도구로 "테스트용 MySQL
서버"와 "테스트용 Redis 서버"를 그때그때 띄워서 썼다.

문제는 이걸 **테스트 클래스마다 따로** 했다는 점이다. 클래스 A를
테스트할 때 서버를 하나 새로 켜고, 끝나면 버리고, 클래스 B를 테스트할
때 또 새로 켜고, 끝나면 또 버리고... 이런 식으로 MySQL 서버를 11번,
Redis 서버를 6번, 총 17번을 새로 켰다 껐다 했다. 서버 하나를 새로
켜는 데도 시간이 걸리는데(수십 초), 이 "켜고 끄는" 시간만 다 합쳐도
통합 테스트 시간의 상당 부분을 차지하고 있었다.

## 4. 무엇을 바꿨는가

### 4.1 두 작업을 별도 창구로 분리한다

`.github/workflows/ci.yml`의 `test` job을 `unit-test` /
`integration-test` 두 개의 독립된 job으로 나눴다. GitHub Actions는
서로 의존관계가 없는 job을 자동으로 동시에 실행해주기 때문에, 이제 두
작업이 순서를 기다리지 않고 같은 시간에 나란히 돈다.

### 4.2 서버를 새로 사고 버리는 대신, 하나를 계속 쓴다

`SharedMySqlTestContainer`, `SharedRedisTestContainer`
(`src/test/java/com/roompick/testsupport`)를 새로 만들었다. 발상은
단순하다 — 테스트 클래스마다 서버를 새로 사지 않고, MySQL 서버 1대,
Redis 서버 1대만 켜놓은 채로 모든 테스트 클래스가 같이 쓴다.

```text
기존: 테스트 클래스마다 서버를 새로 켜고 끔 (총 17번)
변경: MySQL 서버 1대, Redis 서버 1대만 켜놓고 계속 씀
```

서버를 같이 쓴다고 해서 테스트 데이터가 섞이는 건 아니다. 한 서버
안에서도 MySQL은 "데이터베이스"라는 서랍을 여러 개 만들 수 있는데,
클래스마다 자기 서랍(데이터베이스 이름)을 그대로 유지하도록 했다.
그래서 서버는 하나를 같이 쓰지만, 각 테스트가 보는 데이터는 이전과
똑같이 서로 분리돼 있다.

```java
@Tag("integration")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
class ReservationConcurrencyMySqlIntegrationTest {

    private static final String DATABASE_NAME = "roompick_reservation_lock_test";

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        // 이 클래스 전용 서랍(데이터베이스)이 없으면 만든다
        SharedMySqlTestContainer.createDatabaseIfAbsent(DATABASE_NAME);
        // 그 서랍을 가리키는 접속 주소로 연결한다
        registry.add("spring.datasource.url", () -> SharedMySqlTestContainer.jdbcUrl(DATABASE_NAME));
        registry.add("spring.datasource.username", () -> SharedMySqlTestContainer.USERNAME);
        registry.add("spring.datasource.password", () -> SharedMySqlTestContainer.PASSWORD);
    }
}
```

Redis는 애초에 "서랍" 같은 구분이 없고, 각 테스트가 자기가 쓴 키를
알아서 지우고 있었기 때문에 서랍을 나눌 필요 없이 그냥 주소(호스트·포트)만
알려주면 된다.

```java
@DynamicPropertySource
static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", SharedRedisTestContainer::host);
    registry.add("spring.data.redis.port", SharedRedisTestContainer::port);
}
```

### 4.3 구현하면서 부딪힌 문제 두 가지

서버(컨테이너)를 하나만 켜놓고 여러 데이터베이스를 만들어 쓰려면,
접속 계정에 "아무 데이터베이스나 새로 만들 수 있는 권한"이 있어야
한다. 그런데 컨테이너가 처음 켜질 때 기본으로 만들어주는 계정은 정해진
데이터베이스 하나에만 권한이 있어서, 컨테이너를 켜자마자 관리자(root)
계정으로 접속해서 "전체 권한을 줘"라고 명령을 한 번 실행하게
했다(`GRANT ALL PRIVILEGES`). 이 과정에서 두 가지가 막혔다.

- **root로 접속이 안 됨**: MySQL은 기본적으로 root 계정을 "컨테이너
  안에서만" 접속 가능하게 만든다. 그런데 테스트 코드는 컨테이너
  바깥(호스트 컴퓨터)에서 접속하는 거라서 거부당했다. → 컨테이너를 켤 때
  `MYSQL_ROOT_HOST=%` 설정을 추가해서 "바깥에서 root로 접속해도 된다"고
  허용했다.
- **root 비밀번호가 몰래 바뀌어 있었음**: 컨테이너를 만들 때 쓰는
  도구(Testcontainers)가 "앱 계정 비밀번호"를 root 비밀번호에도
  그대로 복사해버리는 동작이 있었다. 그래서 필자가 따로 정해둔 root
  비밀번호로는 접속이 안 됐다. → root 접속에도 앱 계정과 같은
  비밀번호를 쓰도록 맞춰서 해결했다.

두 문제 다 실제로 테스트를 돌려서 나온 에러 로그(`Access denied for
user 'root'@'...'`)를 보고 원인을 찾아 고쳤다.

## 5. 측정 결과

같은 워크플로 기준, 바꾸기 전(2026-08-17 develop 실행)과 바꾼 후(PR
#139 CI 실행, 2026-08-19)를 비교했다.

| 항목 | 변경 전 | 변경 후 | 변화 |
| --- | --- | --- | --- |
| **CI 전체 소요 시간** | 24분 52초 | **10분 0초** | **약 60% 단축** |
| unit test | 9분 46초 | 9분 56초 | 거의 동일 |
| integration test | 14분 42초 | 8분 27초 | 약 43% 단축 |
| flyway-check | 51초 | 46초 | 거의 동일 |

변경 후 `unit-test`/`integration-test`/`flyway-check` 세 job이 모두
같은 시각에 시작해서, 실제로 동시에 실행되는 것까지 확인했다.

전체 시간이 크게 줄어든 데는 두 가지 이유가 겹쳐 있다.

1. **통합 테스트 자체가 빨라짐** (14분42초 → 8분27초) — 서버를 새로
   켜고 끄는 시간이 줄어든 효과다.
2. **더 이상 순서를 기다리지 않음** — 예전에는 "단위 테스트 9분46초 +
   통합 테스트 14분42초"를 그대로 더한 시간이 걸렸는데, 이제는 둘이
   동시에 돌아서 "둘 중 더 오래 걸리는 쪽" 시간만큼만 기다리면 된다.
   전체 시간이 줄어든 폭의 더 큰 부분은 사실 이 효과다.

참고로 로컬 컴퓨터에서 `./gradlew integrationTest`만 따로 돌렸을 때는
14분42초 → 7분9초로 더 크게 줄었다. GitHub Actions는 로컬과 달리 매번
새 컴퓨터에서 시작해서 Docker 이미지도 매번 새로 받아야 하기 때문에,
그만큼 CI 쪽 단축 폭이 로컬보다 조금 작게 나온다.

## 6. 한계와 후속 과제

- 앞으로 MySQL·Redis가 필요한 통합 테스트를 새로 만들 때 이 방식(4.2절)을
  안 쓰고 예전처럼 클래스마다 서버를 새로 만들면, 서버 개수가 다시
  하나둘 늘어나면서 이번에 줄인 효과가 서서히 사라진다. 이걸 자동으로
  막아주는 장치는 없어서, 코드 리뷰에서 확인해야 한다.
- 이제는 단위 테스트(9분56초)가 CI 전체 시간을 좌우하는 병목이다. 더
  줄이려면 단위 테스트 자체가 왜 이렇게 오래 걸리는지(예:
  `@SpringBootTest`가 매번 스프링 애플리케이션을 새로 띄우는 비용)를
  따로 들여다봐야 한다.
