package com.roompick.domain.admin.accommodation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.admin.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.dto.response.AccommodationCreateResponseDto;
import com.roompick.domain.admin.facade.AdminAccommodationFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 숙소 등록 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/admin/accommodations")
@RequiredArgsConstructor
public class AdminAccommodationController {

    private final AdminAccommodationFacade adminAccommodationFacade;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDto<AccommodationCreateResponseDto>>
    createAccommodation(
        @Valid @RequestBody AccommodationCreateRequestDto request
    ) {

        AccommodationCreateResponseDto result =
            adminAccommodationFacade.createAccommodation(request);

        ResponseEntity<ApiResponseDto<AccommodationCreateResponseDto>> response =
            ResponseEntity.status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "숙소가 등록되었습니다.",
                        result
                    )
                );

        return response;
    }
}
