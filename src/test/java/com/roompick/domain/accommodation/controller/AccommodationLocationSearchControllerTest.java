package com.roompick.domain.accommodation.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.facade.AccommodationFacade;

/**
 * 위치 기반 숙소 검색 API의 HTTP 요청과 응답 형식을 검증하는 테스트입니다.
 *
 * 실제 MySQL 거리 계산은 Repository 통합 테스트에서 별도로 검증하므로,
 * 여기서는 Facade를 Mock하여 Controller의 파라미터 전달과
 * 공통 응답 형식에 집중합니다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AccommodationLocationSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccommodationFacade accommodationFacade;

    @Test
    void 위치_기반_숙소_검색에_성공한다() throws Exception {
        // given: 서울시청을 기준으로 검색할 요청값을 준비합니다.
        String keyword = "룸픽";
        double latitude = 37.566500;
        double longitude = 126.978000;
        double radiusKm = 5.0;
        int limit = 20;

        AccommodationLocationSearchResponseDto searchResult =
            new AccommodationLocationSearchResponseDto(
                1L,
                "룸픽 서울 호텔",
                "서울특별시 중구",
                37.565800,
                126.978500,
                0.85
            );

        given(
            accommodationFacade.searchNearbyAccommodations(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            )
        ).willReturn(
            List.of(searchResult)
        );

        // when & then: 위치 검색 요청이 정상 응답을 반환합니다.
        mockMvc.perform(
                get("/api/v1/accommodations/search")
                    .param("keyword", keyword)
                    .param("latitude", String.valueOf(latitude))
                    .param("longitude", String.valueOf(longitude))
                    .param("radiusKm", String.valueOf(radiusKm))
                    .param("limit", String.valueOf(limit))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success")
                .value(true))
            .andExpect(jsonPath("$.message")
                .value("주변 숙소 검색에 성공했습니다."))
            .andExpect(jsonPath("$.data.length()")
                .value(1))
            .andExpect(jsonPath("$.data[0].accommodationId")
                .value(1L))
            .andExpect(jsonPath("$.data[0].name")
                .value("룸픽 서울 호텔"))
            .andExpect(jsonPath("$.data[0].address")
                .value("서울특별시 중구"))
            .andExpect(jsonPath("$.data[0].latitude")
                .value(37.565800))
            .andExpect(jsonPath("$.data[0].longitude")
                .value(126.978500))
            .andExpect(jsonPath("$.data[0].distanceKm")
                .value(0.85));

        then(accommodationFacade)
            .should()
            .searchNearbyAccommodations(
                keyword,
                latitude,
                longitude,
                radiusKm,
                limit
            );
    }

    @Test
    void 검색_반경과_limit을_생략하면_기본값을_사용한다()
        throws Exception {

        // given: keyword 없이 검색하고 Facade는 빈 결과를 반환합니다.
        double latitude = 37.566500;
        double longitude = 126.978000;
        double defaultRadiusKm = 5.0;
        int defaultLimit = 20;

        given(
            accommodationFacade.searchNearbyAccommodations(
                null,
                latitude,
                longitude,
                defaultRadiusKm,
                defaultLimit
            )
        ).willReturn(
            List.of()
        );

        // when & then:
        // radiusKm와 limit을 생략하면 Controller 기본값을 사용합니다.
        mockMvc.perform(
                get("/api/v1/accommodations/search")
                    .param("latitude", String.valueOf(latitude))
                    .param("longitude", String.valueOf(longitude))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success")
                .value(true))
            .andExpect(jsonPath("$.data.length()")
                .value(0));

        then(accommodationFacade)
            .should()
            .searchNearbyAccommodations(
                null,
                latitude,
                longitude,
                defaultRadiusKm,
                defaultLimit
            );
    }

    @Test
    void 위도가_없으면_400을_반환한다() throws Exception {
        // when & then:
        // 필수 파라미터인 latitude가 없으면 요청 바인딩 단계에서 실패합니다.
        mockMvc.perform(
                get("/api/v1/accommodations/search")
                    .param("longitude", "126.978000")
            )
            .andExpect(status().isBadRequest());

        /*
         * 요청 파라미터 바인딩 단계에서 차단되므로
         * Facade는 호출되지 않아야 합니다.
         */
        then(accommodationFacade)
            .shouldHaveNoInteractions();
    }

    @Test
    void 경도가_없으면_400을_반환한다() throws Exception {
        // when & then:
        // 필수 파라미터인 longitude가 없으면 요청 바인딩 단계에서 실패합니다.
        mockMvc.perform(
                get("/api/v1/accommodations/search")
                    .param("latitude", "37.566500")
            )
            .andExpect(status().isBadRequest());

        then(accommodationFacade)
            .shouldHaveNoInteractions();
    }
}
