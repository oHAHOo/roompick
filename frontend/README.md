# RoomPick Frontend (Vanilla JS)

빌드 도구 없이 순수 HTML/CSS/JS(ES Module)로 만든 정적 프론트엔드입니다. S3 정적 웹사이트 호스팅에
그대로 업로드해서 배포할 수 있습니다.

## 로컬에서 실행

ES Module과 `fetch`는 `file://`로 직접 열면 동작하지 않으므로, 로컬 정적 서버로 띄워야 합니다.

```bash
npx http-server frontend -p 5500 -c-1
```

브라우저에서 `http://localhost:5500`으로 접속합니다.

백엔드는 기본적으로 `http://localhost:8080`을 호출합니다. 백엔드 주소는 화면 어디에도 노출하지
않습니다 — 방문자가 볼 필요 없는 내부 설정이기 때문입니다. 다른 주소를 쓰려면 브라우저 콘솔에서
직접 바꿉니다.

```js
RoomPickConfig.setApiBase("http://<주소>:8080");
```

`localStorage`에 저장되며, 바꾼 뒤 새로고침하면 적용됩니다. 배포 환경에서 바꾸는 방법은 아래
"S3 배포" 참고.

백엔드 로컬 실행 시 `application.yml`의 `roompick.cors.allowed-origins` 기본값에 `http://localhost:5500`이
포함되어 있어 CORS 설정 없이 바로 붙습니다.

## S3 배포

1. 버킷을 정적 웹사이트 호스팅으로 설정하고 인덱스 문서를 `index.html`로 지정합니다.
2. `frontend/` 폴더 전체(js, css, index.html)를 버킷에 업로드합니다.
3. 백엔드의 `CORS_ALLOWED_ORIGINS` 환경변수에 S3 웹사이트 엔드포인트(또는 CloudFront 도메인)를 추가합니다.
4. 배포된 사이트에 아래처럼 `?apiBase=` 쿼리로 **한 번만** 접속해 백엔드 주소를 저장합니다.
   저장 후에는 파라미터 없이 방문해도 유지됩니다. 일반 방문자에게는 이 설정이 노출되지 않습니다.

   ```
   https://<S3 웹사이트 엔드포인트>/?apiBase=http://<EC2 퍼블릭 IP>:8080
   ```

해시 라우팅(`#/...`)을 사용하므로 S3에 별도의 SPA 리다이렉트 규칙을 설정할 필요가 없습니다.

> 재배포 시 브라우저가 예전 `style.css`·`js`를 계속 쓰는 경우가 있습니다. CloudFront를 쓰면
> 무효화(invalidation)를 걸고, S3 단독이면 오래 캐시되지 않도록 `Cache-Control`을 짧게 두세요.

## 백엔드 연동 시 주의점

- **예약 생성은 `Idempotency-Key` 헤더가 필수**입니다(`docs/RESERVATION_IDEMPOTENCY.md`).
  빠뜨리면 `400 INVALID_INPUT_VALUE`가 납니다. 다른 도메인에서 호출하려면 백엔드 CORS의
  `allowedHeaders`에도 이 헤더가 있어야 합니다(`SecurityConfig`).
- **로그아웃은 body에 `refreshToken`을 함께 보내야** access·refresh 토큰이 모두 블랙리스트에
  등록됩니다. 빠뜨리면 `400`이 나고 토큰이 무효화되지 않습니다.
- 결제 준비(`POST /reservations/{id}/payments`)는 예약당 한 번만 가능하고 결제 정보를 다시
  조회하는 API가 없어서, 준비에 성공한 `paymentId`를 `sessionStorage`에 보관해 재사용합니다.

## 로컬에서 백엔드 띄우기 / 외부 API 키

```bash
docker compose up -d mysql redis
bash frontend/run-local-backend.sh
```

Elasticsearch와 Kafka는 없어도 됩니다(`ACCOMMODATION_LOCATION_SEARCH_ENGINE=MYSQL`로 ES 자동구성을
제외하고, Kafka는 연결 실패해도 앱이 뜹니다).

스크립트가 프로젝트 루트의 `.env`를 먼저 읽고 **없는 값만 더미로 채웁니다.** 더미 상태에서는
아래 기능이 실패합니다.

