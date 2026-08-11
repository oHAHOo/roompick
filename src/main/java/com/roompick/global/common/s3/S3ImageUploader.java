package com.roompick.global.common.s3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.s3.S3Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ImageUploader implements ImageUploader {

    private static final int MAX_IMAGE_COUNT = 10;
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    // 선언된 Content-Type별로 실제 파일 바이트가 시작해야 하는 매직 넘버입니다.
    // MultipartFile의 Content-Type은 요청자가 임의로 지정할 수 있으므로,
    // 실제 파일 내용의 시그니처까지 함께 확인합니다.
    private static final Map<String, byte[]> MAGIC_NUMBERS_BY_CONTENT_TYPE = Map.of(
        "image/jpeg", new byte[] {(byte)0xFF, (byte)0xD8, (byte)0xFF},
        "image/png", new byte[] {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
    );
    private static final byte[] WEBP_RIFF_SIGNATURE = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_FORMAT_SIGNATURE = {0x57, 0x45, 0x42, 0x50};

    private final S3Client s3Client;
    private final S3Properties properties;
    private final CloudFrontClient cloudFrontClient;

    @Override
    public String upload(MultipartFile file, String directory) {
        byte[] content = readValidated(file);
        return uploadContent(content, file.getContentType(), file.getOriginalFilename(), directory);
    }

    @Override
    public List<String> uploadAll(List<MultipartFile> files, String directory) {
        if (files.size() > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_COUNT_EXCEEDED);
        }

        // 일부 파일만 검증에 실패해 이미 업로드된 앞선 파일이 남지 않도록,
        // 업로드를 시작하기 전에 모든 파일을 먼저 읽고 검증합니다.
        List<byte[]> contents = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            contents.add(readValidated(file));
        }

        List<String> uploadedUrls = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                uploadedUrls.add(
                    uploadContent(contents.get(i), file.getContentType(), file.getOriginalFilename(), directory)
                );
            }
        } catch (RuntimeException e) {
            deleteAll(uploadedUrls);
            throw e;
        }

        return uploadedUrls;
    }

    @Override
    public void delete(String imageUrl) {
        String key = extractKey(imageUrl);
        try {
            s3Client.deleteObject(builder -> builder
                .bucket(properties.bucket())
                .key(key));
        } catch (SdkException e) {
            log.warn("S3 이미지 삭제에 실패했습니다. imageUrl={}", imageUrl, e);
        }
        invalidateCache(List.of(key));
    }

    @Override
    public void deleteAll(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        List<String> keys = imageUrls.stream()
            .map(this::extractKey)
            .toList();

        try {
            s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(properties.bucket())
                    .delete(Delete.builder()
                        .objects(keys.stream().map(key ->
                            ObjectIdentifier.builder().key(key).build()).toList())
                        .build())
                    .build()
            );
        } catch (SdkException e) {
            log.warn("S3 이미지 일괄 삭제에 실패했습니다. imageUrls={}", imageUrls, e);
        }
        invalidateCache(keys);
    }

    private byte[] readValidated(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (!MAGIC_NUMBERS_BY_CONTENT_TYPE.containsKey(file.getContentType())
            && !"image/webp".equals(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }

        if (!matchesDeclaredType(content, file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        return content;
    }

    private boolean matchesDeclaredType(byte[] content, String contentType) {
        if ("image/webp".equals(contentType)) {
            return startsWith(content, WEBP_RIFF_SIGNATURE)
                && content.length >= 12
                && matches(content, 8, WEBP_FORMAT_SIGNATURE);
        }

        byte[] signature = MAGIC_NUMBERS_BY_CONTENT_TYPE.get(contentType);
        return signature != null && startsWith(content, signature);
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return matches(content, 0, signature);
    }

    private boolean matches(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private String uploadContent(byte[] content, String contentType, String originalFilename, String directory) {
        String key = buildKey(directory, originalFilename);

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(content)
            );
        } catch (SdkException e) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED, e);
        }

        return buildUrl(key);
    }

    private String buildKey(String directory, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return directory + "/" + UUID.randomUUID() + extension;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
    }

    private String buildUrl(String key) {
        if (properties.cdnDomain() != null && !properties.cdnDomain().isBlank()) {
            return "https://%s/%s".formatted(properties.cdnDomain(), key);
        }
        return "https://%s.s3.%s.amazonaws.com/%s"
            .formatted(properties.bucket(), properties.region(), key);
    }

    private String extractKey(String imageUrl) {
        if (properties.cdnDomain() != null && !properties.cdnDomain().isBlank()) {
            String cdnPrefix = "https://%s/".formatted(properties.cdnDomain());
            if (imageUrl.startsWith(cdnPrefix)) {
                return imageUrl.substring(cdnPrefix.length());
            }
        }
        String prefix = "https://%s.s3.%s.amazonaws.com/"
            .formatted(properties.bucket(), properties.region());
        return imageUrl.startsWith(prefix)
            ? imageUrl.substring(prefix.length())
            : imageUrl;
    }

    private void invalidateCache(List<String> keys) {
        if (properties.cdnDistributionId() == null ||
            properties.cdnDistributionId().isBlank()) {
            return;
        }

        List<String> paths = keys.stream().map(key -> "/" + key).toList();
        try {
            cloudFrontClient.createInvalidation(
                CreateInvalidationRequest.builder()
                    .distributionId(properties.cdnDistributionId())
                    .invalidationBatch(
                        InvalidationBatch.builder()
                            .callerReference(UUID.randomUUID().toString())
                            .paths(Paths.builder()
                                .quantity(paths.size())
                                .items(paths)
                                .build())
                            .build()
                    ).build()
            );
        } catch (SdkException e) {
            log.warn("CloudFront 캐시 무효화에 실패했습니다. paths={}", paths, e);
        }
    }
}
