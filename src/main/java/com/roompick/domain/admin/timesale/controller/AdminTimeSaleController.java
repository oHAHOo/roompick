package com.roompick.domain.admin.timesale.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.admin.timesale.dto.request.TimeSaleCreateRequestDto;
import com.roompick.domain.admin.timesale.dto.response.TimeSaleCreateResponseDto;
import com.roompick.domain.admin.timesale.facade.AdminTimeSaleFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자의 타임세일 등록 요청을 처리합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/api/v1/admin/accommodations/"
        + "{accommodationId}/time-sales"
)
public class AdminTimeSaleController {

    private final AdminTimeSaleFacade
        adminTimeSaleFacade;

    /**
     * 숙소 전체 또는 특정 객실에 적용할
     * 타임세일을 등록합니다.
     */
    @PostMapping
    public ResponseEntity<
        ApiResponseDto<TimeSaleCreateResponseDto>
        > createTimeSale(
        @PathVariable Long accommodationId,
        @Valid
        @RequestBody
        TimeSaleCreateRequestDto request
    ) {
        TimeSaleCreateResponseDto response =
            adminTimeSaleFacade.create(
                accommodationId,
                request
            );

        ResponseEntity<
            ApiResponseDto<TimeSaleCreateResponseDto>
            > result =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "타임세일이 등록되었습니다.",
                        response
                    )
                );

        return result;
    }
}
