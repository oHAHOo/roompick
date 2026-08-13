package com.roompick.domain.place.client;

import java.util.List;

import com.roompick.domain.place.model.PlaceSearchCandidate;

/**
 * 외부 장소 검색 API를 통해 후보 장소를 조회하는 Client 계약입니다.
 *
 * 실제 외부 API 제공자와 통신 방식은 구현체 내부에 격리합니다.
 */
public interface PlaceSearchClient {

    /**
     * 검색어와 최대 결과 수를 기준으로 후보 장소를 조회합니다.
     */
    List<PlaceSearchCandidate> search(
        String query,
        int limit
    );
}
