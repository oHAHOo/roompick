package com.roompick.domain.admin.accommodation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.admin.accommodation.dto.request.AccommodationCreateRequestDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;
import com.roompick.domain.admin.accommodation.facade.AdminAccommodationFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/accommodations")
@RequiredArgsConstructor
public class AdminAccommodationController {

    private final AdminAccommodationFacade adminAccommodationFacade;

    @PostMapping
    public ResponseEntity<
        ApiResponseDto<AccommodationCreateResponseDto>
        > createAccommodation(
        @Valid
        @RequestBody
        AccommodationCreateRequestDto request
    ) {
        AccommodationCreateResponseDto response =
            adminAccommodationFacade.createAccommodation(
                request
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponseDto.success(
                    "숙소가 등록되었습니다.",
                    response
                )
            );
    }
}
