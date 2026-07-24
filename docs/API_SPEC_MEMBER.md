# RoomPick API 명세서 — 황정후 담당

- 문서 버전: `v0.2`
- 작성일: 2026-07-22
- 최종 수정일: 2026-07-23
- 담당자: 황정후
- 담당 기능: 회원가입·로그인, 토큰 재발급·로그아웃, JWT 인증·인가
- 협업 도메인: 관리자(minjae123123), 숙소·객실·예약(임선구)

이 문서는 RoomPick MVP의 회원가입·로그인·토큰 재발급·로그아웃 API와 JWT 인증·인가 구조를 정의한다. 관리자 숙소·객실 등록 API는 minjae123123 담당의 `docs/API_SPEC_ADMIN.md`, 숙소·객실·예약 조회·예약 API는 임선구 담당의 `docs/API_SPEC_OWNER.md`에서 관리한다.

---

## 1. 설계 전제

1. API 기본 경로는 `/api/v1`을 사용한다.
2. 회원 인증 방식은 JWT로 확정한다. Access Token과 Refresh Token을 함께 발급하되 별도 토큰 테이블은 두지 않는다.
3. 로그아웃은 JWT + Redis 블랙리스트 방식으로 구현한다. 별도의 Refresh Token 저장소(화이트리스트)는 두지 않고, 무효화가 필요한 토큰만 블랙리스트에 등록한다.
4. Access Token 만료 시간은 30분(1800초), Refresh Token 만료 시간은 14일(1209600초)이다.
5. 일반 회원가입의 기본 권한은 `USER`이며 요청으로 `ADMIN`을 선택할 수 없다.
6. 회원 ID는 인증 컨텍스트(`AuthMember`)에서만 가져오고, Request Body로 받지 않는다.
7. Controller는 Facade만 호출하고, `AuthFacade`가 `MemberService`를 조율한다.
8. 비밀번호는 BCrypt로 단방향 암호화하여 저장하고 원문을 응답에 포함하지 않는다.
9. 모든 토큰(Access·Refresh)에는 블랙리스트 조회용 고유 식별자(`jti`)를 claim으로 포함한다.
10. Refresh Token은 재발급 시 1회용으로 소모된다. 사용된 Refresh Token은 즉시 블랙리스트에 등록해 재사용(재생 공격)을 차단한다.

---

## 2. 공통 요청·응답 규칙

### 성공 응답

```json
{
  "success": true,
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

### 실패 응답

```json
{
  "success": false,
  "code": "MEMBER_001",
  "message": "이미 사용 중인 이메일입니다."
}
```

---

## 3. API 목록

| 번호 | Method | URL | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/v1/auth/signup` | 회원가입 | 불필요 |
| 2 | `POST` | `/api/v1/auth/login` | 로그인 | 불필요 |
| 3 | `POST` | `/api/v1/auth/refresh` | Access/Refresh Token 재발급 | 불필요(Refresh Token 자체를 검증) |
| 4 | `POST` | `/api/v1/auth/logout` | 로그아웃(토큰 블랙리스트 등록) | 필요 |

---

## 4. 회원가입

이메일·비밀번호·이름으로 회원을 등록한다. 생성된 회원의 권한은 항상 `USER`이다.

### Request

```http
POST /api/v1/auth/signup
Content-Type: application/json
```

```json
{
  "email": "user@roompick.com",
  "password": "roompick1234",
  "name": "홍길동"
}
```

### Request Field

| 이름 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `email` | `String` | O | 이메일 형식, 공백 불가 |
| `password` | `String` | O | 영문과 숫자를 포함해 8자 이상 64자 이하 |
| `name` | `String` | O | 공백 제외 1자 이상, 최대 50자 |

