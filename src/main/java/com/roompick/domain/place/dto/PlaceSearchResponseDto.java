package com.roompick.domain.place.dto;

import com.roompick.domain.place.model.PlaceSearchCandidate;

/**
 * 장소 검색 화면에 필요한 후보 장소와 좌표를 반환하는 응답 DTO입니다.
 */
public record PlaceSearchResponseDto(

    String placeId,

    String name,

    String address,

    String roadAddress,

    double latitude,

    double longitude,

    String category

) {

    /**
     * 제공자 중립 장소 검색 후보를 공개 응답 DTO로 변환합니다.
     */
    public static PlaceSearchResponseDto from(
        PlaceSearchCandidate candidate
    ) {
        return new PlaceSearchResponseDto(
            candidate.placeId(),
            candidate.name(),
            candidate.address(),
            candidate.roadAddress(),
            candidate.latitude(),
            candidate.longitude(),
            candidate.category()
        );
    }
}
