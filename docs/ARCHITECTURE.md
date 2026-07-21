# RoomPick 아키텍처

## 기본 호출 흐름

```text
HTTP 요청
  ↓
Controller
  ↓
Facade
  ↓
Service
  ↓
Repository
  ↓
Database
```

## 계층별 책임

- Controller: 요청 검증, DTO 변환, HTTP 응답 생성
- Facade: 여러 도메인 Service를 조합하는 유스케이스
- Service: 단일 도메인의 규칙과 트랜잭션 처리
- Repository: 영속성 접근

단일 도메인 조회처럼 조합할 로직이 없는 경우에도 팀의 일관성을 위해 Facade를 거칩니다.

## 패키지 구조

```text
com.roompick
├── domain
│   ├── accommodation
│   ├── room
│   ├── reservation
│   ├── payment
│   └── member
└── global
    ├── common
    └── config
```

각 도메인은 기능이 추가될 때 `controller`, `facade`, `service`, `repository`, `entity`, `dto` 하위 패키지를 만듭니다. 사용되지 않는 빈 계층은 미리 만들지 않습니다.

## 예약 가능 여부

별도 재고 테이블을 두지 않고 활성 예약의 날짜 중복 여부로 판단합니다.

```text
기존 체크인 < 요청 체크아웃
AND
기존 체크아웃 > 요청 체크인
```

취소된 예약은 중복 검사에서 제외합니다. 동시 예약 방지 전략은 예약 기능 구현 PR에서 확정하고 테스트와 선택 근거를 함께 기록합니다.
