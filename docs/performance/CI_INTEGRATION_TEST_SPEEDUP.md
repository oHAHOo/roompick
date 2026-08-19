# CI 통합 테스트 실행 시간 단축

## 1. 문서 목적

이 문서는 CI `test` job이 24분 넘게 걸리던 문제의 원인과, job 분리·Testcontainers
싱글톤화로 이를 줄인 결과를 정리한다.

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

`test` job 하나가 전체 CI 시간을 그대로 늘리고 있었다.

## 3. 원인 분석

1. **job 미분리**: unit test와 integration test가 서로 데이터 의존 없이 실행
   가능한데도 한 job 안에서 순차 실행되고 있었다.
2. **Testcontainers 중복 기동**: 통합 테스트 클래스 16개가 각자
   `@Container` 정적 필드로 컨테이너를 개별 기동하고 있었다. MySQL
   컨테이너 11개, Redis 컨테이너 6개가 클래스마다 새로 뜨고 내려가면서
   컨테이너 기동 오버헤드만 상당한 비중을 차지했다.

## 4. 적용한 변경

### 4.1 CI job 분리

`.github/workflows/ci.yml`의 `test` job을 `unit-test` / `integration-test`
두 개의 독립 job으로 분리했다. 서로 의존관계가 없는 top-level job이라
GitHub Actions가 자동으로 병렬 실행한다.

### 4.2 Testcontainers 싱글톤화

`SharedMySqlTestContainer`, `SharedRedisTestContainer`
(`src/test/java/com/roompick/testsupport`)를 추가해 통합 테스트 전체가
컨테이너를 하나씩만 공유하도록 변경했다.

```text
기존: 테스트 클래스마다 컨테이너를 새로 기동·종료
      (MySQL 11개 + Redis 6개 = 17회 기동)

변경: MySQL 1개, Redis 1개만 기동한 채 계속 사용
      각 클래스는 기존과 동일하게 자기 전용 데이터베이스 이름으로
      스키마를 분리 (데이터 격리는 그대로 유지)
```

자세한 사용 방법은 `docs/CODE_CONVENTION.md` 19절을 참고한다.

## 5. 측정 결과

같은 워크플로 기준, 변경 전(2026-08-17 develop 실행)과 변경 후(PR #139
CI 실행, 2026-08-19)를 비교했다.

| 항목 | 변경 전 | 변경 후 | 변화 |
| --- | --- | --- | --- |
| **CI 전체 소요 시간** | 24분 52초 | **10분 0초** | **약 60% 단축** |
| unit test | 9분 46초 | 9분 56초 | 거의 동일 |
| integration test | 14분 42초 | 8분 27초 | 약 43% 단축 |
| flyway-check | 51초 | 46초 | 거의 동일 |

변경 후 `unit-test`/`integration-test`/`flyway-check` 세 job이 모두 같은
시각에 시작해 실제로 병렬 실행됨을 확인했다.

```text
전체 시간 감소의 두 요인
1. integration test 자체 단축 (14분42초 → 8분27초)
   → Testcontainers 싱글톤화 효과
2. unit test와 integration test가 더 이상 순차 합산되지 않음
   → job 분리로 인한 병렬화 효과 (감소분의 더 큰 비중)
```

로컬(`./gradlew integrationTest`)에서는 14분42초 → 7분9초로 더 크게
단축됐는데, GitHub Actions 러너는 로컬과 달리 Docker 이미지를 매 실행마다
새로 받아야 해서 그 차이만큼 CI 쪽 단축 폭이 조금 작게 나타난다.

## 6. 한계와 후속 과제

- 새로 추가하는 MySQL·Redis 통합 테스트도 이 패턴을 따르지 않으면
  컨테이너 수가 다시 늘어나 효과가 서서히 사라진다. `docs/CODE_CONVENTION.md`
  19절에 컨벤션으로 남겨뒀다.
- unit test(9분56초)가 이제 CI 전체 시간의 병목이다. 추가로 줄이려면
  unit test 자체의 원인(예: `@SpringBootTest` 컨텍스트 로딩 비용)을
  별도로 분석해야 한다.
