package com.roompick.domain.admin.accommodation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;

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

import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationCreateResponseDto;
import com.roompick.domain.admin.accommodation.dto.response.AccommodationStatusUpdateResponseDto;
import com.roompick.domain.admin.accommodation.facade.AdminAccommodationFacade;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminAccommodationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccommodationFacade adminAccommodationFacade;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 숙소를 등록할 수 있다")
    void 관리자는_숙소를_등록할_수_있다() throws Exception {
        // given
        AccommodationCreateResponseDto result =
            new AccommodationCreateResponseDto(
                1L,
                "룸픽 호텔",
                "서울특별시 중구",
                "RoomPick MVP 예약 테스트를 위한 숙소",
                LocalTime.of(15, 0, 0),
                LocalTime.of(11, 0, 0),
                AccommodationStatus.ACTIVE,
                List.of()
            );

        given(
            adminAccommodationFacade.createAccommodation(any(), any())
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("description", "RoomPick MVP 예약 테스트를 위한 숙소")
                    .param("latitude", "37.566500")
                    .param("longitude", "126.978000")
                    .param("checkInTime", "15:00:00")
                    .param("checkOutTime", "11:00:00")
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value("숙소가 등록되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.accommodationId")
                    .value(1L)
            )
            .andExpect(
                jsonPath("$.data.name")
                    .value("룸픽 호텔")
            )
            .andExpect(
                jsonPath("$.data.checkInTime")
                    .value("15:00:00")
            )
            .andExpect(
                jsonPath("$.data.checkOutTime")
                    .value("11:00:00")
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("ACTIVE")
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("위도와 경도가 누락되면 숙소 등록에 실패한다")
    void 위도와_경도가_누락되면_숙소_등록에_실패한다() throws Exception {
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("checkInTime", "15:00:00")
                    .param("checkOutTime", "11:00:00")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.INVALID_INPUT_VALUE.getCode())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("좌표 범위를 벗어나면 숙소 등록에 실패한다")
    void 좌표_범위를_벗어나면_숙소_등록에_실패한다() throws Exception {
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("latitude", "90.000001")
                    .param("longitude", "180.000001")
                    .param("checkInTime", "15:00:00")
                    .param("checkOutTime", "11:00:00")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.INVALID_INPUT_VALUE.getCode())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 회원은 숙소를 등록할 수 없다")
    void 일반_회원은_숙소를_등록할_수_없다() throws Exception {
        // when & then
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("description", "RoomPick MVP 예약 테스트를 위한 숙소")
                    .param("checkInTime", "15:00:00")
                    .param("checkOutTime", "11:00:00")
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.FORBIDDEN.getCode())
            )
            .andExpect(
                jsonPath("$.message")
                    .value(ErrorCode.FORBIDDEN.getMessage())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @DisplayName("인증되지 않은 회원은 숙소를 등록할 수 없다")
    void 인증되지_않은_회원은_숙소를_등록할_수_없다() throws Exception {
        // when & then
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("description", "RoomPick MVP 예약 테스트를 위한 숙소")
                    .param("checkInTime", "15:00:00")
                    .param("checkOutTime", "11:00:00")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.UNAUTHORIZED.getCode())
            )
            .andExpect(
                jsonPath("$.message")
                    .value(ErrorCode.UNAUTHORIZED.getMessage())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("체크인 시간이 누락되면 숙소 등록에 실패한다")
    void 체크인_시간이_누락되면_숙소_등록에_실패한다() throws Exception {
        // when & then
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("description", "RoomPick MVP 예약 테스트를 위한 숙소")
                    .param("checkOutTime", "11:00:00")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.INVALID_INPUT_VALUE.getCode())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("시간 형식이 올바르지 않으면 숙소 등록에 실패한다")
    void 시간_형식이_올바르지_않으면_숙소_등록에_실패한다() throws Exception {
        // when & then
        mockMvc.perform(
                multipart("/api/v1/admin/accommodations")
                    .with(csrf())
                    .param("name", "룸픽 호텔")
                    .param("address", "서울특별시 중구")
                    .param("description", "RoomPick MVP 예약 테스트를 위한 숙소")
                    .param("checkInTime", "15:00")
                    .param("checkOutTime", "11:00")
            )
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 숙소를 논리 삭제할 수 있다")
    void 관리자는_숙소를_논리_삭제할_수_있다()
        throws Exception {

        mockMvc.perform(
                delete(
                    "/api/v1/admin/accommodations/{accommodationId}",
                    1L
                )
                    .with(csrf())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("숙소가 삭제되었습니다.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        then(adminAccommodationFacade)
            .should()
            .deleteAccommodation(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 회원은 숙소를 삭제할 수 없다")
    void 일반_회원은_숙소를_삭제할_수_없다()
        throws Exception {

        mockMvc.perform(
                delete(
                    "/api/v1/admin/accommodations/{accommodationId}",
                    1L
                )
                    .with(csrf())
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.FORBIDDEN.getCode())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @DisplayName("인증되지 않은 회원은 숙소를 삭제할 수 없다")
    void 인증되지_않은_회원은_숙소를_삭제할_수_없다()
        throws Exception {

        mockMvc.perform(
                delete(
                    "/api/v1/admin/accommodations/{accommodationId}",
                    1L
                )
                    .with(csrf())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.UNAUTHORIZED.getCode())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 숙소 삭제 요청은 404를 반환한다")
    void 존재하지_않는_숙소_삭제_요청은_404를_반환한다()
        throws Exception {

        willThrow(
            new BusinessException(
                ErrorCode.ACCOMMODATION_NOT_FOUND
            )
        )
            .given(adminAccommodationFacade)
            .deleteAccommodation(999L);

        mockMvc.perform(
                delete(
                    "/api/v1/admin/accommodations/{accommodationId}",
                    999L
                )
                    .with(csrf())
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.ACCOMMODATION_NOT_FOUND
                            .getCode()
                    )
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 숙소를 다시 공개할 수 있다")
    void 관리자는_숙소를_다시_공개할_수_있다() throws Exception {
        // given
        given(
            adminAccommodationFacade.updateAccommodationStatus(
                eq(1L),
                any()
            )
        ).willReturn(
            new AccommodationStatusUpdateResponseDto(
                1L,
                AccommodationStatus.ACTIVE
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
                    "/api/v1/admin/accommodations/{accommodationId}/status",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accommodationId").value(1L))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자는 숙소를 비공개할 수 있다")
    void 관리자는_숙소를_비공개할_수_있다() throws Exception {
        // given
        given(
            adminAccommodationFacade.updateAccommodationStatus(
                eq(1L),
                any()
            )
        ).willReturn(
            new AccommodationStatusUpdateResponseDto(
                1L,
                AccommodationStatus.INACTIVE
            )
        );

        String requestBody = """
            {
              "status": "INACTIVE"
            }
            """;

        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/status",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accommodationId").value(1L))
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("같은 상태를 다시 요청해도 200으로 멱등하게 처리한다")
    void 같은_상태를_다시_요청해도_멱등하게_처리한다() throws Exception {
        // given
        given(
            adminAccommodationFacade.updateAccommodationStatus(
                eq(1L),
                any()
            )
        ).willReturn(
            new AccommodationStatusUpdateResponseDto(
                1L,
                AccommodationStatus.ACTIVE
            )
        );

        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        // when & then: 같은 요청을 두 번 보내도 모두 200이다.
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(
                    patch(
                        "/api/v1/admin/accommodations/{accommodationId}/status",
                        1L
                    )
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        then(adminAccommodationFacade)
            .should(org.mockito.Mockito.times(2))
            .updateAccommodationStatus(eq(1L), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("숙소 상태가 누락되면 400을 반환한다")
    void 숙소_상태가_누락되면_400을_반환한다() throws Exception {
        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/status",
                    1L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.INVALID_INPUT_VALUE.getCode())
            );

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 회원은 숙소 상태를 변경할 수 없다")
    void 일반_회원은_숙소_상태를_변경할_수_없다() throws Exception {
        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/status",
                    1L
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

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @DisplayName("인증되지 않은 회원은 숙소 상태를 변경할 수 없다")
    void 인증되지_않은_회원은_숙소_상태를_변경할_수_없다() throws Exception {
        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/status",
                    1L
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

        verifyNoInteractions(adminAccommodationFacade);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 숙소의 상태 변경 요청은 404를 반환한다")
    void 존재하지_않는_숙소의_상태_변경_요청은_404를_반환한다() throws Exception {
        // given
        given(
            adminAccommodationFacade.updateAccommodationStatus(
                eq(999L),
                any()
            )
        ).willThrow(
            new BusinessException(ErrorCode.ACCOMMODATION_NOT_FOUND)
        );

        String requestBody = """
            {
              "status": "ACTIVE"
            }
            """;

        mockMvc.perform(
                patch(
                    "/api/v1/admin/accommodations/{accommodationId}/status",
                    999L
                )
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code")
                    .value(ErrorCode.ACCOMMODATION_NOT_FOUND.getCode())
            );
    }
}
