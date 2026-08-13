package com.roompick.domain.admin.timesale.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.admin.timesale.dto.request.TimeSaleCreateRequestDto;
import com.roompick.domain.admin.timesale.dto.response.TimeSaleCreateResponseDto;
import com.roompick.domain.admin.timesale.facade.AdminTimeSaleFacade;
import com.roompick.domain.timesale.entity.TimeSaleStatus;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 관리자 타임세일 등록 API의 성공 응답,
 * 입력값 검증 및 접근 권한을 검증합니다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminTimeSaleControllerTest {

    private static final String API_URL =
        "/api/v1/admin/accommodations/"
            + "{accommodationId}/time-sales";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminTimeSaleFacade adminTimeSaleFacade;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "관리자는 숙소 전체 타임세일을 등록할 수 있다"
    )
    void 관리자는_숙소_전체_타임세일을_등록할_수_있다()
        throws Exception {
        // given
        Long accommodationId = 1L;

        LocalDateTime startAt =
            LocalDateTime.of(
                2026,
                8,
                13,
                12,
                0
            );

        LocalDateTime endAt =
            LocalDateTime.of(
                2026,
                8,
                13,
                18,
                0
            );

        TimeSaleCreateResponseDto response =
            new TimeSaleCreateResponseDto(
                100L,
                accommodationId,
                null,
                20,
                startAt,
                endAt,
                TimeSaleStatus.SCHEDULED
            );

        given(
            adminTimeSaleFacade.create(
                eq(accommodationId),
                any(TimeSaleCreateRequestDto.class)
            )
        ).willReturn(response);

        String requestBody = """
            {
              "roomId": null,
              "discountRate": 20,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    accommodationId
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "타임세일이 등록되었습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.timeSaleId")
                    .value(100L)
            )
            .andExpect(
                jsonPath(
                    "$.data.accommodationId"
                ).value(accommodationId)
            )
            .andExpect(
                jsonPath("$.data.roomId")
                    .value(nullValue())
            )
            .andExpect(
                jsonPath("$.data.discountRate")
                    .value(20)
            )
            .andExpect(
                jsonPath("$.data.startAt")
                    .value(
                        "2026-08-13T12:00:00"
                    )
            )
            .andExpect(
                jsonPath("$.data.endAt")
                    .value(
                        "2026-08-13T18:00:00"
                    )
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("SCHEDULED")
            );

        then(adminTimeSaleFacade)
            .should()
            .create(
                eq(accommodationId),
                any(TimeSaleCreateRequestDto.class)
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "관리자는 특정 객실 타임세일을 등록할 수 있다"
    )
    void 관리자는_특정_객실_타임세일을_등록할_수_있다()
        throws Exception {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;

        LocalDateTime startAt =
            LocalDateTime.of(
                2026,
                8,
                13,
                12,
                0
            );

        LocalDateTime endAt =
            LocalDateTime.of(
                2026,
                8,
                13,
                18,
                0
            );

        TimeSaleCreateResponseDto response =
            new TimeSaleCreateResponseDto(
                101L,
                accommodationId,
                roomId,
                30,
                startAt,
                endAt,
                TimeSaleStatus.SCHEDULED
            );

        given(
            adminTimeSaleFacade.create(
                eq(accommodationId),
                any(TimeSaleCreateRequestDto.class)
            )
        ).willReturn(response);

        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 30,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    accommodationId
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "타임세일이 등록되었습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.timeSaleId")
                    .value(101L)
            )
            .andExpect(
                jsonPath(
                    "$.data.accommodationId"
                ).value(accommodationId)
            )
            .andExpect(
                jsonPath("$.data.roomId")
                    .value(roomId)
            )
            .andExpect(
                jsonPath("$.data.discountRate")
                    .value(30)
            )
            .andExpect(
                jsonPath("$.data.startAt")
                    .value(
                        "2026-08-13T12:00:00"
                    )
            )
            .andExpect(
                jsonPath("$.data.endAt")
                    .value(
                        "2026-08-13T18:00:00"
                    )
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("SCHEDULED")
            );

        then(adminTimeSaleFacade)
            .should()
            .create(
                eq(accommodationId),
                any(TimeSaleCreateRequestDto.class)
            );
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName(
        "일반 회원은 타임세일을 등록할 수 없다"
    )
    void 일반_회원은_타임세일을_등록할_수_없다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 20,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.FORBIDDEN
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.FORBIDDEN
                            .getMessage()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @DisplayName(
        "인증되지 않은 회원은 타임세일을 등록할 수 없다"
    )
    void 인증되지_않은_회원은_타임세일을_등록할_수_없다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 20,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.UNAUTHORIZED
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.UNAUTHORIZED
                            .getMessage()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "할인율이 누락되면 타임세일 등록에 실패한다"
    )
    void 할인율이_누락되면_타임세일_등록에_실패한다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode
                            .INVALID_INPUT_VALUE
                            .getCode()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "할인율이 1보다 작으면 타임세일 등록에 실패한다"
    )
    void 할인율이_최솟값보다_작으면_등록에_실패한다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 0,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode
                            .INVALID_INPUT_VALUE
                            .getCode()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "할인율이 99보다 크면 타임세일 등록에 실패한다"
    )
    void 할인율이_최댓값보다_크면_등록에_실패한다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 100,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode
                            .INVALID_INPUT_VALUE
                            .getCode()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "타임세일 시작 시각이 누락되면 등록에 실패한다"
    )
    void 시작_시각이_누락되면_등록에_실패한다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 20,
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode
                            .INVALID_INPUT_VALUE
                            .getCode()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "타임세일 종료 시각이 누락되면 등록에 실패한다"
    )
    void 종료_시각이_누락되면_등록에_실패한다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 20,
              "startAt": "2026-08-13T12:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode
                            .INVALID_INPUT_VALUE
                            .getCode()
                    )
            );

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "시작 시각 형식이 올바르지 않으면 등록에 실패한다"
    )
    void 시작_시각_형식이_올바르지_않으면_실패한다()
        throws Exception {
        // given
        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 20,
              "startAt": "2026/08/13 12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    1L
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminTimeSaleFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName(
        "타임세일 기간이 겹치면 409 충돌 응답을 반환한다"
    )
    void 타임세일_기간이_겹치면_충돌_응답을_반환한다()
        throws Exception {
        // given
        Long accommodationId = 1L;

        given(
            adminTimeSaleFacade.create(
                eq(accommodationId),
                any(TimeSaleCreateRequestDto.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP
            )
        );

        String requestBody = """
            {
              "roomId": 10,
              "discountRate": 20,
              "startAt": "2026-08-13T12:00:00",
              "endAt": "2026-08-13T18:00:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post(
                    API_URL,
                    accommodationId
                )
                    .with(csrf())
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(requestBody)
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode
                            .TIME_SALE_PERIOD_OVERLAP
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode
                            .TIME_SALE_PERIOD_OVERLAP
                            .getMessage()
                    )
            );
    }
}
