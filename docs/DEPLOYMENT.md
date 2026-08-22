# 배포 (AWS EC2 + RDS)

이 문서는 RoomPick MVP를 외부에서 접근할 수 있는 서버에 배포하는 절차를 설명합니다. 여러 대의 서버가 동시에 떠 있어서 하나가 죽어도 서비스가 끊기지 않는 운영 환경이 아니라, MVP 완료 조건인 "외부에서 접근할 수 있는 개발 서버 배포" 수준을 목표로 합니다.

## 구성

- **앱 EC2 인스턴스**: 애플리케이션 컨테이너 + Redis 실행. 필요하면 API 전용
  인스턴스와 Kafka 이벤트 처리 전용 인스턴스로 나눠서 2대로 운영할 수 있다
  (12절 참고). 기본은 1대로 두 역할을 같이 처리한다.
- **모니터링 EC2 인스턴스**: Prometheus + Grafana + Kafka 실행 (11절 참고)
- **RDS MySQL**: EC2와 분리된 관리형 데이터베이스
- **리전**: `ap-northeast-2` (서울)

배포 환경은 `docs/MVP_CONTEXT.md`에 팀 회의가 필요한 항목으로 표시되어 있었으나, 위 구성으로 팀 결정을 확정했습니다.

---

## 1. 사전 준비 (계정 소유자가 직접 수행)

AWS 자격증명은 대화형 AI 도구가 대신 입력하지 않습니다. 아래 단계는 계정 소유자가 본인 브라우저·터미널에서 직접 진행합니다.

1. IAM 사용자 생성 (AWS 콘솔 → IAM → 사용자 생성)
   - 권한: `AmazonEC2FullAccess`, `AmazonRDSFullAccess`
   - 콘솔 암호 불필요 (프로그래밍 방식 접근만 사용)
2. Access Key 발급
   - 생성된 사용자 → 보안 자격 증명 → 액세스 키 생성 → 사용 사례 "Command Line Interface (CLI)"
   - 발급된 csv는 안전한 곳에 보관 (git에 커밋 금지)
3. 로컬 터미널에서 AWS CLI 설정
   ```bash
   aws configure
   ```
   - Access Key ID / Secret Access Key / `ap-northeast-2` / `json` 입력
4. 확인 (읽기 전용)
   ```bash
   aws sts get-caller-identity
   ```
5. EC2 키페어 생성 (SSH 접속용)
   - AWS 콘솔 → EC2 → 키 페어 → 생성 → `.pem` 파일 로컬에 다운로드 (git에 커밋 금지, `.gitignore`에 `*.pem` 이미 등록됨)
6. 본인 공인 IP 확인 (보안그룹에서 SSH를 내 IP로만 제한하기 위함)
   ```bash
   curl -s https://checkip.amazonaws.com
   ```

---

## 2. 리전 / 사양

| 항목 | 값 | 비고 |
| --- | --- | --- |
| 리전 | `ap-northeast-2` | 서울 |
| EC2 인스턴스 | `t3.small` | 앱 + Redis 운영 (2vCPU/2GB) |
| EC2 AMI | Amazon Linux 2023 | Docker를 user-data로 설치 |
| RDS 인스턴스 | `db.t4g.micro` | 프리티어 대상 (12개월) |
| RDS 스토리지 | gp3 20GB, 단일 AZ | 여러 AZ에 복제하지 않음 (비용 절감) |
| RDS 퍼블릭 액세스 | 비활성화 | EC2 보안그룹에서만 접근 |

Prometheus·Grafana·Kafka는 별도 EC2 인스턴스에서 운영합니다. 11절 참고.

## 3. 네트워크 / 보안그룹

기본 VPC를 사용합니다.