| 환경변수 | 더미일 때 실패하는 기능 |
| --- | --- |
| `KAKAO_REST_API_KEY` | 장소 검색 → `502 PLACE_API_AUTHENTICATION_FAILED` |
| `AWS_S3_ACCESS_KEY`·`AWS_S3_SECRET_KEY` | 이미지 첨부 등록 → `502 IMAGE_004` |
| `PORTONE_*` | 실제 PG 결제 (Mock 결제는 영향 없음) |

실제 키를 쓰려면 `.env`에 넣으면 됩니다(`.env`는 gitignore 대상입니다).

```
KAKAO_REST_API_KEY=<본인 Kakao REST API 키>
```

넣고 백엔드를 다시 띄우면 장소 검색이 동작합니다. 좌표 기반 주변 숙소 검색
(`/accommodations/search`)은 DB만 쓰므로 키 없이도 정상 동작합니다.

## 로컬 데모용 이미지 채우기

이미지 업로드는 S3를 거치므로 실제 AWS 키가 없으면 `502 IMAGE_004`로 실패합니다. 또 이미지는
**등록(POST) 시점에만** 넣을 수 있고 나중에 추가하는 API가 없습니다.

로컬에서 화면만 채워보려면 아래 스크립트로 플레이스홀더 이미지 URL을 DB에 직접 넣습니다.
정식 업로드 경로를 우회하는 **로컬 개발 DB 전용**입니다.

```bash
python frontend/seed-demo-images.py
```

되돌리기:

```sql
DELETE FROM accommodation_images WHERE image_url LIKE 'https://picsum.photos/%';
DELETE FROM room_images          WHERE image_url LIKE 'https://picsum.photos/%';
```

## 알려진 제약 — 결제 완료 예약은 취소 불가

예약 취소는 **결제 전(`PENDING_PAYMENT`) 예약에서만** 동작합니다. 결제까지 마친
`CONFIRMED` 예약을 취소하면 서버가 `409 RESERVATION_NOT_CANCELABLE`로 거절합니다
(환불 연동이 아직 없기 때문. `docs/API_SPEC_OWNER.md` 13절의 "W1 구현 범위" 참고).

그래서 예약 상세 화면에서 취소 버튼은 결제 전 예약에만 노출하고, 결제 완료 예약에는
안내 문구만 보여줍니다. 환불 흐름이 붙으면 `CONFIRMED`도 취소 버튼 대상에 넣어야 합니다.

## 관리자의 INACTIVE 숙소·객실 조회

`GET /api/v1/accommodations/{id}`, `GET /api/v1/accommodations/{id}/rooms`,
`GET /api/v1/rooms/{id}`는 인증 없이도 호출할 수 있는 공개 API이지만, 프론트는 이 세
엔드포인트에 한해 `auth: true`로 Authorization 헤더를 함께 보냅니다(`js/api.js`의
`AccommodationApi.detail/rooms`, `RoomApi.detail`). 요청자가 `ADMIN`이면 서버가 ACTIVE
필터를 건너뛰고 INACTIVE 숙소·객실도 그대로 반환하며, 응답에 `status`가 포함됩니다.
ADMIN이 아니면 지금까지처럼 404/0건입니다 — 프론트가 조건부로 숨기는 게 아니라 서버가
애초에 데이터를 내려주지 않는 방식입니다.

숙소 상세 화면(`accommodationDetail.js`)의 관리자 패널은 이 `status`를 보고 "숙소 비공개"
또는 "다시 공개하기" 버튼을 전환해서 보여주고, `PATCH /admin/accommodations/{id}/status`로
왕복 전환할 수 있습니다(객실의 기존 상태 전환 API와 동일한 패턴). 다시 공개해도 비공개
전환 시 함께 내려간 객실은 자동으로 살아나지 않으므로, 객실 상세 화면(`roomDetail.js`)의
관리자 패널에서 각 객실을 따로 다시 공개해야 합니다.

## PortOne V2 실결제

`js/pages/payment.js`가 실제 PortOne 결제창(V2 브라우저 SDK)을 엽니다. `index.html`에서
`https://cdn.portone.io/v2/browser-sdk.js`를 불러오고, 결제 준비(`prepare`) 응답의
`storeId`·`channelKey`·`portOnePaymentId`를 그대로 `PortOne.requestPayment(...)`에 넘깁니다.
결제창에서 성공하면 `POST /payments/{id}/complete`를 호출해 서버가 PortOne API로 금액·상태를
다시 검증합니다(브라우저에서 보낸 금액은 신뢰하지 않음).

