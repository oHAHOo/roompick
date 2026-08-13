package com.roompick.domain.place.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.place.dto.PlaceSearchResponseDto;
import com.roompick.domain.place.facade.PlaceFacade;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 공개 장소 검색 API의 요청 파라미터 전달, 보안 정책과 응답 형식을 검증합니다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PlaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceFacade placeFacade;

    @Test
    @DisplayName("비로그인 사용자가 장소 검색에 성공한다")
    void searchPlacesWithoutAuthentication() throws Exception {
        // given
        PlaceSearchResponseDto searchResult =
            new PlaceSearchResponseDto(
                "123456",
                "강남역 2호선",
                "서울 강남구 역삼동",
                "서울 강남구 강남대로 396",
                37.4979,
                127.0276,
                "교통,수송 > 지하철"
            );

        given(
            placeFacade.searchPlaces(
                "강남역",
                5
            )
        ).willReturn(List.of(searchResult));

        // when & then
        mockMvc.perform(
                get("/api/v1/places/search")
                    .param("query", "강남역")
                    .param("limit", "5")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message")
                .value("장소 검색에 성공했습니다."))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].placeId").value("123456"))
            .andExpect(jsonPath("$.data[0].name").value("강남역 2호선"))
            .andExpect(jsonPath("$.data[0].address")
                .value("서울 강남구 역삼동"))
            .andExpect(jsonPath("$.data[0].roadAddress")
                .value("서울 강남구 강남대로 396"))
            .andExpect(jsonPath("$.data[0].latitude").value(37.4979))
            .andExpect(jsonPath("$.data[0].longitude").value(127.0276))
            .andExpect(jsonPath("$.data[0].category")
                .value("교통,수송 > 지하철"));

        then(placeFacade)
            .should()
            .searchPlaces(
                "강남역",
                5
            );
    }

    @Test
    @DisplayName("limit을 생략하면 기본값 5를 Facade에 전달한다")
    void useDefaultLimitWhenLimitIsOmitted() throws Exception {
        // given
        given(
            placeFacade.searchPlaces(
                "서울역",
                5
            )
        ).willReturn(List.of());

        // when & then
        mockMvc.perform(
                get("/api/v1/places/search")
                    .param("query", "서울역")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(0));

        then(placeFacade)
            .should()
            .searchPlaces(
                "서울역",
                5
            );
    }

    @Test
    @DisplayName("필수 query가 누락되면 400을 반환한다")
    void rejectWhenQueryIsMissing() throws Exception {
        // when & then
        mockMvc.perform(
                get("/api/v1/places/search")
                    .param("limit", "5")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON_001"));

        then(placeFacade).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("query가 100자를 초과하면 400을 반환한다")
    void rejectWhenQueryExceedsMaximumLength() throws Exception {
        String query = "가".repeat(101);

        given(
            placeFacade.searchPlaces(query, 5)
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            )
        );

        mockMvc.perform(
                get("/api/v1/places/search")
                    .param("query", query)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.INVALID_INPUT_VALUE.getCode())
            );

        then(placeFacade)
            .should()
            .searchPlaces(query, 5);
    }

    @Test
    @DisplayName("장소 검색 입력 오류는 공통 예외 응답으로 400을 반환한다")
    void returnBadRequestWhenPlaceSearchInputIsInvalid()
        throws Exception {

        // given
        given(
            placeFacade.searchPlaces(
                "강남역",
                16
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            )
        );

        // when & then
        mockMvc.perform(
                get("/api/v1/places/search")
                    .param("query", "강남역")
                    .param("limit", "16")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON_001"))
            .andExpect(jsonPath("$.message")
                .value("요청 값이 올바르지 않습니다."));

        then(placeFacade)
            .should()
            .searchPlaces(
                "강남역",
                16
            );
    }
}
