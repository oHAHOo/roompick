# 접근 제어 정책 (역할 기반 권한)

- 문서 버전: `v0.2`
- 최종 수정일: 2026-08-13
- 담당자: oHAHOo
- 근거 코드: `MemberRole`, `AuthMember`, `SecurityConfig`, `JwtAuthenticationFilter`,
  `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`

---

## 1. 역할(Role) 정의

| 역할 | 값 | 설명 |
| --- | --- | --- |
| 일반 회원 | `USER` | 회원가입으로 생성되는 기본 권한 |
| 관리자 | `ADMIN` | 숙소·객실 등록 등 관리자 전용 API를 호출할 수 있는 권한 |

- 역할은 `MemberRole` enum(`USER`, `ADMIN`) 두 가지만 존재한다.
- 일반 회원가입 요청(`MemberService.signup`)은 역할 필드를 입력받지 않으며, 생성되는 회원은
  항상 `USER`다. `ADMIN` 권한을 요청 값으로 선택하는 경로는 없다.
- 최초 `ADMIN` 계정을 만드는 방법은 아직 팀 합의가 끝나지 않았다(`docs/MVP_CONTEXT.md` 12절
  "아직 팀 회의가 필요한 항목" 참고). 시드 데이터, DB 직접 수정, 별도 승격 API 등 여러 방식이
  가능하며 하나로 확정하지 않는다.

## 2. 인증 방식

- 인증은 JWT Access Token 기반이며 `Authorization: Bearer {accessToken}` 헤더로 전달한다.
- 인증된 사용자 정보는 `AuthMember(memberId, role)` 형태로 Controller에 주입되며, 회원 식별자는
  이 인증 컨텍스트에서만 가져온다. Request Body의 회원 ID 값은 신뢰하지 않는다.
- 인증 실패는 `JwtAuthenticationEntryPoint`가 `401 Unauthorized`로, 권한 부족은
  `JwtAccessDeniedHandler`가 `403 Forbidden`으로 응답한다.

## 3. 경로별 접근 규칙 (`SecurityConfig`)

| 경로 패턴 | 규칙 |
| --- | --- |
| `/api/v1/auth/signup`, `/api/v1/auth/login` | 인증 없이 호출 가능 |
| `/actuator/health`, `/actuator/info` | 인증 없이 호출 가능 |
| `GET /api/v1/accommodations/**`, `GET /api/v1/rooms/**` | 인증 없이 호출 가능(비로그인 사용자도 숙소·객실 조회 가능) |
| `GET /api/v1/places/**` | 인증 없이 호출 가능(비로그인 사용자도 장소 후보 검색 가능) |
| `/api/v1/admin/**` | `ADMIN` 역할만 호출 가능 |
| 그 외 모든 경로 | 인증된 사용자만 호출 가능 |

- 위 규칙은 URL 패턴 단위로 적용되며, 기능(도메인)별 세부 접근 권한은 각 정책 문서의
  "접근 권한" 절에서 다룬다.
- 새로운 API를 추가할 때 위 표에 해당하지 않는 경로는 기본적으로 "인증된 사용자만 호출 가능"
  규칙이 적용된다는 점을 감안해 설계한다.

## 4. 리소스 소유자 검증

URL 패턴만으로 표현할 수 없는 "본인 소유 리소스만 접근 가능" 같은 규칙은 Service 계층에서
개별적으로 검증한다. 이 검증은 `SecurityConfig`가 아니라 각 기능의 정책 문서에 있다.

- 예약 소유자 검증: [`RESERVATION_POLICY.md`](RESERVATION_POLICY.md) 참고.
