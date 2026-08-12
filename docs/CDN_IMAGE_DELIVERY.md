# 숙소·객실 이미지 CDN 배포 정책

## 1. 목적

숙소·객실 이미지를 S3 원본에서 직접 서빙하지 않고 CloudFront CDN을 경유하여
제공함으로써 조회 지연을 줄이고 S3 direct access 트래픽을 낮춥니다.

이미지가 삭제될 때는 CloudFront 캐시도 함께 무효화하여, 삭제된 이미지가
캐시에 남아 재노출되지 않도록 합니다.

---

## 2. 인프라 구성

- CloudFront 배포 이름: `roompick-images-cdn`
- Distribution ID: `E3PAC8MQVLGDZQ`
- 배포 도메인: `d27w0523hykv0h.cloudfront.net`
- 서비스 도메인: `images.roompick.ina3700.click`
- 원본(Origin): `roompick-images.s3.ap-northeast-2.amazonaws.com`
- 뷰어 프로토콜 정책: HTTP를 HTTPS로 리디렉션
- 캐시 정책: AWS 관리형 `Managed-CachingOptimized`

### 캐시(TTL) 정책

`Managed-CachingOptimized`는 AWS 관리형 캐시 정책으로 아래 값이 고정되어 있습니다.

| 항목 | 값 |
| --- | --- |
| Min TTL | 1초 |
| Default TTL | 86400초 (1일) |
| Max TTL | 31536000초 (365일) |

원본 응답에 `Cache-Control`/`Expires` 헤더가 없으면 Default TTL(1일)이 적용되고,
있으면 Min~Max TTL 범위 내에서 해당 헤더 값을 따릅니다. S3에 업로드하는
이미지 객체는 별도의 `Cache-Control` 헤더를 지정하지 않으므로 기본적으로
Default TTL(1일) 기준으로 캐시됩니다.

---

## 3. 애플리케이션 동작

### URL 생성

`S3ImageUploader.buildUrl()`은 `cdn-domain` 설정값이 있으면 CDN URL을,
없으면 S3 원본 URL을 반환합니다.

```yaml
roompick:
  s3:
    cdn-domain: images.roompick.ina3700.click
    cdn-distribution-id: E3PAC8MQVLGDZQ
```

- `cdn-domain`이 비어 있으면(local 등) 업로드 결과가 S3 원본 URL로 저장됩니다.
- prod에서는 두 값 모두 필수로 설정합니다.

### 삭제 시 캐시 무효화

이미지를 삭제할 때는 S3 객체 삭제가 성공한 경우에만 CloudFront invalidation을
요청합니다. S3 삭제가 실패했는데 캐시만 무효화하면, 다음 요청에서 CloudFront가
원본을 다시 조회해 삭제되지 않은 이미지가 재캐싱될 수 있기 때문입니다.

- 단건 삭제(`delete`): `deleteObject` 성공 시에만 invalidation 호출
- 일괄 삭제(`deleteAll`): `DeleteObjectsResponse.errors()`로 개별 실패 key를
  확인하고, 실제로 삭제에 성공한 key만 invalidation 대상으로 사용

`cdn-distribution-id`가 비어 있으면(local 등) invalidation 자체를 건너뜁니다.

### 기존(legacy) S3 URL 호환

CDN 적용 이전에 업로드되어 S3 원본 URL로 저장된 이미지도 삭제 시 key를
정상적으로 추출해 S3에서 삭제할 수 있도록 `extractKey()`가 CDN URL과 S3 원본
URL 형식을 모두 처리합니다.

DB에 이미 저장된 기존 S3 원본 URL 자체는 Flyway 마이그레이션
(`V10__migrate_legacy_s3_image_urls_to_cdn.sql`)으로 CDN URL로 일괄 전환합니다.

---

## 4. 운영 시 참고사항

- CDN 도메인이나 캐시 정책을 변경하면 이 문서도 함께 갱신합니다.
- CloudFront 콘솔에서 캐시 정책을 커스텀 정책으로 변경하는 경우 TTL 표를
  다시 확인해 갱신해야 합니다.
- 이미지 콘텐츠 자체를 교체(같은 key에 다른 파일 업로드)하는 케이스는 현재
  없으므로, TTL 만료 전 갱신이 필요하면 삭제 후 재업로드 시 수행되는
  invalidation 흐름을 따릅니다.