- **EC2 보안그룹** (`roompick-ec2-sg`)
  - `22` (SSH): 내 IP만 허용
  - `8080` (앱): 전체 공개(`0.0.0.0/0`) 허용
  - `8081` (앱의 상태 확인·지표 제공 기능인 Actuator가 쓰는 포트): 모니터링 인스턴스의
    보안그룹(`roompick-kafka-monitoring-sg`)에서 오는 접속만 허용합니다. Prometheus가 이
    포트에서 앱의 지표를 주기적으로 가져갑니다. 그 외에는 접근할 수 없습니다(11절 참고).
- **RDS 보안그룹**
  - `3306`: EC2 보안그룹에서 들어오는 트래픽만 허용 (퍼블릭 미노출). 인스턴스를
    2대로 나눈 경우 컨슈머 인스턴스의 보안그룹도 함께 허용한다(12절 참고).

Prometheus(`9090`)·Grafana(`3000`)의 보안그룹 규칙은 별도 모니터링 인스턴스의 보안그룹에 있습니다. 11절 참고.

## 4. 프로비저닝 절차 (요약)

1. 키페어 생성
2. RDS용 보안그룹 생성 → 3306 인바운드를 EC2 보안그룹으로 제한
3. RDS MySQL 인스턴스 생성 (`db.t4g.micro`, 단일 AZ, 퍼블릭 액세스 비활성화)
4. EC2용 보안그룹 생성 → 22(내 IP), 8080(전체) 허용
5. EC2 인스턴스 생성 (`t3.small`, Amazon Linux 2023, user-data로 Docker 설치)
6. EC2 보안그룹을 RDS 보안그룹의 인바운드 허용 대상으로 지정

실제 리소스 생성(과금 발생) 전에는 위 사양과 예상 비용을 다시 한번 확인한 뒤 진행합니다.

## 5. 배포 절차

1. EC2에 SSH 접속
   ```bash
   ssh -i <키페어>.pem ec2-user@<EC2 퍼블릭 IP>
   ```
2. 리포지토리 클론 후 이미지 빌드
   ```bash
   git clone <repo-url>
   cd roompick-backend
   docker build -t roompick-backend .
   ```
3. 프로덕션 환경변수 파일 준비 (EC2 로컬에만 생성, git에 포함하지 않음)
   ```bash
   # .env.prod (EC2 로컬 전용)
   SPRING_PROFILES_ACTIVE=prod
   DB_HOST=<RDS 엔드포인트>
   DB_PORT=3306
   DB_NAME=roompick
   DB_USERNAME=roompick
   DB_PASSWORD=<RDS 비밀번호>
   JWT_SECRET=<운영용 JWT 시크릿, 로컬 기본값과 다른 값>
   ```
4. 컨테이너 실행
   ```bash
   docker run -d --name roompick-backend \
     --network roompick-net \
     --env-file .env.prod \
     -p 8080:8080 \
     -p <앱 인스턴스 프라이빗 IP>:8081:8081 \
     --restart unless-stopped \
     --log-opt max-size=10m \
     --log-opt max-file=3 \
     roompick-backend
   ```

   `--log-opt`는 컨테이너 로그(`docker logs`로 보는 stdout/stderr)가 무한정 쌓여 EC2 디스크를
   채우는 것을 막는 설정입니다. 파일당 최대 10MB, 최대 3개까지만 남기고 오래된 로그는
   지웁니다(총 30MB 제한).

   `8081`은 Actuator가 쓰는 포트(`management.server.port`, prod 프로필 전용)입니다. 앱
   인스턴스의 프라이빗 IP에만 연결하므로, 같은 VPC 안의 다른 인스턴스(모니터링 인스턴스의
   Prometheus)에서만 접근할 수 있고 퍼블릭 인터넷에는 노출되지 않습니다. 보안그룹에서도
   모니터링 인스턴스의 보안그룹에서 오는 접속만 허용하도록 제한합니다(3절, 11절 참고).

   `roompick-net`은 Redis 컨테이너가 붙어있는 Docker 네트워크입니다. 이 옵션을 빼고 실행하면
   앱이 기본 `bridge` 네트워크에 뜨는데, 이 네트워크에서는 `redis`라는 이름으로 접속할 수
   없어서 헬스체크가 `DOWN`으로 나옵니다.

