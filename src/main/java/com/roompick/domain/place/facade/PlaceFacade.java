package com.roompick.domain.place.facade;

import java.util.List;

import org.springframework.stereotype.Component;

import com.roompick.domain.place.dto.PlaceSearchResponseDto;
import com.roompick.domain.place.service.PlaceSearchService;

import lombok.RequiredArgsConstructor;

/**
 * 장소 검색 API 흐름을 조율하는 Facade입니다.
 */
@Component
@RequiredArgsConstructor
public class PlaceFacade {

    private final PlaceSearchService placeSearchService;

    /**
     * 장소 검색 요청을 Service에 전달하고 후보 장소 목록을 반환합니다.
     */
    public List<PlaceSearchResponseDto> searchPlaces(
        String query,
        int limit
    ) {
        return placeSearchService.searchPlaces(
            query,
            limit
        );
    }
}