### Response — 201 Created

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "memberId": 1,
    "email": "user@roompick.com",
    "name": "홍길동"
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_INPUT_VALUE` | 이메일 형식, 비밀번호 규칙, 이름 길이 등 요청 값 검증 실패 |
| `400` | `DUPLICATED_EMAIL` | 이미 가입된 이메일로 요청함 |

### 처리 순서

```text
요청 값 검증
→ 이메일 중복 확인
→ 비밀번호 BCrypt 암호화
→ USER 권한으로 회원 생성 및 저장
→ 생성 결과 반환
```

---

## 5. 로그인

이메일·비밀번호로 인증하고 Access Token과 Refresh Token을 발급한다.

### Request

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "user@roompick.com",
  "password": "roompick1234"
}
```

### Request Field

| 이름 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `email` | `String` | O | 이메일 형식, 공백 불가 |
| `password` | `String` | O | 공백 불가 |

### Response — 200 OK

응답 헤더와 본문에 동일한 Access Token을 포함한다.

```http
Authorization: Bearer {accessToken}
```

```json
{
  "success": true,
  "message": "로그인에 성공했습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer"
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_INPUT_VALUE` | 이메일 형식, 필수 값 누락 등 요청 값 검증 실패 |
| `401` | `INVALID_LOGIN` | 이메일이 존재하지 않거나 비밀번호가 일치하지 않음 |

### 처리 순서

```text
요청 값 검증
→ 이메일로 회원 조회
→ 비밀번호 일치 확인
→ Access/Refresh Token 발급
→ 토큰 반환
```

---

## 6. 토큰 재발급

Refresh Token으로 새 Access Token과 Refresh Token을 발급받는다. Refresh Token은 1회용이며, 사용된 토큰은 즉시 블랙리스트에 등록되어 재사용할 수 없다(재발급마다 회전).

### Request

