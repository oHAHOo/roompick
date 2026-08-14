package com.roompick.domain.place.client.kakao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kakao Local 장소 키워드 검색 결과의 개별 장소 정보입니다.
 *
 * Kakao 응답의 x는 경도, y는 위도를 문자열로 제공합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPlaceDocumentDto(

    String id,

    @JsonProperty("place_name")
    String placeName,

    @JsonProperty("category_name")
    String categoryName,

    @JsonProperty("address_name")
    String addressName,

    @JsonProperty("road_address_name")
    String roadAddressName,

    String x,

    String y

) {
}
