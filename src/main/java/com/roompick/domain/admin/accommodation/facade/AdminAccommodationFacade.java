package com.roompick.domain.admin.accommodation.facade;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.accommodation.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;
import com.roompick.global.common.s3.ImageUploader;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminAccommodationFacade {

    private static final String IMAGE_DIRECTORY = "accommodations";

    private final AccommodationService accommodationService;
    private final ImageUploader imageUploader;

    public AccommodationCreateResponseDto createAccommodation(
        AccommodationCreateRequestDto request,
        List<MultipartFile> images
    ) {
        List<String> imageUrls =
            uploadImages(images);

        try {
            Accommodation accommodation =
                accommodationService.createAccommodation(
                    request.name(),
                    request.address(),
                    request.description(),
                    request.checkInTimeAsLocalTime(),
                    request.checkOutTimeAsLocalTime(),
                    imageUrls
                );

            return AccommodationCreateResponseDto.from(
                accommodation
            );
        } catch (RuntimeException e) {
            // S3는 DB 트랜잭션에 참여하지 않으므로, 업로드 이후 단계가
            // 실패하면 이미 올라간 이미지를 직접 정리해 orphan object를 남기지 않는다.
            imageUploader.deleteAll(imageUrls);
            throw e;
        }
    }

    private List<String> uploadImages(
        List<MultipartFile> images
    ) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }

        return imageUploader.uploadAll(
            images,
            IMAGE_DIRECTORY
        );
    }
}