```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Request Field

| 이름 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `refreshToken` | `String` | O | 로그인 또는 이전 재발급으로 받은 Refresh Token, 공백 불가 |

### Response — 200 OK

응답 헤더와 본문에 새로 발급된 Access Token을 포함한다.

```http
Authorization: Bearer {accessToken}
```

```json
{
  "success": true,
  "message": "토큰이 재발급되었습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer"
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_INPUT_VALUE` | `refreshToken` 누락 등 요청 값 검증 실패 |
| `401` | `INVALID_REFRESH_TOKEN` | 서명 불일치·만료, Access Token으로 잘못 요청, 이미 사용되어 블랙리스트에 등록된 토큰 |

### 처리 순서

```text
요청 값 검증
→ Refresh Token 서명·만료·타입(REFRESH) 검증
→ Redis 블랙리스트 조회로 재사용 여부 확인
→ 사용한 Refresh Token을 블랙리스트에 등록 (남은 만료 시간만큼 TTL 설정)
→ 새 Access/Refresh Token 발급
→ 토큰 반환
```

---

## 7. 로그아웃

인증된 회원이 현재 사용 중인 Access Token과 Refresh Token을 모두 무효화한다.

### Request

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

### Request Field

| 이름 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `refreshToken` | `String` | O | 함께 무효화할 Refresh Token, 공백 불가 |

### Response — 200 OK

```json
{
  "success": true,
  "message": "로그아웃되었습니다.",
  "data": null
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `401` | `UNAUTHORIZED` | Access Token 없음·형식 오류·만료·이미 블랙리스트에 등록됨 |
| `400` | `INVALID_INPUT_VALUE` | `refreshToken` 누락 |

### 처리 순서

```text
Access Token 인증 확인 (JwtAuthenticationFilter)
→ Access Token을 블랙리스트에 등록 (남은 만료 시간만큼 TTL 설정)
→ 전달받은 Refresh Token을 블랙리스트에 등록 (남은 만료 시간만큼 TTL 설정)
→ 로그아웃 완료 응답
```

---

## 8. 블랙리스트 구조 (Redis)

- 모든 토큰 발급 시 고유 식별자(`jti`)를 claim에 포함한다.
- 블랙리스트 키는 `blacklist:{jti}` 형태로 저장하고, TTL은 해당 토큰의 남은 만료 시간과 동일하게 설정해 Redis가 자동으로 정리하게 한다.
- `JwtAuthenticationFilter`는 서명·만료·타입 검증을 통과한 Access Token에 대해 추가로 블랙리스트 조회를 수행하고, 등록되어 있으면 인증하지 않는다(미인증 처리 → `401 UNAUTHORIZED`).
- `/api/v1/auth/refresh`도 동일한 블랙리스트 조회를 거쳐 이미 사용(또는 로그아웃)된 Refresh Token의 재사용을 차단한다.
- 토큰 원문을 Redis 키로 직접 사용하지 않고 `jti`만 저장하여 키 길이와 노출 범위를 최소화한다.

---

## 9. 인증·인가 공통 규칙

로그인 이후 API는 `Authorization: Bearer {accessToken}` 헤더로 인증한다.

| 상황 | HTTP | Error Code |
| --- | --- | --- |
| 토큰 없음·형식 오류·만료 | `401 Unauthorized` | `UNAUTHORIZED` |
| 토큰은 유효하나 블랙리스트에 등록됨(로그아웃되었거나 이미 사용된 Refresh Token) | `401 Unauthorized` | `UNAUTHORIZED` |
| 인증은 됐으나 권한 부족 (예: `USER`가 관리자 API 호출) | `403 Forbidden` | `FORBIDDEN` |

인증에 성공하면 `JwtAuthenticationFilter`가 토큰을 검증하고 `AuthMember(memberId, role)`를 인증 컨텍스트에 주입한다. 다른 도메인의 Controller는 `AuthMember`에서 회원 ID와 권한을 가져오고 Request Body로 받지 않는다.

---

## 10. 담당자별 구현 경계

| 담당자 | 구현 범위 |
| --- | --- |
| 황정후 | Member Entity·Repository, `AuthController`·`AuthFacade`·`MemberService`·`TokenService`, JWT 발급·검증, Redis 블랙리스트 연동, `SecurityConfig`, 인증·인가 예외 처리 |
| minjae123123 | 관리자 전용 API에서 `ADMIN` 권한 검증 사용 (`docs/API_SPEC_ADMIN.md`) |
| 임선구 | 예약 API에서 `AuthMember`로 회원 ID를 식별 (`docs/API_SPEC_OWNER.md`) |

- 황정후는 다른 도메인의 Entity·Repository를 직접 호출하지 않는다.
- 다른 담당자는 `AuthMember`를 통해서만 인증 정보를 사용하고, 회원 Repository와 블랙리스트 저장소를 직접 호출하지 않는다.

---

## 11. 에러 코드 목록

| Error Code | HTTP | 메시지 초안 |
| --- | --- | --- |
| `INVALID_INPUT_VALUE` | `400` | 요청 값이 올바르지 않습니다. |
| `UNAUTHORIZED` | `401` | 인증이 필요합니다. |
| `FORBIDDEN` | `403` | 접근 권한이 없습니다. |
| `DUPLICATED_EMAIL` | `400` | 이미 사용 중인 이메일입니다. |
| `INVALID_LOGIN` | `401` | 이메일 또는 비밀번호가 일치하지 않습니다. |
| `INVALID_REFRESH_TOKEN` | `401` | 유효하지 않은 리프레시 토큰입니다. |

---

## 12. 팀 회의에서 최종 확정할 항목

- [x] 회원 인증 방식을 JWT로 확정할지 → JWT로 확정
- [x] Refresh Token으로 Access Token을 재발급하는 API를 MVP에 포함할지 → 포함, 1회용 회전 방식
- [x] 로그아웃(JWT + Redis 블랙리스트)을 어느 시점에 구현할지 → 이번 버전(`v0.2`)에서 구현
- [x] 최초 관리자 계정을 준비하는 방식을 무엇으로 할지 → 별도 관리자 가입 API 없이 더미데이터로 준비하고, 필요 시 DB에서 직접 `role`을 수정한다
- [ ] 블랙리스트 Redis 인스턴스의 로컬·운영 배포 방식 (docker compose 서비스 추가 등)
