package com.roompick.domain.admin.accommodation.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.roompick.domain.admin.accommodation.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;
import com.roompick.domain.admin.accommodation.facade.AdminAccommodationFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/accommodations")
public class AdminAccommodationController {

    private final AdminAccommodationFacade adminAccommodationFacade;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto<AccommodationCreateResponseDto>>
    createAccommodation(
        @Valid @ModelAttribute AccommodationCreateRequestDto request,
        @RequestParam(required = false)
        List<MultipartFile> images
    ) {

        AccommodationCreateResponseDto response =
            adminAccommodationFacade.createAccommodation(
                request,
                images
            );

        ResponseEntity<ApiResponseDto<AccommodationCreateResponseDto>> result =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "숙소가 등록되었습니다.",
                        response
                    )
                );

        return result;
    }

    @DeleteMapping("/{accommodationId}")
    public ResponseEntity<ApiResponseDto<Void>> deleteAccommodation(
        @PathVariable Long accommodationId
    ) {
        adminAccommodationFacade.deleteAccommodation(
            accommodationId
        );

        ResponseEntity<ApiResponseDto<Void>> result =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "숙소가 삭제되었습니다."
                    )
                );

        return result;
    }
}
