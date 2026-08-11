# RoomPick API 명세서 — 조민재 담당

- 문서 버전: `v0.2`
- 작성일: 2026-07-22
- 최종 수정일: 2026-07-28
- 담당자: minjae123123
- 담당 기능: 관리자 숙소·객실 등록
- 협업 도메인: 숙소, 객실, 회원·인증·보안

이 문서는 RoomPick MVP의 관리자 전용 숙소·객실 등록 API와 팀원 간 구현 경계를 정의한다. 결제 API는 minjae123123 담당의 별도 명세에서 관리한다. 객실 상태의 의미와 날짜별 `SOLD_OUT` 계산 규칙은 `docs/policy/ROOM_POLICY.md`를 우선 기준으로 삼는다.

---

## 1. 설계 전제

1. API 기본 경로는 `/api/v1`을 사용한다.
2. 관리자 등록 API는 인증된 `ADMIN`만 호출할 수 있다.
3. 일반 회원가입의 기본 권한은 `USER`이며 요청으로 `ADMIN`을 선택할 수 없다.
4. 숙소의 생성 상태는 서버에서 `ACTIVE`로 정한다.
5. 객실의 생성 상태는 서버에서 `INACTIVE`로 정한다.
6. 관리자 ID와 상태는 Request Body로 받지 않는다.
7. 관리자가 `SOLD_OUT` 상태를 직접 지정하거나 변경하지 않는다.
8. `SOLD_OUT`은 사용자가 선택한 숙박 기간의 활성 예약 존재 여부로 계산한다.
9. 숙소·객실 수정·삭제·관리 목록과 검색은 MVP에서 제외한다.
10. Controller는 Facade만 호출하고, AdminFacade가 숙소·객실 Service를 조율한다.
11. 관리자 기능에서 숙소·객실 Repository를 직접 호출하지 않는다.
12. 객실 공개·비공개 상태는 관리자 상태 변경 API로 전환한다.

---

## 2. 공통 요청·권한 규칙

### Request Header

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

| 상황 | HTTP | Error Code |
| --- | --- | --- |
| 인증 정보 없음 | `401 Unauthorized` | `UNAUTHORIZED` |
| `USER` 권한으로 관리자 API 호출 | `403 Forbidden` | `FORBIDDEN` |
| `ADMIN` 권한으로 정상 요청 | API 처리 계속 | - |

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
  "code": "COMMON_004",
  "message": "접근 권한이 없습니다."
}
```

---

## 3. API 목록

| 번호 | Method | URL | 기능 | 인증 |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/v1/admin/accommodations` | 관리자 숙소 등록 | `ADMIN` 필요 |
| 2 | `POST` | `/api/v1/admin/accommodations/{accommodationId}/rooms` | 관리자 객실 등록 | `ADMIN` 필요 |
| 3 | `PATCH` | `/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}/status` | 관리자 객실 공개 상태 변경 | `ADMIN` 필요 |

---

# 관리자 숙소·객실 등록 API

## 4. 관리자 숙소 등록

인증된 관리자가 숙소를 등록한다. 생성된 숙소의 초기 상태는 `ACTIVE`이다.

이미지 업로드를 포함하기 위해 `multipart/form-data`로 요청한다. `images`는 여러 장 첨부할 수 있으며, 업로드 순서가 그대로 대표(썸네일) 순서가 된다(0번째가 대표 이미지).

### Request

