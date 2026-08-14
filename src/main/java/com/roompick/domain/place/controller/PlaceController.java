package com.roompick.domain.place.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.place.dto.PlaceSearchResponseDto;
import com.roompick.domain.place.facade.PlaceFacade;
import com.roompick.global.common.ApiResponseDto;

import lombok.RequiredArgsConstructor;

/**
 * 장소명으로 후보 장소와 좌표를 검색하는 공개 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceFacade placeFacade;

    /**
     * 장소 검색어와 최대 결과 수를 기준으로 후보 장소를 조회합니다.
     *
     * 검색 결과의 좌표는 기존 숙소 위치 검색 API에 전달할 수 있습니다.
     */
    @GetMapping("/search")
    public ResponseEntity<
        ApiResponseDto<List<PlaceSearchResponseDto>>
        > searchPlaces(
        @RequestParam(name = "query")
        String query,
        @RequestParam(
            name = "limit",
            defaultValue = "5"
        )
        int limit
    ) {
        List<PlaceSearchResponseDto> result =
            placeFacade.searchPlaces(
                query,
                limit
            );

        ResponseEntity<
            ApiResponseDto<List<PlaceSearchResponseDto>>
            > response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(
                    ApiResponseDto.success(
                        "장소 검색에 성공했습니다.",
                        result
                    )
                );

        return response;
    }
}
