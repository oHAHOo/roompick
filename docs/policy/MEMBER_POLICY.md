# 회원 정책

- 문서 버전: `v0.1`
- 최종 수정일: 2026-07-24
- 담당자: oHAHOo
- 근거 코드: `Member`, `MemberService`, `MemberRole`

역할 정의와 인증 방식은 [`ACCESS_CONTROL_POLICY.md`](ACCESS_CONTROL_POLICY.md)를 따른다.
이 문서는 회원가입·로그인의 비즈니스 규칙만 다룬다.

---

## 1. 접근 권한

| 동작 | 필요 권한 |
| --- | --- |
| 회원가입 | 없음(비로그인) |
| 로그인 | 없음(비로그인) |

## 2. 회원가입 규칙

- 이메일은 중복될 수 없다. 이미 가입된 이메일이면 `DUPLICATED_EMAIL`(`400`)을 반환한다.
  - 구현: `MemberService.signup()` → `validateEmailNotDuplicated()`
- 비밀번호는 `BCryptPasswordEncoder`로 암호화하여 저장하며, 평문 비밀번호는 저장하지 않는다.
- 가입 시 역할은 항상 `USER`이며, 요청으로 `ADMIN`을 선택할 수 없다(`MemberService.signup()`이
  역할 파라미터를 받지 않음).

## 3. 로그인 규칙

- 이메일이 존재하지 않거나 비밀번호가 일치하지 않으면 동일하게 `INVALID_LOGIN`(`401`)을
  반환한다. 이메일 존재 여부와 비밀번호 오류를 구분해서 응답하지 않는다(계정 존재 여부 노출 방지).
  - 구현: `MemberService.authenticate()`

## 4. 미확정 항목

- 최초 관리자(`ADMIN`) 계정 준비 방식은 아직 결정되지 않았다. 자세한 내용은
  [`ACCESS_CONTROL_POLICY.md`](ACCESS_CONTROL_POLICY.md) 1절 참고.
