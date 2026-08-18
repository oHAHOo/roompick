package com.roompick.domain.admin.specialoffer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.admin.specialoffer.dto.SpecialOfferCreateRequestDto;
import com.roompick.domain.admin.specialoffer.dto.SpecialOfferCreateResponseDto;
import com.roompick.domain.admin.specialoffer.facade.AdminSpecialOfferFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/accommodations/" + "{accommodationId}/rooms/{roomId}/special-offers")
public class AdminSpecialOfferController {

    private final AdminSpecialOfferFacade adminSpecialOfferFacade;

    @PostMapping
    public ResponseEntity<ApiResponseDto<SpecialOfferCreateResponseDto>> createSpecialOffer(
        @PathVariable Long accommodationId, @PathVariable Long roomId,
        @Valid @RequestBody SpecialOfferCreateRequestDto request
    ) {
        SpecialOfferCreateResponseDto response = adminSpecialOfferFacade.create(accommodationId, roomId, request);

        ResponseEntity<ApiResponseDto<SpecialOfferCreateResponseDto>> result =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "특가 상품이 등록되었습니다.",
                        response
                    )
                );
        return result;
    }


}