## 6. 비밀 관리

- `JWT_SECRET`, `DB_PASSWORD` 등은 리포지토리에 실제 값을 커밋하지 않습니다.
- EC2 로컬의 `.env.prod` 파일 또는 `docker run --env-file`로만 주입합니다.
- `application.yml`의 `JWT_SECRET` 기본값(`roompick-local-dev-only-...`)은 로컬 전용이며 운영에서는 반드시 다른 값을 사용합니다.

## 7. 배포 확인

```bash
curl http://<EC2 퍼블릭 IP>:8080/api/v1/accommodations
```

정상 응답(200)을 확인합니다. Actuator가 제공하는 `health` 엔드포인트는 `8081`(앱 인스턴스
프라이빗 IP 전용)에만 있어서 퍼블릭 IP로는 확인할 수 없습니다. 같은 VPC 안(예: 모니터링
인스턴스)에서 확인합니다.

```bash
curl http://<앱 인스턴스 프라이빗 IP>:8081/actuator/health
```

`{"status":"UP"}` 응답을 확인합니다. 앱 인스턴스가 자기 자신의 이 프라이빗 IP로 curl하면
연결되지 않습니다 — Docker가 게시한 포트로 자기 자신에게 접속할 때 흔히 나타나는 동작이며,
다른 인스턴스에서 접속하면 정상 동작합니다. 앱 인스턴스 스스로 확인해야 하면
`curl http://127.0.0.1:8080/api/v1/accommodations`로 대신 확인합니다.

## 8. 비용 안내

- 프리티어(가입 후 12개월) 기간에는 EC2 `t3.micro` + RDS `db.t4g.micro` 조합이 대체로 무료 한도 내에서 운영 가능합니다.
- 프리티어 종료 후에는 EC2/RDS 인스턴스 시간당 요금과 RDS 스토리지 요금이 발생합니다. 실제 리소스 생성 전에 최신 요금을 다시 확인합니다.

## 9. CI/CD 자동 배포

`develop`에 push되면 GitHub Actions가 자동으로 테스트 → 빌드 → 배포까지 진행합니다 (`.github/workflows/cd.yml`).

1. **test**: 기존 CI와 동일하게 `./gradlew test` 실행
2. **build-and-push**: GitHub 러너에서 `docker build` 후 GHCR(`ghcr.io/imsun9/roompick-backend`)에 `latest`, 커밋 SHA 태그로 push
3. **deploy**: GitHub Actions가 SSH로 EC2에 접속해 새 이미지를 `pull`하고 기존 컨테이너를 교체

EC2는 더 이상 직접 `docker build`를 하지 않습니다. 이미 빌드된 이미지를 받기만 하므로, `t3.micro`에서 이미지 빌드 중 메모리가 부족해 실패하던 문제(OOM)가 다시 생기지 않습니다.

수동으로 다시 배포하고 싶을 때는 GitHub 저장소 Actions 탭에서 `CD` 워크플로우를 `workflow_dispatch`로 직접 실행할 수 있습니다.

### 필요한 GitHub Secrets

