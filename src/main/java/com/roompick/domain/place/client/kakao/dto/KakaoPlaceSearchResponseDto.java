package com.roompick.domain.place.client.kakao.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kakao Local 장소 키워드 검색 응답입니다.
 *
 * 장소 후보 변환에 필요한 documents만 역직렬화합니다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPlaceSearchResponseDto(
    List<KakaoPlaceDocumentDto> documents
) {
}
