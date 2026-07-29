package com.roompick.domain.admin.room.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.roompick.domain.admin.room.dto.response.RoomStatusUpdateResponseDto;
import com.roompick.domain.admin.room.facade.AdminRoomFacade;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.global.common.BusinessException;
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
    void 관리자는_객실을_등록할_수_있다()
        throws Exception {

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
                RoomStatus.INACTIVE
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
                    .value("INACTIVE")
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("객실 가격이 0원이어도 등록할 수 있다")
    void 객실_가격이_0원이어도_등록할_수_있다()
        throws Exception {

        // given
        RoomCreateResponseDto response =
            new RoomCreateResponseDto(
                10L,
                1L,
                "101",
                "이벤트 객실",
                "무료 이벤트 객실",
                0L,
                1,
                2,
                RoomStatus.INACTIVE
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
              "name": "이벤트 객실",
              "description": "무료 이벤트 객실",
              "pricePerNight": 0,
              "standardCapacity": 1,
              "maxCapacity": 2
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
                jsonPath("$.data.pricePerNight")
                    .value(0)
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
              "description": "객실 설명",
              "pricePerNight": 150000,
              "standardCapacity": 2,
              "maxCapacity": 4
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
                jsonPath("$.code")
                    .value(
                        ErrorCode.FORBIDDEN.getCode()
                    )
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
              "description": "객실 설명",
              "pricePerNight": 150000,
              "standardCapacity": 2,
              "maxCapacity": 4
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
                jsonPath("$.code")
                    .value(
                        ErrorCode.UNAUTHORIZED.getCode()
                    )
            );

        verifyNoInteractions(adminRoomFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("객실 가격이 음수이면 등록에 실패한다")
    void 객실_가격이_음수이면_등록에_실패한다()
        throws Exception {

        String requestBody = """
            {
              "roomNumber": "101",
              "name": "디럭스 더블룸",
              "description": "객실 설명",
              "pricePerNight": -1,
              "standardCapacity": 2,
              "maxCapacity": 4
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

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("동일한 객실 번호가 존재하면 409를 반환한다")
    void 동일한_객실_번호가_존재하면_409를_반환한다()
        throws Exception {

        // given
        given(
            adminRoomFacade.createRoom(
                eq(1L),
                any()
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.ROOM_NUMBER_DUPLICATED
            )
        );

        String requestBody = """
            {
              "roomNumber": "101",
              "name": "디럭스 더블룸",
              "description": "객실 설명",
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
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.ROOM_NUMBER_DUPLICATED
                            .getCode()
                    )
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("운영 중지된 숙소에 객실을 등록하면 409를 반환한다")
    void 운영_중지된_숙소에_객실을_등록하면_409를_반환한다()
        throws Exception {

        // given
        given(
            adminRoomFacade.createRoom(
                eq(1L),
                any()
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.ACCOMMODATION_INACTIVE
            )
        );

        String requestBody = """
            {
              "roomNumber": "101",
              "name": "디럭스 더블룸",
              "description": "객실 설명",
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
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.ACCOMMODATION_INACTIVE
                            .getCode()
                    )
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 객실을 공개할 수 있다")
    void 관리자는_객실을_공개할_수_있다()
        throws Exception {
        // given
        given(
            adminRoomFacade.updateRoomStatus(
                eq(1L),
                eq(10L),
                any()
            )
        ).willReturn(
            new RoomStatusUpdateResponseDto(
                10L,
                RoomStatus.ACTIVE
            )
        );

        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        // when & then
        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}/status",
                    1L,
                    10L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roomId").value(10L))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 회원은 객실 상태를 변경할 수 없다")
    void 일반_회원은_객실_상태를_변경할_수_없다()
        throws Exception {
        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}/status",
                    1L,
                    10L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.FORBIDDEN.getCode())
            );

        verifyNoInteractions(adminRoomFacade);
    }

    @Test
    @DisplayName("미인증 사용자는 객실 상태를 변경할 수 없다")
    void 미인증_사용자는_객실_상태를_변경할_수_없다()
        throws Exception {
        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}/status",
                    1L,
                    10L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.UNAUTHORIZED.getCode())
            );

        verifyNoInteractions(adminRoomFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("다른 숙소 객실의 상태 변경 요청은 404를 반환한다")
    void 다른_숙소_객실의_상태_변경은_404를_반환한다()
        throws Exception {
        // given
        given(
            adminRoomFacade.updateRoomStatus(
                eq(1L),
                eq(10L),
                any()
            )
        ).willThrow(
            new BusinessException(ErrorCode.ROOM_NOT_FOUND)
        );

        String requestBody = """
            {
              "status": "INACTIVE"
            }
            """;

        // when & then
        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/rooms/{roomId}/status",
                    1L,
                    10L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }
}