| 이름 | 값 |
| --- | --- |
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_SSH_KEY` | EC2 접속용 키페어의 개인키(`.pem`) 전체 내용 |
| `AWS_ACCESS_KEY_ID` | 1단계에서 발급한 IAM 사용자의 Access Key ID |
| `AWS_SECRET_ACCESS_KEY` | 1단계에서 발급한 IAM 사용자의 Secret Access Key |
| `EC2_SG_ID` | EC2용 보안그룹 ID (`sg-`로 시작, 3~4단계에서 만든 EC2 보안그룹). AWS 콘솔 → EC2 → 보안 그룹에서 확인 |

`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`는 CD가 배포를 실행할 때마다 GitHub Actions
러너의 공인 IP를 `EC2_SG_ID` 보안그룹의 22번 포트에 잠깐 추가했다가 배포가 끝나면 다시
지우는 데 사용합니다(`.github/workflows/cd.yml`,
[docs/bug/cd-ec2-ssh-timeout-security-group.md](bug/cd-ec2-ssh-timeout-security-group.md) 참고).
1단계에서 만든 IAM 사용자의 키를 그대로 등록하면 됩니다.

`GITHUB_TOKEN`은 Actions가 자동으로 제공하므로 별도 등록이 필요 없습니다.

### Flyway 마이그레이션 CI 검증

`test`(H2, `flyway.enabled: false`)에서는 Flyway 마이그레이션이 실제로 실행되지 않으므로,
`src/main/resources/db/migration`의 SQL이 실제 MySQL에서 문제없이 적용되는지는 별도로
검증해야 합니다. 검증 방식으로 두 가지를 검토했습니다.

- **A. `flywayMigrate` Gradle 태스크**: Flyway만 단독 실행해 마이그레이션 SQL 자체를 검증.
  Flyway Gradle 플러그인 추가가 필요하지만, 앱을 띄우지 않고 SQL만 실행하므로 빠르고
  실패했을 때 원인(Flyway 에러 로그)이 바로 드러남.
- **B. prod와 비슷한 프로필로 앱을 실제로 띄움**: 마이그레이션과 Hibernate
  `ddl-auto: validate`가 하는 엔티티-스키마 일치 확인까지 한 번에 검증하고 실제 배포
  경로와 가장 가깝지만, 앱 전체를 띄우는 만큼 느리고 실패했을 때 원인(Flyway인지
  Hibernate인지 다른 빈 초기화 문제인지)을 찾기 상대적으로 어려움.

**A안 채택.** 마이그레이션 파일이 아직 4개뿐이라 검증 대상이 단순하고, CI를 자주 돌리는
단계에서는 실행 속도와 실패 원인을 빠르게 찾는 게 더 중요하다고 판단했습니다. 엔티티와
스키마가 일치하는지는 이미 `test` 작업과 실제 배포 시 `ddl-auto: validate`가 걸러주므로,
이 검증에서 앱 전체를 또 띄워 중복으로 확인할 필요는 없습니다.

## 10. 장애 시 재배포·롤백 절차

### 10-1. 배포 실패 감지

- GitHub Actions `CD` 워크플로우가 실패하면(테스트·빌드·배포 어느 단계든) `deploy` 작업의
  `Deploy to EC2 over SSH` 단계가 실행되지 않거나 중간에 실패로 끝납니다. 이 경우 **기존에
  떠 있던 컨테이너는 그대로 남아 서비스가 유지됩니다.** 즉 새 이미지를 받아오거나 컨테이너를
  교체하는 데 실패해도 자동으로 서비스가 끊기지는 않습니다.
- 단, 컨테이너 교체 단계(`docker stop` → `docker rm` → `docker run`) 도중에 실패하면
  이전 컨테이너는 이미 내려간 상태에서 새 컨테이너가 뜨지 못했을 수 있습니다. 이 경우
  `curl http://<EC2 퍼블릭 IP>:8080/api/v1/accommodations`가 응답하지 않는 것으로
  확인합니다(Actuator의 `health`는 `8081`(앱 인스턴스 프라이빗 IP 전용)에만 있어서 같은
  VPC 안에서만 확인할 수 있습니다).

### 10-2. 이전 버전으로 롤백

배포는 `latest`가 아닌 커밋 SHA 태그(`${{ github.sha }}`)로 이루어지므로, GHCR에 남아있는
과거 이미지로 즉시 롤백할 수 있습니다.

1. 롤백할 커밋의 SHA 확인 (`git log --oneline`)
2. EC2에 SSH 접속
   ```bash
   ssh -i <키페어>.pem ec2-user@<EC2 퍼블릭 IP>
   ```
