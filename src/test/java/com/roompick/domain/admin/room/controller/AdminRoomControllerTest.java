package com.roompick.domain.admin.room.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.roompick.domain.admin.room.dto.response.RoomCreateResponseDto;
import com.roompick.domain.admin.room.facade.AdminRoomFacade;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.global.common.ErrorCode;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminRoomFacade adminRoomFacade;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 객실을 등록할 수 있다")
    void 관리자는_객실을_등록할_수_있다() throws Exception {
        // given
        RoomCreateResponseDto response =
            new RoomCreateResponseDto(
                10L,
                1L,
                "101",
                "디럭스 더블룸",
                "퀸사이즈 침대가 포함된 객실",
                150000L,
                2,
                4,
                RoomStatus.ACTIVE
            );

        given(
            adminRoomFacade.createRoom(
                eq(1L),
                any()
            )
        ).willReturn(response);

        String requestBody = """
        {
          "roomNumber": "101",
          "name": "디럭스 더블룸",
          "description": "퀸사이즈 침대가 포함된 객실",
          "pricePerNight": 150000,
          "standardCapacity": 2,
          "maxCapacity": 4
        }
        """;

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("객실이 등록되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.roomId")
                    .value(10L)
            )
            .andExpect(
                jsonPath("$.data.accommodationId")
                    .value(1L)
            )
            .andExpect(
                jsonPath("$.data.roomNumber")
                    .value("101")
            )
            .andExpect(
                jsonPath("$.data.name")
                    .value("디럭스 더블룸")
            )
            .andExpect(
                jsonPath("$.data.description")
                    .value("퀸사이즈 침대가 포함된 객실")
            )
            .andExpect(
                jsonPath("$.data.pricePerNight")
                    .value(150000L)
            )
            .andExpect(
                jsonPath("$.data.standardCapacity")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.data.maxCapacity")
                    .value(4)
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("ACTIVE")
            );
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 회원은 객실을 등록할 수 없다")
    void 일반_회원은_객실을_등록할_수_없다()
        throws Exception {

        String requestBody = """
            {
              "roomNumber": "101",
              "name": "디럭스 더블룸",
              "price": 150000,
              "capacity": 2
            }
            """;

        mockMvc.perform(
                post(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.FORBIDDEN.getCode())
            );

        verifyNoInteractions(adminRoomFacade);
    }

    @Test
    @DisplayName("인증되지 않은 회원은 객실을 등록할 수 없다")
    void 인증되지_않은_회원은_객실을_등록할_수_없다()
        throws Exception {

        String requestBody = """
            {
              "roomNumber": "101",
              "name": "디럭스 더블룸",
              "price": 150000,
              "capacity": 2
            }
            """;

        mockMvc.perform(
                post(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
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
                        ErrorCode.UNAUTHORIZED.getCode()
                    )
            );

        verifyNoInteractions(adminRoomFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("객실 가격이 0이면 등록에 실패한다")
    void 객실_가격이_0이면_등록에_실패한다()
        throws Exception {

        String requestBody = """
            {
              "roomNumber": "101",
              "name": "디럭스 더블룸",
              "price": 0,
              "capacity": 2
            }
            """;

        mockMvc.perform(
                post(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getCode()
                    )
            );

        verifyNoInteractions(adminRoomFacade);
    }
}
