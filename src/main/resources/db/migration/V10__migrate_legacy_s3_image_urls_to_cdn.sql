-- 기존에 저장된 S3 원본 URL을 CloudFront CDN URL로 전환합니다.
-- 예: https://<bucket>.s3.<region>.amazonaws.com/rooms/xxx.jpg
--  -> https://images.roompick.ina3700.click/rooms/xxx.jpg
UPDATE accommodation_images
SET image_url = REGEXP_REPLACE(
    image_url,
    '^https://[^/]+\\.s3\\.[^/]+\\.amazonaws\\.com/',
    'https://images.roompick.ina3700.click/'
)
WHERE image_url REGEXP '^https://[^/]+\\.s3\\.[^/]+\\.amazonaws\\.com/';

UPDATE room_images
SET image_url = REGEXP_REPLACE(
    image_url,
    '^https://[^/]+\\.s3\\.[^/]+\\.amazonaws\\.com/',
    'https://images.roompick.ina3700.click/'
)
WHERE image_url REGEXP '^https://[^/]+\\.s3\\.[^/]+\\.amazonaws\\.com/';
