# RoomPick 내부 정책 문서

- 문서 버전: `v0.1`
- 작성일: 2026-07-24
- 최종 수정일: 2026-07-24
- 문서 목적: 기능별 비즈니스 규칙과 역할(Role) 기반 접근 권한을 실제 코드 기준으로 정리한다.

이 디렉터리의 문서는 `docs/MVP_CONTEXT.md` 8절 "최소 비즈니스 규칙 초안"에서 시작된 정책을
기능 단위로 분리하고, 실제 구현(Service·Entity·SecurityConfig)과 계속 일치시키기 위한
문서다. 기획 배경과 아직 확정되지 않은 항목은 `docs/MVP_CONTEXT.md`를 따르고, "지금 코드가
실제로 어떻게 동작하는가"는 이 디렉터리를 기준으로 판단한다.

---

## 1. 문서 구성

| 문서 | 담당 영역 | 담당자 |
| --- | --- | --- |
| [`ACCESS_CONTROL_POLICY.md`](ACCESS_CONTROL_POLICY.md) | 역할 정의, 인증·인가 공통 규칙 | oHAHOo |
| [`MEMBER_POLICY.md`](MEMBER_POLICY.md) | 회원가입, 로그인 | oHAHOo |
| [`ACCOMMODATION_POLICY.md`](ACCOMMODATION_POLICY.md) | 숙소 등록·상태 | IMSUN9 (관리자 등록 유스케이스: minjae123123) |
| [`ROOM_POLICY.md`](ROOM_POLICY.md) | 객실 등록·가격·인원·상태 | IMSUN9 (관리자 등록 유스케이스: minjae123123) |
| [`RESERVATION_POLICY.md`](RESERVATION_POLICY.md) | 예약 생성·조회·취소, 숙박 기간·인원 검증 | IMSUN9 |
| [`PAYMENT_POLICY.md`](PAYMENT_POLICY.md) | 결제 상태·정합성 (DB 설계만 완료, Service 미구현) | minjae123123 |

담당 영역과 PR 규칙은 `AGENTS.md`와 `README.md`의 "담당 영역"을 따른다. 공통 파일이나 다른
담당자의 정책 문서를 수정해야 하면 PR에 이유와 영향 범위를 명시한다.

---

## 2. 문서 작성 규칙

- 각 정책 문서는 "접근 권한"과 "비즈니스 규칙"을 분리해서 정리한다.
- 규칙마다 실제로 검증하는 코드 위치(클래스·메서드)와 관련 에러 코드(`ErrorCode`)를 함께 적는다.
- 코드로 아직 구현되지 않았지만 팀이 합의한 규칙은 "미구현" 표시를 남긴다.
- 아직 팀 합의가 끝나지 않은 항목은 결정된 것처럼 쓰지 않고 "미확정"으로 표시한다.
- 코드가 바뀌어 정책이 바뀌면 같은 PR에서 관련 문서도 함께 갱신한다(`AGENTS.md` 작업 완료 조건 참고).

---

## 3. 공통 응답 규칙

- 에러 응답 형식과 에러 코드 목록은 `com.roompick.global.common.ErrorCode`, `GlobalExceptionHandler`,
  `ErrorResponseDto`를 따른다.
- 성공 응답 형식은 `com.roompick.global.common.ApiResponseDto`를 따른다.
- 상세 요청·응답 스키마는 이 디렉터리가 아니라 `docs/API_SPEC_*.md`에서 관리한다. 이 디렉터리는
  "왜 그 응답이 나오는가"에 해당하는 내부 정책만 다룬다.
