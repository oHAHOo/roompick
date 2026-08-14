package com.roompick.domain.place.model;

/**
 * 외부 장소 검색 결과를 애플리케이션 내부로 전달하는 후보 장소입니다.
 *
 * 특정 외부 API의 응답 타입을 Service 계층에 노출하지 않도록
 * 장소 검색 Client와 Service 사이의 제공자 중립 모델로 사용합니다.
 */
public record PlaceSearchCandidate(

    String placeId,

    String name,

    String address,

    String roadAddress,

    double latitude,

    double longitude,

    String category

) {
}
