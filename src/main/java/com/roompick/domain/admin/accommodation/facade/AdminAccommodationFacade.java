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
