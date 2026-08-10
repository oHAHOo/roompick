package com.roompick.global.common.s3;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.s3.S3Properties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3ImageUploader implements ImageUploader {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
        Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

    private final S3Client s3Client;
    private final S3Properties properties;

    @Override
    public String upload(MultipartFile file, String directory) {
        validate(file);

        String key = buildKey(directory, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .build(),
                RequestBody.fromInputStream(inputStream, file.getSize())
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }

        return buildUrl(key);
    }

    @Override
    public List<String> uploadAll(List<MultipartFile> files, String directory) {
        return files.stream()
            .map(file -> upload(file, directory))
            .toList();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE_FILE);
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }
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
        return "https://%s.s3.%s.amazonaws.com/%s"
            .formatted(properties.bucket(), properties.region(), key);
    }
}
