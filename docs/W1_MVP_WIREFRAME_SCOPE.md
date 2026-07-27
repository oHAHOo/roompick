# RoomPick W1 MVP 와이어프레임 범위

- 문서 버전: `v0.1`
- 작성일: 2026-07-27
- 기준 이슈: #50
- 관련 이슈: #2, #16, #20, #22

이 문서는 확정된 W1 MVP 와이어프레임을 기준으로 화면 흐름과 백엔드 API의 최소 구현 범위를 정리한다.

기존 W1 계획에서는 숙소 목록을 제외했지만, 와이어프레임의 첫 탐색 화면에 전체 숙소 목록과 숙소별 객실 목록이 포함되어 있어 화면 연결에 필요한 기본 조회 API를 #50에서 보완한다.

검색·필터·사용자 정렬과 같은 탐색 고도화는 기존 계획대로 W3 #20에서 진행한다.

---

## 1. W1 MVP 사용자 흐름

```text
회원가입 또는 로그인
→ 전체 숙소 목록
→ 선택한 숙소의 객실 목록
→ 객실 상세
→ 체크인·체크아웃·인원 선택
→ 예약 확인 및 예약 생성
→ 결제 준비
→ Mock 결제 성공 또는 실패
```

---

## 2. 화면과 API 연결

| 순서 | 화면 | 주요 API | 인증 | 담당 |
| --- | --- | --- | --- | --- |
| 1 | 로그인 | `POST /api/v1/auth/login` | 불필요 | 회원·인증 |
| 2 | 회원가입 | `POST /api/v1/auth/signup` | 불필요 | 회원·인증 |
| 3 | 전체 숙소 목록 | `GET /api/v1/accommodations` | 불필요 | 숙소 |
| 4 | 숙소별 객실 목록 | `GET /api/v1/accommodations/{accommodationId}/rooms` | 불필요 | 숙소·객실 |
| 5 | 객실 상세 | `GET /api/v1/rooms/{roomId}` | 불필요 | 객실 |
| 6 | 예약 가능 여부 | `GET /api/v1/rooms/{roomId}/availability` | 불필요 | 객실·예약 |
| 7 | 예약 확인·생성 | `POST /api/v1/reservations` | 필요 | 예약 |
| 8 | 결제 준비 | `POST /api/v1/reservations/{reservationId}/payments` | 필요 | 결제 |
| 9 | Mock 결제 승인 | `POST /api/v1/payments/{paymentId}/approve` | 필요 | 결제 |
| 10 | Mock 결제 실패 | `POST /api/v1/payments/{paymentId}/fail` | 필요 | 결제 |

---

## 3. W1 보완 API

### 3.1 전체 숙소 목록 조회

```http
GET /api/v1/accommodations?page=0&size=20
```

비로그인 사용자가 현재 공개 가능한 숙소 목록을 조회한다.

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 제약 |
| --- | --- | --- | --- | --- |
| `page` | `int` | X | `0` | 0 이상 |
| `size` | `int` | X | `20` | 1 이상, 최대 100 |

#### 목록 항목

```text
accommodationId
name
address
```

#### 응답 예시

```json
{
  "success": true,
  "message": "숙소 목록 조회에 성공했습니다.",
  "data": {
    "content": [
      {
        "accommodationId": 1,
        "name": "룸픽 호텔",
        "address": "서울특별시 강남구 테헤란로 123"
      },
      {
        "accommodationId": 2,
        "name": "룸픽 리조트",
        "address": "부산광역시 해운대구 해운대로 100"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 2,
    "totalPages": 1,
    "last": true
  }
}
```

#### 처리 규칙

- `ACTIVE` 상태의 숙소만 반환한다.
- 기본 정렬은 `accommodationId ASC`로 고정한다.
- 검색어, 지역, 날짜, 인원, 가격 조건은 받지 않는다.
- 조회 결과가 없으면 오류가 아닌 빈 `content`를 반환한다.
- 목록 DTO에 필요한 필드만 조회하여 불필요한 객실 연관관계 로딩을 피한다.
- 조회 전용 트랜잭션을 사용한다.

