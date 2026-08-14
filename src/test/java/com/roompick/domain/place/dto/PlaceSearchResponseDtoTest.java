package com.roompick.domain.place.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roompick.domain.place.model.PlaceSearchCandidate;

/**
 * 제공자 중립 장소 후보의 공개 응답 변환을 검증합니다.
 */
class PlaceSearchResponseDtoTest {

    @Test
    @DisplayName("장소 검색 후보를 공개 응답 DTO로 변환한다")
    void convertCandidateToResponse() {
        // given
        PlaceSearchCandidate candidate =
            new PlaceSearchCandidate(
                "123456",
                "강남역 2호선",
                "서울 강남구 역삼동",
                "서울 강남구 강남대로 396",
                37.4979,
                127.0276,
                "교통,수송 > 지하철"
            );

        // when
        PlaceSearchResponseDto response =
            PlaceSearchResponseDto.from(candidate);

        // then
        assertThat(response.placeId()).isEqualTo("123456");
        assertThat(response.name()).isEqualTo("강남역 2호선");
        assertThat(response.address()).isEqualTo("서울 강남구 역삼동");
        assertThat(response.roadAddress())
            .isEqualTo("서울 강남구 강남대로 396");
        assertThat(response.latitude()).isEqualTo(37.4979);
        assertThat(response.longitude()).isEqualTo(127.0276);
        assertThat(response.category()).isEqualTo("교통,수송 > 지하철");
    }
}
