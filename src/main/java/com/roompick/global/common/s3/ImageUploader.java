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
     */
    List<String> uploadAll(List<MultipartFile> files, String directory);
}
