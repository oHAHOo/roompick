# RoomPick AI 작업 지침

이 파일은 Codex, ChatGPT, Claude 등 프로젝트를 돕는 AI가 가장 먼저 읽어야 하는 문서입니다.

## 작업 전 필수 확인

1. `docs/MVP_CONTEXT.md`
2. `docs/CODE_CONVENTION.md`
3. `docs/ERD.md`
4. 변경 대상 API가 있다면 `docs/API_SPEC_OWNER.md`

## 고정된 MVP 범위

- 서비스는 숙박 예약 애플리케이션이다.
- 숙소 1개와 실제 객실 1개의 더미 데이터만 사용한다.
- 검색 기능은 구현하지 않는다.
- 핵심 흐름은 객실 확인 → 예약 → 결제 → 예약 확인·취소이다.
- MVP 밖의 기능을 임의로 추가하지 않는다.

## 고정된 코드 규칙

- 호출 방향은 `Controller → Facade → Service → Repository`이다.
- DTO는 Java `record`를 우선 사용하고 이름 끝에 `Dto`를 붙인다.
- Entity 생성은 정적 팩토리 메서드를 사용한다.
- Entity에 public setter나 Lombok `@Builder`를 사용하지 않는다.
- Controller는 `ResponseEntity` 변수를 만든 뒤 반환한다.
- 트랜잭션은 Service 계층에서 시작한다.
- 읽기 전용 로직에는 `@Transactional(readOnly = true)`를 사용한다.
- 네이버 Java 코딩 컨벤션과 프로젝트의 EditorConfig를 따른다.

## 도메인 담당자

- IMSUN9: accommodation, room, reservation, integration
- minjae123123: payment
- oHAHOo: member, auth, security

공통 파일이나 다른 담당자의 영역을 변경해야 하면 PR에 이유와 영향 범위를 명시한다.

## 작업 완료 조건

- 관련 테스트를 추가하거나 변경한다.
- `./gradlew test`를 통과시킨다.
- API, ERD 또는 정책이 바뀌면 `docs`도 함께 갱신한다.
- 비밀값, 토큰, 실제 비밀번호를 커밋하지 않는다.
