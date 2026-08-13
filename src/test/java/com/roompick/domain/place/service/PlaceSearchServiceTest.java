package com.roompick.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.place.client.PlaceSearchClient;
import com.roompick.domain.place.dto.PlaceSearchResponseDto;
import com.roompick.domain.place.model.PlaceSearchCandidate;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 장소 검색 조건 검증, 외부 Client 호출과 응답 변환을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    private PlaceSearchClient placeSearchClient;

    @InjectMocks
    private PlaceSearchService placeSearchService;

    @Test
    @DisplayName("검색어로 후보 장소를 조회하고 공개 응답으로 변환한다")
    void searchPlacesSuccessfully() {
        // given
        PlaceSearchCandidate candidate = createCandidate();

        given(
            placeSearchClient.search(
                "강남역",
                5
            )
        ).willReturn(List.of(candidate));

        // when
        List<PlaceSearchResponseDto> responses =
            placeSearchService.searchPlaces(
                "강남역",
                5
            );

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).placeId()).isEqualTo("123456");
        assertThat(responses.get(0).latitude()).isEqualTo(37.4979);
        assertThat(responses.get(0).longitude()).isEqualTo(127.0276);

        then(placeSearchClient)
            .should()
            .search(
                "강남역",
                5
            );
    }

    @Test
    @DisplayName("검색어의 앞뒤 공백을 제거한 뒤 외부 Client를 호출한다")
    void trimQueryBeforeCallingClient() {
        // given
        given(
            placeSearchClient.search(
                "서울역",
                5
            )
        ).willReturn(List.of());

        // when
        placeSearchService.searchPlaces(
            "  서울역  ",
            5
        );

        // then
        then(placeSearchClient)
            .should()
            .search(
                "서울역",
                5
            );
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 목록을 반환한다")
    void returnEmptyListWhenNoPlaceMatches() {
        // given
        given(
            placeSearchClient.search(
                "존재하지않는장소",
                5
            )
        ).willReturn(List.of());

        // when
        List<PlaceSearchResponseDto> responses =
            placeSearchService.searchPlaces(
                "존재하지않는장소",
                5
            );

        // then
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("검색어가 null이면 외부 Client를 호출하지 않는다")
    void rejectNullQueryBeforeCallingClient() {
        assertInvalidQuery(null);
    }

    @Test
    @DisplayName("검색어가 공백이면 외부 Client를 호출하지 않는다")
    void rejectBlankQueryBeforeCallingClient() {
        assertInvalidQuery("   ");
    }

    @Test
    @DisplayName("limit이 1보다 작으면 외부 Client를 호출하지 않는다")
    void rejectLimitLessThanMinimum() {
        assertInvalidLimit(0);
    }

    @Test
    @DisplayName("limit이 15보다 크면 외부 Client를 호출하지 않는다")
    void rejectLimitGreaterThanMaximum() {
        assertInvalidLimit(16);
    }

    @Test
    @DisplayName("limit 경계값 1과 15를 허용한다")
    void allowLimitBoundaryValues() {
        // given
        given(
            placeSearchClient.search(
                "해운대",
                1
            )
        ).willReturn(List.of());

        given(
            placeSearchClient.search(
                "해운대",
                15
            )
        ).willReturn(List.of());

        // when
        placeSearchService.searchPlaces("해운대", 1);
        placeSearchService.searchPlaces("해운대", 15);

        // then
        then(placeSearchClient)
            .should()
            .search("해운대", 1);

        then(placeSearchClient)
            .should()
            .search("해운대", 15);
    }

    /**
     * 잘못된 검색어의 오류 정보와 Client 미호출을 검증합니다.
     */
    private void assertInvalidQuery(
        String query
    ) {
        assertThatThrownBy(
            () -> placeSearchService.searchPlaces(
                query,
                5
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

                assertThat(businessException.getFieldErrors())
                    .extracting(
                        BusinessException.BusinessFieldError::field
                    )
                    .containsExactly("query");
            });

        then(placeSearchClient).shouldHaveNoInteractions();
    }

    /**
     * 잘못된 limit의 오류 정보와 Client 미호출을 검증합니다.
     */
    private void assertInvalidLimit(
        int limit
    ) {
        assertThatThrownBy(
            () -> placeSearchService.searchPlaces(
                "강남역",
                limit
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

                assertThat(businessException.getFieldErrors())
                    .extracting(
                        BusinessException.BusinessFieldError::field
                    )
                    .containsExactly("limit");
            });

        then(placeSearchClient).shouldHaveNoInteractions();
    }

    /**
     * 장소 검색 Service 테스트용 후보 장소를 생성합니다.
     */
    private PlaceSearchCandidate createCandidate() {
        return new PlaceSearchCandidate(
            "123456",
            "강남역 2호선",
            "서울 강남구 역삼동",
            "서울 강남구 강남대로 396",
            37.4979,
            127.0276,
            "교통,수송 > 지하철"
        );
    }
}