#### 이미지 처리

현재 숙소 이미지 모델이 없으므로 W1 API 응답에는 이미지 URL을 포함하지 않는다.

프론트엔드는 기본 플레이스홀더 이미지를 사용하며, 실제 이미지 업로드와 대표 이미지 관리는 W3 #22에서 구현한다.

---

### 3.2 숙소별 객실 목록 조회

```http
GET /api/v1/accommodations/{accommodationId}/rooms
```

비로그인 사용자가 선택한 숙소에 속한 공개 가능한 객실 목록을 조회한다.

#### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `accommodationId` | `Long` | O | 객실 목록을 조회할 숙소 ID |

#### 목록 항목

```text
roomId
name
pricePerNight
standardCapacity
maxCapacity
```

#### 응답 예시

```json
{
  "success": true,
  "message": "객실 목록 조회에 성공했습니다.",
  "data": [
    {
      "roomId": 1,
      "name": "디럭스 더블룸",
      "pricePerNight": 100000,
      "standardCapacity": 2,
      "maxCapacity": 2
    },
    {
      "roomId": 2,
      "name": "스탠다드 트윈룸",
      "pricePerNight": 80000,
      "standardCapacity": 2,
      "maxCapacity": 3
    }
  ]
}
```

#### 처리 규칙

- 숙소가 존재해야 한다.
- 공개 목록에는 `ACTIVE` 상태의 숙소와 객실만 사용한다.
- 기본 정렬은 `roomNumber ASC`, 동일 객실 번호에서는 `roomId ASC`로 고정한다.
- 객실 상세 설명과 숙소 전체 정보는 반환하지 않는다.
- 객실이 없으면 오류가 아닌 빈 배열을 반환한다.
- 목록 응답에 필요한 필드만 조회하여 불필요한 숙소·예약 연관관계 로딩을 피한다.
- 조회 전용 트랜잭션을 사용한다.

#### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `404` | `ACCOMMODATION_NOT_FOUND` | 숙소가 존재하지 않음 |

---

## 4. W1 구현 경계

### 포함

- 검색 조건 없는 숙소 목록
- 숙소 목록 기본 페이지네이션
- 숙소별 기본 객실 목록
- 비로그인 공개 조회
- `ACTIVE` 데이터만 노출
- 서버 고정 정렬
- 빈 목록 정상 응답
- Controller → Facade → Service → Repository 구조
- 목록 DTO 중심 조회와 N+1 방지
- 관련 단위·통합 테스트

### 제외

- 지역·숙소명 검색
- 날짜·인원·가격 필터
- 가격·평점·최신순 사용자 선택 정렬
- QueryDSL 복합 동적 쿼리
- 검색 결과 count 쿼리 고도화
- 숙소·객실 이미지 업로드
- 찜·리뷰·평점

---

## 5. 후속 고도화 연결

### W2 #16

다음 대표 흐름을 통합 테스트한다.

```text
관리자 숙소·객실 등록
→ 회원 로그인
→ 숙소 목록
→ 객실 목록·상세
→ 예약
→ 결제
→ 예약 조회·취소
```

### W3 #20

#50에서 만든 기본 숙소 목록 API를 다음 조건으로 확장한다.

- 지역·숙소명 검색
- 날짜·인원·가격 필터
- 가격·평점·최신순 정렬
- QueryDSL 동적 쿼리
- 페이지 및 count 쿼리 최적화

### W3 #22

- 숙소·객실 이미지 업로드
- 대표 이미지 선택
- 목록과 상세 응답의 이미지 URL 추가

---

## 6. 문서 동기화 규칙

#50 구현 PR에서는 다음 문서도 실제 코드와 함께 동기화한다.

- `docs/API_SPEC_OWNER.md`
- 관련 Controller 요청·응답 예시
- 테스트 시나리오

테이블 구조가 변경되지 않으므로 이번 범위에서는 ERD와 테이블 명세 변경이 필요하지 않다.