```http
POST /api/v1/admin/accommodations
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| 폼 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `name` | `String` | O | 공백 제외 1자 이상, 최대 100자 |
| `address` | `String` | O | 공백 제외 1자 이상, 최대 255자 |
| `description` | `String` | X | 숙소 설명 |
| `checkInTime` | `LocalTime` | O | `HH:mm:ss` |
| `checkOutTime` | `LocalTime` | O | `HH:mm:ss` |
| `images` | `File[]` | X | jpg/png/webp, 파일당 최대 10MB, 최대 10장 |

`status`와 관리자 ID는 Request로 받지 않는다. 숙소 상태는 서버에서 `ACTIVE`로 정하고 관리자 여부는 인증 정보로 검증한다.

### Response — 201 Created

```json
{
  "success": true,
  "message": "숙소가 등록되었습니다.",
  "data": {
    "accommodationId": 1,
    "name": "룸픽 호텔",
    "address": "서울특별시 강남구 테헤란로 123",
    "description": "RoomPick MVP 예약 테스트를 위한 숙소입니다.",
    "checkInTime": "15:00:00",
    "checkOutTime": "11:00:00",
    "status": "ACTIVE",
    "imageUrls": [
      "https://images.roompick.ina3700.click/accommodations/....jpg"
    ]
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `ACCOMMODATION_NAME_REQUIRED` | 숙소명이 없거나 공백임 |
| `400` | `ACCOMMODATION_ADDRESS_REQUIRED` | 숙소 주소가 없거나 공백임 |
| `400` | `ACCOMMODATION_TIME_REQUIRED` | 체크인 또는 체크아웃 시간이 없음 |
| `400` | `UNSUPPORTED_IMAGE_TYPE` | 첨부한 이미지 형식이 jpg/png/webp가 아님 |
| `400` | `IMAGE_SIZE_EXCEEDED` | 이미지 파일 용량이 10MB를 초과함 |
| `400` | `IMAGE_COUNT_EXCEEDED` | 첨부한 이미지가 10장을 초과함 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |
| `403` | `FORBIDDEN` | `ADMIN` 권한이 없는 회원의 요청 |
| `502` | `IMAGE_UPLOAD_FAILED` | S3 이미지 업로드 중 오류가 발생함 |

### 처리 순서

```text
인증 회원의 ADMIN 권한 확인
→ 요청 값 검증
→ 첨부 이미지 S3 업로드
→ ACTIVE 상태의 숙소 생성 및 이미지 URL 저장
→ 숙소 저장
→ 생성 결과 반환
```

S3는 DB 트랜잭션에 참여하지 않으므로, 이미지 업로드 이후 단계(숙소 생성·저장)가 실패하면 이미 업로드된 이미지를 서버가 직접 정리해 orphan object가 남지 않도록 한다.

---

## 5. 관리자 객실 등록

인증된 관리자가 기존 숙소에 실제 객실을 등록한다. 생성된 객실의 초기 상태는 `INACTIVE`이며, 공개 처리 전에는 사용자 화면에 노출되지 않는다.

이미지 업로드를 포함하기 위해 `multipart/form-data`로 요청한다. `images`는 여러 장 첨부할 수 있으며, 업로드 순서가 그대로 대표(썸네일) 순서가 된다(0번째가 대표 이미지).

### Request

```http
POST /api/v1/admin/accommodations/1/rooms
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `accommodationId` | `Long` | O | 객실을 등록할 숙소 ID |

### Request Field

| 폼 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `roomNumber` | `String` | O | 같은 숙소 안에서 중복 불가, 최대 30자 |
| `name` | `String` | O | 공백 제외 1자 이상, 최대 100자 |
| `description` | `String` | X | 객실 설명 |
| `pricePerNight` | `long` | O | 0원 이상 |
| `standardCapacity` | `int` | O | 1명 이상 |
| `maxCapacity` | `int` | O | 기준 인원 이상 |
| `images` | `File[]` | X | jpg/png/webp, 파일당 최대 10MB, 최대 10장 |

`status`는 Request로 받지 않고 서버에서 `INACTIVE`로 정한다. 관리자가 `SOLD_OUT`을 요청 값으로 전달할 수 없다.

### Response — 201 Created

```json
{
  "success": true,
  "message": "객실이 등록되었습니다.",
  "data": {
    "roomId": 1,
    "accommodationId": 1,
    "roomNumber": "101",
    "name": "디럭스 더블룸",
    "description": "2인이 이용할 수 있는 더블룸입니다.",
    "pricePerNight": 100000,
    "standardCapacity": 2,
    "maxCapacity": 2,
    "status": "INACTIVE",
    "imageUrls": [
      "https://images.roompick.ina3700.click/rooms/....jpg"
    ]
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `ROOM_NUMBER_REQUIRED` | 객실 번호가 없거나 공백임 |
| `400` | `ROOM_NAME_REQUIRED` | 객실명이 없거나 공백임 |
| `400` | `INVALID_ROOM_PRICE` | 1박 가격이 0원 미만임 |
| `400` | `INVALID_ROOM_CAPACITY` | 기준·최대 인원 조건이 올바르지 않음 |
| `400` | `UNSUPPORTED_IMAGE_TYPE` | 첨부한 이미지 형식이 jpg/png/webp가 아님 |
| `400` | `IMAGE_SIZE_EXCEEDED` | 이미지 파일 용량이 10MB를 초과함 |
| `400` | `IMAGE_COUNT_EXCEEDED` | 첨부한 이미지가 10장을 초과함 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |
| `403` | `FORBIDDEN` | `ADMIN` 권한이 없는 회원의 요청 |
| `404` | `ACCOMMODATION_NOT_FOUND` | 숙소가 존재하지 않음 |
| `409` | `ACCOMMODATION_INACTIVE` | 운영 중지된 숙소에 객실 등록을 요청함 |
| `409` | `ROOM_NUMBER_DUPLICATED` | 같은 숙소에 동일한 객실 번호가 이미 존재함 |
| `502` | `IMAGE_UPLOAD_FAILED` | S3 이미지 업로드 중 오류가 발생함 |

### 처리 순서

```text
인증 회원의 ADMIN 권한 확인
→ 요청 값 검증
→ 숙소 조회
→ 첨부 이미지 S3 업로드
→ 숙소 운영 상태 확인
→ 같은 숙소의 객실 번호 중복 확인
→ INACTIVE 상태의 객실 생성 및 이미지 URL 저장
→ 생성 결과 반환
```

S3는 DB 트랜잭션에 참여하지 않으므로, 이미지 업로드 이후 단계(숙소 상태 확인, 객실 번호 중복 확인, 객실 생성·저장)가 실패하면 이미 업로드된 이미지를 서버가 직접 정리해 orphan object가 남지 않도록 한다.

### 등록 이후 상태 정책

- 객실 등록 직후 사용자용 목록과 상세 조회에는 노출되지 않는다.
- 관리자가 공개 처리하면 `ACTIVE`로 전환하고 사용자용 조회 대상에 포함한다.
- 관리자가 운영을 중지하면 `INACTIVE`로 전환한다.
- `SOLD_OUT`은 관리자가 변경하는 상태가 아니다.
- `ACTIVE` 객실에 대해 사용자가 선택한 기간과 겹치는 활성 예약이 존재할 때만 화면에서 `SOLD_OUT`으로 표시한다.

---

## 6. 관리자 객실 공개 상태 변경

인증된 관리자가 지정한 숙소에 소속된 객실을 공개하거나 비공개로 변경한다.

### Request — ACTIVE

```http
PATCH /api/v1/admin/accommodations/1/rooms/10/status
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "status": "ACTIVE"
}
```

### Request — INACTIVE

```json
{
  "status": "INACTIVE"
}
```

`status`에는 `ACTIVE`, `INACTIVE`만 요청할 수 있다. `SOLD_OUT`은 날짜별 예약 데이터로 계산하는 화면 표시 상태이므로 요청할 수 없다.

### Response — 200 OK

```json
{
  "success": true,
  "message": "객실 상태가 변경되었습니다.",
  "data": {
    "roomId": 10,
    "status": "ACTIVE"
  }
}
```

### Error

| HTTP | Error Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_INPUT_VALUE` | `status`가 누락되거나 `ACTIVE`, `INACTIVE` 이외의 값인 경우 |
| `401` | `UNAUTHORIZED` | 인증되지 않은 요청 |
| `403` | `FORBIDDEN` | 일반 회원이 관리자 API를 호출한 경우 |
| `404` | `ROOM_NOT_FOUND` | 객실이 존재하지 않거나 URL의 숙소에 소속되지 않은 경우 |
| `409` | `ACCOMMODATION_INACTIVE` | 운영 중지된 숙소의 객실을 `ACTIVE`로 변경하려는 경우 |

### 상태 변경 정책

- 같은 상태를 다시 요청해도 `200 OK`를 반환하는 멱등 API다.
- 숙소가 `INACTIVE`이면 객실을 `ACTIVE`로 변경할 수 없다.
- 객실을 `INACTIVE`로 변경하는 것은 숙소 상태와 관계없이 허용한다.
- `PENDING_PAYMENT` 또는 `CONFIRMED` 예약이 있어도 객실을 `INACTIVE`로 변경할 수 있다.
- 객실 비공개 전환은 기존 예약을 취소하거나 변경하지 않으며 이후 신규 공개 조회와 신규 예약만 차단한다.
- 숙소 상태 변경 시 소속 객실 상태를 일괄 변경하지 않는다.

---

## 7. 담당자별 구현 경계

| 담당자 | 구현 범위 |
| --- | --- |
| minjae123123 | 관리자 Controller·Facade, 요청·응답 DTO, 관리자 등록 API 테스트 |
| IMSUN9 | Accommodation·Room Entity, Repository, Service와 도메인 테스트 |
| oHAHOo | `USER/ADMIN` 권한, 인증 객체, Security 설정과 접근 제어 테스트 |

호출 방향은 다음과 같다.

```text
AdminController
→ AdminFacade
   ├─ AccommodationService
   └─ RoomService
→ 각 도메인 Repository
```

- minjae123123은 관리자 유스케이스를 조율하지만 숙소·객실 Repository를 직접 사용하지 않는다.
- IMSUN9은 관리자 화면이나 인증 로직을 구현하지 않고 숙소·객실 생성 메서드와 도메인 규칙을 제공한다.
- oHAHOo는 일반 회원가입으로 `ADMIN` 권한을 획득할 수 없도록 하고 `/api/v1/admin/**` 접근을 제한한다.
- 다른 담당자의 공통 파일이나 도메인 코드를 변경하면 PR에 이유와 영향 범위를 작성한다.

---

## 8. 에러 코드 목록

| Error Code | HTTP | 메시지 초안 |
| --- | --- | --- |
| `UNAUTHORIZED` | `401` | 인증이 필요합니다. |
| `FORBIDDEN` | `403` | 접근 권한이 없습니다. |
| `ACCOMMODATION_NOT_FOUND` | `404` | 숙소를 찾을 수 없습니다. |
| `ACCOMMODATION_INACTIVE` | `409` | 운영 중지된 숙소에는 객실을 등록할 수 없습니다. |
| `ACCOMMODATION_NAME_REQUIRED` | `400` | 숙소명은 필수입니다. |
| `ACCOMMODATION_ADDRESS_REQUIRED` | `400` | 숙소 주소는 필수입니다. |
| `ACCOMMODATION_TIME_REQUIRED` | `400` | 체크인·체크아웃 시간은 필수입니다. |
| `ROOM_NUMBER_REQUIRED` | `400` | 객실 번호는 필수입니다. |
| `ROOM_NAME_REQUIRED` | `400` | 객실명은 필수입니다. |
| `INVALID_ROOM_PRICE` | `400` | 객실 가격은 0원 이상이어야 합니다. |
| `INVALID_ROOM_CAPACITY` | `400` | 객실 인원 조건이 올바르지 않습니다. |
| `ROOM_NUMBER_DUPLICATED` | `409` | 같은 숙소에 동일한 객실 번호가 존재합니다. |

---

## 9. 팀 회의에서 최종 확정할 항목

- [ ] 최초 관리자 계정을 어떤 방식으로 준비할지
- [ ] 실제 인증 객체에서 권한을 어떤 형태로 제공할지
- [ ] AdminFacade가 사용할 숙소·객실 Service 메서드 계약
- [ ] 결제 관리자 기능이 추가될 경우 같은 관리자 API 명세에 포함할지