로컬 더미 PortOne 키(`local-dummy-not-used`)로는 결제창까지는 열리고 PortOne 서버가 실제로
`channelKey is not correct`로 거절하는 것까지 확인했지만, **끝까지 결제되는 성공 경로는
실제 PortOne 테스트 스토어 키가 있어야 검증 가능**합니다. 실제 PG 없이 흐름만 확인하려면
결제 화면의 "실제 PG 없이 상태만 테스트 (Mock)" 아코디언을 씁니다.

## 선착순 특가 참여 (`js/pages/specialOffer.js`)

`#/special-offers` (offerId 입력) → `#/special-offers/:offerId` (참여 신청 + 상태 폴링, WAIT
4초 간격 자동 갱신). 특가 상세를 조회하는 공개 API가 없어서 offerId를 이미 알고 있어야
들어올 수 있습니다 — 관리자가 특가를 등록하면서 알려준 번호를 입력하는 방식입니다.

특가 상세 조회 API가 따로 없어서, 진입 시 판매 중인 특가 목록(`GET /special-offers`)에서
이 offerId를 찾아 숙소명·객실명·대표 이미지·가격·숙박 날짜를 화면 상단에 보여줍니다(신청
여부·WAIT/HOLD 등 상태와 무관하게 항상 표시). 다만 특가가 이미 종료돼 목록에서 빠지면
(예: HOLD 이후 시간이 흘러 판매 종료) 이 정보를 다시 가져올 수 없어 생략됩니다. 실제
결제는 이 화면이 아닌, 위 객실 정보의 "객실 보기"로 이동해 같은 숙박 날짜로 일반 예약
화면에서 진행합니다.

**로컬에서 확인 시 주의**: 점유 요청(`POST .../occupy-requests`)은 Kafka로 비동기 처리됩니다.
로컬에 Kafka가 안 떠 있으면 약 60초 뒤 `500 COMMON_002`로 실패합니다(의도된 응답은
`503 OFFER_OCCUPY_PUBLISH_TIMEOUT`이어야 하는데, `OfferOccupyEventProducer.send()`의
`kafkaTemplate.send()` 자체가 메타데이터 조회 단계에서 최대 60초(`max.block.ms` 기본값)
동안 동기적으로 막히면서 `org.apache.kafka.common.errors.TimeoutException`을 던지고, 이건
`java.util.concurrent.TimeoutException`만 잡는 현재 catch 블록에 안 걸려서 그대로 500으로
샙니다). 프론트는 이 지연·에러를 그대로 화면에 보여주도록 만들어서 버튼이 멈춘 것처럼
보이지만, 결국 응답이 오면 정상적으로 복구됩니다. WAIT→HOLD로 실제 전환되는 정상 경로는
Kafka가 떠 있어야 확인할 수 있습니다.

## 타임세일·특가 할인 배지

숙소 상세 객실 목록, 객실 상세 페이지에서 `discountApplied`가 true면 할인율 배지 +
원가 취소선 + 할인가를 보여줍니다(`ui.js`의 `priceBlock`).

## 구조

- `js/api.js` — API 호출, 토큰 저장/자동 재발급
- `js/router.js` — 해시 기반 라우터
- `js/app.js` — 라우트 등록, 상단 네비게이션
- `js/pages/*.js` — 화면별 렌더링 로직 (숙소 목록/상세, 객실 상세·예약, 결제, 내 예약, 관리자)

관리자 화면(`js/pages/admin.js`)은 숙소 등록 / 객실 등록 / 특가 등록 / 타임세일 등록 4개 탭입니다.
객실 공개·비공개 전환은 관리자로 로그인한 상태에서 객실 화면의 관리자 패널에서 합니다.

## 담당 API 매핑

- 회원가입/로그인/로그아웃 — `docs/API_SPEC_MEMBER.md`
- 숙소/객실/예약/장소 검색 — `docs/API_SPEC_OWNER.md`
- 결제 — `docs/API_SPEC_PAYMENT.md`
- 관리자 숙소·객실 등록/상태 변경 — `docs/API_SPEC_ADMIN.md`