3. 해당 SHA의 이미지로 컨테이너 교체
   ```bash
   sudo docker pull ghcr.io/imsun9/roompick-backend:<이전 커밋 SHA>
   sudo docker stop roompick-backend || true
   sudo docker rm roompick-backend || true
   sudo docker run -d --name roompick-backend \
     --network roompick-net \
     --env-file /home/ec2-user/app/.env.prod \
     -p 8080:8080 \
     -p <앱 인스턴스 프라이빗 IP>:8081:8081 \
     --restart unless-stopped \
     --log-opt max-size=10m \
     --log-opt max-file=3 \
     ghcr.io/imsun9/roompick-backend:<이전 커밋 SHA>
   ```
4. `curl http://<앱 인스턴스 프라이빗 IP>:8081/actuator/health`(같은 VPC 안의 다른
   인스턴스에서)로 정상 기동을 확인합니다. 앱 인스턴스가 자기 자신의 이 주소로 curl하면
   연결되지 않으니(7절 참고), 앱 인스턴스 스스로 확인해야 하면
   `curl http://127.0.0.1:8080/api/v1/accommodations`로 대신 확인합니다.

### 10-3. 코드 원인 수정 후 재배포

일회성 롤백이 아니라 `develop` 자체를 되돌려야 하는 경우:

1. 문제가 된 커밋을 `revert`하거나 원인을 수정한 새 커밋을 `develop`에 push
2. push 시 CD가 자동으로 test → build → deploy를 다시 실행
3. 또는 GitHub 저장소 Actions 탭 → `CD` 워크플로우 → `Run workflow`(`workflow_dispatch`)로
   특정 시점 재배포를 수동 트리거

### 10-4. DB 마이그레이션이 원인인 경우

`ddl-auto: validate`이므로 Flyway가 적용한 스키마와 엔티티가 어긋나면 앱이 아예 뜨지
못합니다. 이 경우 이미지 롤백만으로는 부족하고, 문제가 된 `V*__*.sql` 마이그레이션을
되돌리는 새 마이그레이션(`V(n+1)__revert_xxx.sql`)을 추가해야 합니다. Flyway는 이미 적용된
마이그레이션 파일을 수정하면 체크섬이 달라져 실패하므로, 기존 파일을 고치지 말고 항상
새 버전을 추가합니다.

## 11. 모니터링·메시징 (별도 EC2: Prometheus + Grafana + Kafka)

Prometheus·Grafana·Kafka는 앱 인스턴스와 별도인 `roompick-kafka-monitoring`(`t3.small`,
`ap-northeast-2a`) 인스턴스에서 구동합니다. 앱 인스턴스와는 같은 VPC 안에서 프라이빗
IP·보안그룹으로만 통신하며, 퍼블릭 인터넷에는 노출하지 않습니다. Redis는 앱과 강하게
묶여 있어 이번 분리 대상에서 제외하고 앱 인스턴스에 그대로 둡니다.

| 항목 | 값 |
| --- | --- |
| 인스턴스 | `roompick-kafka-monitoring`, `t3.small`, Amazon Linux 2023 |
| 보안그룹 | `roompick-kafka-monitoring-sg` |
| `22`(SSH) | 팀 고정 IP만 허용 |
| `9090`(Prometheus) | 팀 고정 IP만 허용 — Prometheus는 로그인 기능이 없어 공개로 열면 안 됨 |
| `3000`(Grafana) | 팀 고정 IP만 허용 |
| `9092`(Kafka) | 앱 인스턴스의 보안그룹(`roompick-ec2-sg`)에 속한 인스턴스만 허용. 인스턴스를 2대로 나눈 경우 컨슈머 인스턴스의 보안그룹도 함께 허용(12절 참고) |

IP 주소가 아니라 보안그룹을 기준으로 허용하므로, 앱 인스턴스의 IP가 바뀌어도 규칙을 다시 설정할 필요가 없습니다.

