package com.roompick.domain.accommodation.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.facade.AccommodationFacade;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.common.GlobalExceptionHandler;

/**
 * 인기 숙소 조회 Controller의 요청값과 응답 형식을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationPopularControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccommodationFacade accommodationFacade;

    @InjectMocks
    private AccommodationController accommodationController;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(
            accommodationController
        )
            .setControllerAdvice(
                new GlobalExceptionHandler()
            )
            .build();
    }

    @Test
    @DisplayName("limit을 생략하면 기본값 10으로 인기 숙소를 조회한다")
    void limit을_생략하면_기본값_10으로_인기_숙소를_조회한다()
        throws Exception {

        // given
        List<PopularAccommodationResponseDto> result =
            List.of(
                new PopularAccommodationResponseDto(
                    1,
                    3L,
                    "룸픽 부산 호텔",
                    "부산광역시 해운대구",
                    null
                )
            );

        given(
            accommodationFacade.getPopularAccommodations(
                10
            )
        ).willReturn(
            result
        );

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/popular"
                )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "인기 숙소 목록 조회에 성공했습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.data[0].rank")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.data[0].accommodationId")
                    .value(3L)
            )
            .andExpect(
                jsonPath("$.data[0].name")
                    .value("룸픽 부산 호텔")
            )
            .andExpect(
                jsonPath("$.data[0].address")
                    .value("부산광역시 해운대구")
            )
            .andExpect(
                jsonPath("$.data[0].imageUrl")
                    .value(
                        org.hamcrest.Matchers.nullValue()
                    )
            );

        /*
         * 쿼리 파라미터가 없으면 Controller에 설정한
         * defaultValue 10이 Facade로 전달돼야 합니다.
         */
        then(accommodationFacade)
            .should()
            .getPopularAccommodations(
                10
            );
    }

    @Test
    @DisplayName("허용 범위를 벗어난 limit은 공통 오류 응답을 반환한다")
    void 허용_범위를_벗어난_limit은_공통_오류_응답을_반환한다()
        throws Exception {

        // given
        int invalidLimit = 0;

        given(
            accommodationFacade.getPopularAccommodations(
                invalidLimit
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            )
        );

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/popular"
                )
                    .param(
                        "limit",
                        String.valueOf(invalidLimit)
                    )
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON_001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "요청 값이 올바르지 않습니다."
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(0)
            );

        then(accommodationFacade)
            .should()
            .getPopularAccommodations(
                invalidLimit
            );
    }

    @Test
    @DisplayName("지정한 limit으로 인기 숙소를 조회한다")
    void 지정한_limit으로_인기_숙소를_조회한다()
        throws Exception {

        // given
        int limit = 5;

        given(
            accommodationFacade.getPopularAccommodations(
                limit
            )
        ).willReturn(
            List.of()
        );

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/popular"
                )
                    .param(
                        "limit",
                        String.valueOf(limit)
                    )
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.data.length()")
                    .value(0)
            );

        then(accommodationFacade)
            .should()
            .getPopularAccommodations(
                limit
            );
    }
}
