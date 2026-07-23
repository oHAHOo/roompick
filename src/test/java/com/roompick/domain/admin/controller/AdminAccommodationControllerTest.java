package com.roompick.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.admin.dto.response.AccommodationCreateResponseDto;
import com.roompick.domain.admin.facade.AdminAccommodationFacade;

@WebMvcTest(com.roompick.domain.admin.accommodation.controller.AdminAccommodationController.class)
@Import(AdminAccommodationControllerTest.MethodSecurityTestConfig.class)
class AdminAccommodationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccommodationFacade adminAccommodationFacade;

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

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
                LocalTime.of(15, 0),
                LocalTime.of(11, 0),
                AccommodationStatus.ACTIVE
            );

        given(
            adminAccommodationFacade.createAccommodation(any())
        ).willReturn(result);

        String requestBody = """
            {
              "name": "룸픽 호텔",
              "address": "서울특별시 중구",
              "description": "RoomPick MVP 예약 테스트를 위한 숙소",
              "checkInTime": "15:00",
              "checkOutTime": "11:00"
            }
            """;

        // when & then
        mockMvc.perform(
                post("/api/v1/admin/accommodations")
                    .with(csrf())
                    .contentType("application/json")
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.message")
                    .value("숙소가 등록되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.accommodationId")
                    .value(1L)
            )
            .andExpect(
                jsonPath("$.data.checkInTime")
                    .value("15:00")
            )
            .andExpect(
                jsonPath("$.data.checkOutTime")
                    .value("11:00")
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("ACTIVE")
            );
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 회원은 숙소를 등록할 수 없다")
    void 일반_회원은_숙소를_등록할_수_없다() throws Exception {
        String requestBody = """
        {
          "name": "룸픽 호텔",
          "address": "서울특별시 중구",
          "description": "RoomPick MVP 예약 테스트를 위한 숙소",
          "checkInTime": "15:00",
          "checkOutTime": "11:00"
        }
        """;

        mockMvc.perform(
                post("/api/v1/admin/accommodations")
                    .with(csrf())
                    .contentType("application/json")
                    .content(requestBody)
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COMMON_003"))
            .andExpect(
                jsonPath("$.message")
                    .value("접근 권한이 없습니다.")
            );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("체크인 시간이 없으면 400을 반환한다")
    void 체크인_시간이_없으면_400을_반환한다() throws Exception {
        String requestBody = """
            {
              "name": "룸픽 호텔",
              "address": "서울특별시 중구",
              "description": "RoomPick MVP 예약 테스트를 위한 숙소",
              "checkOutTime": "11:00"
            }
            """;

        mockMvc.perform(
                post("/api/v1/admin/accommodations")
                    .with(csrf())
                    .contentType("application/json")
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest());
    }
}
