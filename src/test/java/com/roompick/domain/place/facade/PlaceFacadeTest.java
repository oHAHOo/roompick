package com.roompick.domain.place.facade;

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

import com.roompick.domain.place.dto.PlaceSearchResponseDto;
import com.roompick.domain.place.service.PlaceSearchService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 장소 검색 Service를 연결하는 PlaceFacade의 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class PlaceFacadeTest {

    @Mock
    private PlaceSearchService placeSearchService;

    @InjectMocks
    private PlaceFacade placeFacade;

    @Test
    @DisplayName("장소 검색 요청을 Service에 위임하고 결과를 반환한다")
    void delegatePlaceSearchToService() {
        // given
        List<PlaceSearchResponseDto> expected =
            List.of(
                new PlaceSearchResponseDto(
                    "123456",
                    "강남역 2호선",
                    "서울 강남구 역삼동",
                    "서울 강남구 강남대로 396",
                    37.4979,
                    127.0276,
                    "교통,수송 > 지하철"
                )
            );

        given(
            placeSearchService.searchPlaces(
                "강남역",
                5
            )
        ).willReturn(expected);

        // when
        List<PlaceSearchResponseDto> actual =
            placeFacade.searchPlaces(
                "강남역",
                5
            );

        // then
        assertThat(actual).isSameAs(expected);

        then(placeSearchService)
            .should()
            .searchPlaces(
                "강남역",
                5
            );
    }

    @Test
    @DisplayName("Service의 장소 검색 예외를 그대로 전파한다")
    void propagatePlaceSearchException() {
        // given
        BusinessException exception =
            new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );

        given(
            placeSearchService.searchPlaces(
                " ",
                5
            )
        ).willThrow(exception);

        // when & then
        assertThatThrownBy(
            () -> placeFacade.searchPlaces(
                " ",
                5
            )
        ).isSameAs(exception);
    }
}