앱 인스턴스(`roompick-ec2-sg`)에는 `8081`(Actuator) 인바운드를 이 모니터링 인스턴스의
보안그룹에서 오는 접속만 허용하도록 추가되어 있고(3절), Actuator 컨테이너의 포트 연결도
`127.0.0.1:8081:8081`이 아니라 `<앱 인스턴스 프라이빗 IP>:8081:8081`로 바꿔서 같은 VPC
안에서만 접근할 수 있게 했습니다(5절). 퍼블릭 노출은 이전과 마찬가지로 없습니다.

### 11-1. 최초 구동 (모니터링 인스턴스에서)

```bash
ssh -i <키페어>.pem ec2-user@<모니터링 인스턴스 퍼블릭 IP>
git clone <repo-url> && cd roompick-backend
echo "GF_SECURITY_ADMIN_PASSWORD=<운영용 Grafana 비밀번호>" > monitoring/.env   # 기본 admin/admin 기동 방지, git에 포함하지 않음
docker compose -f monitoring/docker-compose.yml up -d
docker compose -f kafka/docker-compose.kafka.yml up -d
```

`monitoring/prometheus.yml`의 지표 수집 대상과 `kafka/docker-compose.kafka.yml`의
`KAFKA_ADVERTISED_LISTENERS` 값은 컨테이너 이름이 아니라 각 인스턴스의 **프라이빗 IP**를
직접 적어둡니다. 서로 다른 인스턴스라서 컨테이너 이름으로는 서로를 찾을 수 없기
때문입니다. 인스턴스를 다시 만들어 프라이빗 IP가 바뀌면 두 값 모두 새로 갱신해야 합니다.

### 11-2. 확인

```bash
curl http://localhost:9090/api/v1/targets   # roompick-backend 수집 대상이 up인지 확인
```

- Grafana: `http://<모니터링 인스턴스 퍼블릭 IP>:3000` (`GF_SECURITY_ADMIN_PASSWORD`로
  지정한 비밀번호로 로그인. 기본 `admin`/`admin` 계정으로는 뜨지 않습니다)
- Grafana에서 데이터 소스로 Prometheus를 추가할 때 URL은 같은 인스턴스 안에서 컨테이너
  이름으로 통신하므로 `http://prometheus:9090`
- Kafka 접속 주소는 `<모니터링 인스턴스 프라이빗 IP>:9092`이며, 앱 인스턴스의 `.env.prod`에
  있는 `KAFKA_BOOTSTRAP_SERVERS`가 이 값을 가리켜야 합니다.

### 11-3. 알려진 제약

- 모니터링 인스턴스가 자기 자신의 프라이빗 IP로 앱 인스턴스에 접속하는 것처럼, 인스턴스가
  자기 자신에게 다시 접속하는 상황이 아니라 **다른** 인스턴스가 서로 접속하는 상황에서만
  이 구성이 의미가 있습니다. 자기 자신의 프라이빗 IP로 접속하면 연결되지 않을 수 있습니다
  (7절 참고). 실제로 확인해야 하는 건 "다른 인스턴스에서 접근되는지"이므로 문제가 아닙니다.
- Prometheus·Grafana·Kafka의 데이터는 각각 Docker volume에 저장되므로 컨테이너를
  다시 만들어도 데이터는 남지만, 이 인스턴스 자체를 삭제하면 데이터도 함께 사라집니다.
- Kafka는 서버 1대로만 운영합니다. 별도의 조정 서버(ZooKeeper) 없이 Kafka 자신이 조정
  기능까지 맡는 방식(KRaft)을 쓰지만, 서버가 여러 대로 복제되어 있지는 않으므로 이 서버가
  멈추면 Kafka 전체가 멈춥니다. MVP 단계의 트래픽 규모에 맞춘 구성입니다.
