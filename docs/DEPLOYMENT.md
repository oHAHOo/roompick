# 배포 (AWS EC2 + RDS)

이 문서는 RoomPick MVP를 외부에서 접근 가능한 개발 서버로 배포하는 절차를 설명합니다. 프로덕션급 고가용성 구성이 아니라, MVP 완료 조건인 "외부에서 접근할 수 있는 개발 서버 배포" 수준을 목표로 합니다.

## 구성

- **EC2 단일 인스턴스**: 애플리케이션 컨테이너만 실행
- **RDS MySQL**: EC2와 분리된 관리형 데이터베이스
- **리전**: `ap-northeast-2` (서울)

배포 환경은 `docs/MVP_CONTEXT.md`에 팀 회의가 필요한 항목으로 표시되어 있었으나, 위 구성으로 팀 결정을 확정했습니다.

---

## 1. 사전 준비 (계정 소유자가 직접 수행)

AWS 자격증명은 대화형 AI 도구가 대신 입력하지 않습니다. 아래 단계는 계정 소유자가 본인 브라우저/터미널에서 직접 진행합니다.

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
| EC2 인스턴스 | `t3.micro` | 프리티어 대상 (12개월) |
| EC2 AMI | Amazon Linux 2023 | Docker를 user-data로 설치 |
| RDS 인스턴스 | `db.t4g.micro` | 프리티어 대상 (12개월) |
| RDS 스토리지 | gp3 20GB, 단일 AZ | 멀티 AZ 미사용 (비용 절감) |
| RDS 퍼블릭 액세스 | 비활성화 | EC2 보안그룹에서만 접근 |

## 3. 네트워크 / 보안그룹

기본 VPC를 사용합니다.

- **EC2 보안그룹**
  - `22` (SSH): 내 IP만 허용
  - `8080` (앱): `0.0.0.0/0` 허용
- **RDS 보안그룹**
  - `3306`: EC2 보안그룹에서 들어오는 트래픽만 허용 (퍼블릭 미노출)

## 4. 프로비저닝 절차 (요약)

1. 키페어 생성
2. RDS용 보안그룹 생성 → 3306 인바운드를 EC2 보안그룹으로 제한
3. RDS MySQL 인스턴스 생성 (`db.t4g.micro`, 단일 AZ, 퍼블릭 액세스 비활성화)
4. EC2용 보안그룹 생성 → 22(내 IP), 8080(전체) 허용
5. EC2 인스턴스 생성 (`t3.micro`, Amazon Linux 2023, user-data로 Docker 설치)
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
     --env-file .env.prod \
     -p 8080:8080 \
     --restart unless-stopped \
     roompick-backend
   ```

## 6. 비밀 관리

- `JWT_SECRET`, `DB_PASSWORD` 등은 리포지토리에 실제 값을 커밋하지 않습니다.
- EC2 로컬의 `.env.prod` 파일 또는 `docker run --env-file`로만 주입합니다.
- `application.yml`의 `JWT_SECRET` 기본값(`roompick-local-dev-only-...`)은 로컬 전용이며 운영에서는 반드시 다른 값을 사용합니다.

## 7. 배포 확인

```bash
curl http://<EC2 퍼블릭 IP>:8080/actuator/health
```

`{"status":"UP"}` 응답을 확인합니다.

## 8. 비용 안내

- 프리티어(가입 후 12개월) 기간에는 EC2 `t3.micro` + RDS `db.t4g.micro` 조합이 대체로 무료 한도 내에서 운영 가능합니다.
- 프리티어 종료 후에는 EC2/RDS 인스턴스 시간당 요금 + RDS 스토리지 요금이 발생합니다. 실제 리소스 생성 전에 최신 요금을 다시 확인합니다.

## 9. CI/CD 자동 배포

`develop`에 push되면 GitHub Actions가 자동으로 테스트 → 빌드 → 배포까지 진행합니다 (`.github/workflows/cd.yml`).

1. **test**: 기존 CI와 동일하게 `./gradlew test` 실행
2. **build-and-push**: GitHub 러너에서 `docker build` 후 GHCR(`ghcr.io/imsun9/roompick-backend`)에 `latest`, 커밋 SHA 태그로 push
3. **deploy**: GitHub Actions가 SSH로 EC2에 접속해 새 이미지를 `pull`하고 기존 컨테이너를 교체

EC2는 더 이상 직접 `docker build`를 하지 않습니다 — 이미 빌드된 이미지를 받기만 하므로, `t3.micro`의 메모리 부족(OOM) 문제가 재발하지 않습니다.

수동으로 다시 배포하고 싶을 때는 GitHub 저장소 Actions 탭에서 `CD` 워크플로우를 `workflow_dispatch`로 직접 실행할 수 있습니다.

### 필요한 GitHub Secrets

| 이름 | 값 |
| --- | --- |
| `EC2_HOST` | EC2 퍼블릭 IP |
| `EC2_SSH_KEY` | EC2 접속용 키페어의 개인키(`.pem`) 전체 내용 |

`GITHUB_TOKEN`은 Actions가 자동으로 제공하므로 별도 등록이 필요 없습니다.

## 10. 이번 범위에 포함하지 않는 항목 (향후 별도 작업)

다음 항목은 이번 배포 범위에 포함하지 않았습니다. 필요 시 팀 논의 후 별도 작업으로 진행합니다.

- HTTPS 적용 (도메인 + Nginx + Let's Encrypt)
- ECS/Fargate 등 컨테이너 오케스트레이션으로 전환
- Flyway/Liquibase 마이그레이션 도구 도입 (도입 시 `application-prod.yml`의 `ddl-auto`를 `validate`로 전환)
