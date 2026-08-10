package com.roompick.global.common.s3;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * 숙소/객실 이미지를 S3에 업로드하는 공용 컴포넌트입니다.
 */
public interface ImageUploader {

    /**
     * 이미지 파일을 업로드하고 접근 가능한 URL을 반환합니다.
     */
    String upload(MultipartFile file, String directory);

    /**
     * 여러 이미지 파일을 업로드하고 접근 가능한 URL 목록을 반환합니다.
     *
     * 파일 검증은 업로드를 시작하기 전에 전부 먼저 수행하며,
     * 일부 파일 업로드 후 나머지가 실패하면 이미 업로드된 파일을 정리하고 예외를 던집니다.
     */
    List<String> uploadAll(List<MultipartFile> files, String directory);

    /**
     * 업로드된 이미지를 삭제합니다. 존재하지 않는 URL은 무시합니다.
     */
    void delete(String imageUrl);

    /**
     * 업로드된 여러 이미지를 삭제합니다. 존재하지 않는 URL은 무시합니다.
     */
    void deleteAll(List<String> imageUrls);
}
