# RoomPick Backend

RoomPick은 숙소와 실제 객실을 예약하고 결제하는 3인 팀 숙박 예약 서비스입니다.

현재 목표는 검색을 제외한 최소 MVP입니다. 숙소 더미 데이터 1개와 실제 객실 더미 데이터 1개를 기준으로 예약 흐름을 먼저 완성하고 이후 버전에서 기능을 확장합니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.14
- Gradle 8.14.3
- Spring Web, Spring Data JPA, Spring Security, Validation
- MySQL 8.4
- JUnit 5, H2
- GitHub Actions

## 담당 영역

| 담당자 | 영역 |
| --- | --- |
| IMSUN9 | 숙소, 객실, 예약, 전체 통합 |
| minjae123123 | 결제 |
| oHAHOo | 회원, 인증·인가 |

## 로컬 실행

필수 도구는 JDK 17과 Docker입니다.

```bash
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

애플리케이션 상태는 `GET http://localhost:8080/actuator/health`에서 확인합니다.

MySQL에 직접 접속할 때는 로그인부터 시작합니다.

```bash
mysql -h 127.0.0.1 -P 3307 -u roompick -p
```

비밀번호는 `.env`의 `DB_PASSWORD` 값이며 기본 예시는 `roompick`입니다.

```sql
USE roompick;
SHOW TABLES;
```

종료할 때 데이터는 보존됩니다.

```bash
docker compose down
```

## 테스트

```bash
./gradlew test
```

테스트는 별도의 H2 인메모리 데이터베이스를 사용하므로 로컬 MySQL이 없어도 실행할 수 있습니다.

## 배포

AWS EC2 + RDS 배포 절차는 [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)에서 확인합니다.

## 브랜치와 PR

1. `develop`에서 작업 브랜치를 만듭니다.
2. `feature/{기능명}` 형식을 사용합니다.
3. PR은 `develop`을 대상으로 작성합니다.
4. 최소 1명의 리뷰 후 병합합니다.

상세 규칙과 설계 문서는 [`docs`](docs/)에서 확인합니다.
