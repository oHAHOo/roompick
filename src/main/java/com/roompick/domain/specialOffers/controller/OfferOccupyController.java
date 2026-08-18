package com.roompick.domain.specialOffers.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.specialOffers.dto.OfferOccupyResponseDto;
import com.roompick.domain.specialOffers.dto.OfferOccupyStatusResponseDto;
import com.roompick.domain.specialOffers.facade.OfferOccupyFacade;
import com.roompick.global.common.ApiResponseDto;
import com.roompick.global.security.AuthMember;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/special-offers/{offerId}/occupy-requests")
public class OfferOccupyController {

    private final OfferOccupyFacade offerOccupyFacade;

    @PostMapping
    public ResponseEntity<ApiResponseDto<OfferOccupyResponseDto>> requestOccupy(
        @AuthenticationPrincipal AuthMember authMember,
        @PathVariable Long offerId
    ) {
        OfferOccupyResponseDto response = offerOccupyFacade.requestOccupy(
            authMember.memberId(),
            offerId
        );

        ResponseEntity<ApiResponseDto<OfferOccupyResponseDto>> result =
            ResponseEntity.status(HttpStatus.ACCEPTED).body(
                ApiResponseDto.success(
                    "점유 요청이 접수되었습니다. 처리 결과는 상태 조회 API로 확인해주세요.", response
                )
            );

        return result;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<OfferOccupyStatusResponseDto>> getMyOccupyStatus(
        @AuthenticationPrincipal AuthMember authMember,
        @PathVariable Long offerId
    ) {
        OfferOccupyStatusResponseDto response = offerOccupyFacade.getMyOccupyStatus(
            authMember.memberId(),
            offerId
        );

        ResponseEntity<ApiResponseDto<OfferOccupyStatusResponseDto>> result =
            ResponseEntity.status(HttpStatus.OK).body(
                ApiResponseDto.success(
                    "점유 요청 상태 조회에 성공했습니다.", response)
            );

        return result;
    }

}