- 분리하기 전 기존 인스턴스에 쌓여있던 Prometheus 지표 기록과 Grafana 대시보드는 옮기지
  않고 새로 시작했습니다.

## 12. 프로듀서·컨슈머 인스턴스 분리

기존에는 인스턴스 1대(`roompick-backend`)가 API 처리와 Kafka 이벤트 처리(결제
완료 메일 발송, 특가 점유 처리)를 동시에 했다. 이제 인스턴스를 2대로 나눠서,
한쪽은 API만, 다른 쪽은 이벤트 처리만 하게 한다.

**핵심은 환경변수 하나다.** `KAFKA_CONSUMER_ENABLED`를 `false`로 두면
그 인스턴스는 이벤트를 처리하지 않고 API만 한다. 안 건드리면(기본값
`true`) 이벤트도 처리한다.

**준비 (AWS 콘솔, 계정 소유자가 직접)**

1. 새 보안그룹(`roompick-app-consumer-sg`) 생성 — SSH(22)만 열고, API
   포트(8080)는 열지 않는다(이 인스턴스는 API를 안 받으니까)
2. 기존 RDS·Kafka 보안그룹에 위 보안그룹을 허용 대상으로 추가 — 새
   인스턴스도 같은 DB·Kafka에 접근해야 하므로
3. 새 EC2 인스턴스(`roompick-app-consumer`) 생성 — 기존 인스턴스와 같은
   방식으로 Docker만 설치해두면 됨

이 인스턴스는 외부 공개 IP가 없어서, 기존 인스턴스를 거쳐야 SSH로
들어갈 수 있다.

**배포 (코드가 `develop`에 merge된 뒤)**

1. merge하면 CD가 자동으로 **기존 `roompick-backend`에만** 새 버전을 배포한다
2. 기존 `roompick-backend`에는 `KAFKA_CONSUMER_ENABLED=false`를 수동으로
   추가하고 재시작한다 — 이제부터 API만 처리
3. 새 인스턴스(`roompick-app-consumer`)에는 기존 설정 파일을 그대로
   복사해서 같은 이미지를 실행한다 — 이제부터 이벤트만 처리
4. 결제·특가 이벤트를 하나 만들어서, 새 인스턴스 쪽에서만 처리 로그가
   찍히는지 확인

새 인스턴스로의 배포는 지금은 수동이다. 자동 배포까지 하려면 CD
설정(`cd.yml`)을 팀에서 추가로 손봐야 한다(GitHub 권한상 AI가 그 파일을
직접 못 고친다).

## 13. 이번 범위에 포함하지 않는 항목 (향후 별도 작업)

다음 항목은 이번 배포 범위에 포함하지 않았습니다. 필요하면 팀 논의 후 별도 작업으로 진행합니다.

- HTTPS 적용 (도메인 + Nginx + Let's Encrypt)
- ECS/Fargate 등 컨테이너 오케스트레이션으로 전환
- IAM 사용자 권한 최소화: 현재 CD가 쓰는 IAM 사용자가 `AmazonEC2FullAccess`,
  `AmazonRDSFullAccess`를 갖고 있지만, 실제로 필요한 권한은 `EC2_SG_ID` 보안그룹의 규칙을
  추가·삭제하는 권한(`AuthorizeSecurityGroupIngress`/`RevokeSecurityGroupIngress`)뿐입니다.
  꼭 필요한 권한만 담은 정책으로 좁히거나, CD 전용 IAM 사용자를 새로 만듭니다.
- 장기 AWS Access Key 제거: `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`를 GitHub
  Secrets에 계속 보관하는 대신, GitHub의 OIDC 기능으로 배포할 때마다 짧게만 유효한 키를
  발급받는 방식(`role-to-assume`)으로 바꿔서, 만료 없는 키 자체를 없앱니다. 전환 전까지는
  이 키가 유출되면 EC2·RDS 전체가 위험해지므로, 위 IAM 권한 최소화와 함께 진행하는 것이
  안전합니다.
