package com.roompick.domain.reservation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto.AccommodationSummaryDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto.RoomSummaryDto;
import com.roompick.domain.reservation.dto.ReservationDetailResponseDto;
import com.roompick.domain.reservation.dto.ReservationListResponseDto;
import com.roompick.domain.reservation.dto.ReservationPageResponseDto;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.facade.ReservationFacade;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.security.JwtTokenProvider;

/**
 * 실제 Security 필터와 MVC 파이프라인을 사용하여
 * 예약 생성 API의 인증, 요청 검증, 응답 구조를 확인합니다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReservationFacade reservationFacade;

    @Test
    @DisplayName("인증된 회원은 예약을 생성할 수 있다")
    void 인증된_회원은_예약을_생성할_수_있다()
        throws Exception {

        // given
        Long memberId = 1L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        ReservationCreateResponseDto response =
            new ReservationCreateResponseDto(
                30L,
                memberId,
                new AccommodationSummaryDto(
                    10L,
                    "룸픽 호텔"
                ),
                new RoomSummaryDto(
                    20L,
                    "디럭스 더블룸",
                    "101"
                ),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2,
                2,
                100_000L,
                200_000L,
                ReservationStatus.PENDING_PAYMENT,
                LocalDateTime.of(
                    2026,
                    8,
                    1,
                    12,
                    10
                )
            );

        given(
            reservationFacade.createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            )
        ).willReturn(response);

        String requestBody = """
            {
              "roomId": 20,
              "checkInDate": "2026-08-10",
              "checkOutDate": "2026-08-12",
              "guestCount": 2
            }
            """;

        // when & then
        mockMvc.perform(
                post("/api/v1/reservations")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
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
                    .value(
                        "예약이 생성되었습니다. 제한 시간 내에 결제를 완료해 주세요."
                    )
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(30L)
            )
            .andExpect(
                jsonPath("$.data.memberId")
                    .value(memberId)
            )
            .andExpect(
                jsonPath("$.data.accommodation.accommodationId")
                    .value(10L)
            )
            .andExpect(
                jsonPath("$.data.accommodation.name")
                    .value("룸픽 호텔")
            )
            .andExpect(
                jsonPath("$.data.room.roomId")
                    .value(20L)
            )
            .andExpect(
                jsonPath("$.data.room.name")
                    .value("디럭스 더블룸")
            )
            .andExpect(
                jsonPath("$.data.room.roomNumber")
                    .value("101")
            )
            .andExpect(
                jsonPath("$.data.checkInDate")
                    .value("2026-08-10")
            )
            .andExpect(
                jsonPath("$.data.checkOutDate")
                    .value("2026-08-12")
            )
            .andExpect(
                jsonPath("$.data.guestCount")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.data.nightCount")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.data.pricePerNight")
                    .value(100_000L)
            )
            .andExpect(
                jsonPath("$.data.totalAmount")
                    .value(200_000L)
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("PENDING_PAYMENT")
            );
    }

    @Test
    @DisplayName("인증된 회원은 자신의 예약 목록을 페이지 단위로 조회할 수 있다")
    void 인증된_회원은_예약_목록을_조회할_수_있다()
        throws Exception {

        // given
        Long memberId = 1L;

        int page = 0;
        int size = 10;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        ReservationListResponseDto reservationResponse =
            new ReservationListResponseDto(
                30L,
                "룸픽 호텔",
                "디럭스 더블룸",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2,
                200_000L,
                ReservationStatus.PENDING_PAYMENT,
                LocalDateTime.of(
                    2026,
                    8,
                    1,
                    12,
                    0
                )
            );

        ReservationPageResponseDto response =
            new ReservationPageResponseDto(
                List.of(reservationResponse),
                page,
                size,
                1L,
                1,
                true
            );

        given(
            reservationFacade.getMyReservations(
                memberId,
                page,
                size
            )
        ).willReturn(response);

        // when & then
        mockMvc.perform(
                get("/api/v1/reservations")
                    .param("page", String.valueOf(page))
                    .param("size", String.valueOf(size))
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "내 예약 목록 조회에 성공했습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.content.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].reservationId"
                ).value(30L)
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].accommodationName"
                ).value("룸픽 호텔")
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].roomName"
                ).value("디럭스 더블룸")
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].checkInDate"
                ).value("2026-08-10")
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].checkOutDate"
                ).value("2026-08-12")
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].guestCount"
                ).value(2)
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].totalAmount"
                ).value(200_000L)
            )
            .andExpect(
                jsonPath(
                    "$.data.content[0].status"
                ).value("PENDING_PAYMENT")
            )
            .andExpect(
                jsonPath("$.data.pageNumber")
                    .value(0)
            )
            .andExpect(
                jsonPath("$.data.pageSize")
                    .value(10)
            )
            .andExpect(
                jsonPath("$.data.totalElements")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.data.totalPages")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.data.last")
                    .value(true)
            );

        verify(reservationFacade)
            .getMyReservations(
                memberId,
                page,
                size
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 예약 목록을 조회할 수 없다")
    void 인증되지_않은_회원은_예약_목록을_조회할_수_없다()
        throws Exception {

        // when & then
        mockMvc.perform(
                get("/api/v1/reservations")
                    .param("page", "0")
                    .param("size", "10")
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

        /*
         * 인증 필터에서 요청이 차단되므로
         * 예약 조회 로직까지 실행되면 안 됩니다.
         */
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("인증된 회원은 자신의 예약 상세 정보를 조회할 수 있다")
    void 인증된_회원은_예약_상세를_조회할_수_있다()
        throws Exception {

        // given
        Long memberId = 1L;
        Long reservationId = 30L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        ReservationDetailResponseDto response =
            new ReservationDetailResponseDto(
                reservationId,
                new ReservationDetailResponseDto
                    .AccommodationSummaryDto(
                    10L,
                    "룸픽 호텔",
                    "서울특별시 강남구"
                ),
                new ReservationDetailResponseDto
                    .RoomSummaryDto(
                    20L,
                    "디럭스 더블룸",
                    "101"
                ),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2,
                2,
                100_000L,
                200_000L,
                ReservationStatus.PENDING_PAYMENT,
                LocalDateTime.of(
                    2026,
                    8,
                    1,
                    12,
                    10
                ),
                null,
                LocalDateTime.of(
                    2026,
                    8,
                    1,
                    12,
                    0
                )
            );

        given(
            reservationFacade.getMyReservation(
                memberId,
                reservationId
            )
        ).willReturn(response);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/reservations/{reservationId}",
                    reservationId
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "예약 상세 조회에 성공했습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(reservationId)
            )
            .andExpect(
                jsonPath(
                    "$.data.accommodation.accommodationId"
                ).value(10L)
            )
            .andExpect(
                jsonPath("$.data.accommodation.name")
                    .value("룸픽 호텔")
            )
            .andExpect(
                jsonPath("$.data.accommodation.address")
                    .value("서울특별시 강남구")
            )
            .andExpect(
                jsonPath("$.data.room.roomId")
                    .value(20L)
            )
            .andExpect(
                jsonPath("$.data.room.name")
                    .value("디럭스 더블룸")
            )
            .andExpect(
                jsonPath("$.data.room.roomNumber")
                    .value("101")
            )
            .andExpect(
                jsonPath("$.data.checkInDate")
                    .value("2026-08-10")
            )
            .andExpect(
                jsonPath("$.data.checkOutDate")
                    .value("2026-08-12")
            )
            .andExpect(
                jsonPath("$.data.guestCount")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.data.nightCount")
                    .value(2)
            )
            .andExpect(
                jsonPath("$.data.pricePerNight")
                    .value(100_000L)
            )
            .andExpect(
                jsonPath("$.data.totalAmount")
                    .value(200_000L)
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("PENDING_PAYMENT")
            )
            .andExpect(
                jsonPath("$.data.canceledAt")
                    .doesNotExist()
            );

        verify(reservationFacade)
            .getMyReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 예약 상세 정보를 조회할 수 없다")
    void 인증되지_않은_회원은_예약_상세를_조회할_수_없다()
        throws Exception {

        // given
        Long reservationId = 30L;

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/reservations/{reservationId}",
                    reservationId
                )
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

        /*
         * 인증 필터에서 요청이 차단되므로
         * 예약 상세 조회 로직까지 실행되면 안 됩니다.
         */
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("다른 회원의 예약 상세 정보는 조회할 수 없다")
    void 다른_회원의_예약_상세는_조회할_수_없다()
        throws Exception {

        // given
        Long memberId = 1L;
        Long reservationId = 30L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        given(
            reservationFacade.getMyReservation(
                memberId,
                reservationId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_ACCESS_DENIED
            )
        );

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/reservations/{reservationId}",
                    reservationId
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_ACCESS_DENIED
                            .getCode()
                    )
            );

        verify(reservationFacade)
            .getMyReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("존재하지 않는 예약 상세 정보는 조회할 수 없다")
    void 존재하지_않는_예약_상세는_조회할_수_없다()
        throws Exception {

        // given
        Long memberId = 1L;
        Long reservationId = 999L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        given(
            reservationFacade.getMyReservation(
                memberId,
                reservationId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_NOT_FOUND
            )
        );

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/reservations/{reservationId}",
                    reservationId
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_NOT_FOUND
                            .getCode()
                    )
            );

        verify(reservationFacade)
            .getMyReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 예약을 생성할 수 없다")
    void 인증되지_않은_회원은_예약을_생성할_수_없다()
        throws Exception {

        // given
        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-12",
          "guestCount": 2
        }
        """;

        // when & then
        mockMvc.perform(
                post("/api/v1/reservations")
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

        // 인증 단계에서 차단되므로 Facade가 호출되면 안 됩니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("필수 요청값이 누락되면 예약 생성에 실패한다")
    void 필수_요청값이_누락되면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        // 필수값인 roomId를 누락한 요청입니다.
        String requestBody = """
        {
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-12",
          "guestCount": 2
        }
        """;

        // when & then
        mockMvc.perform(
                post("/api/v1/reservations")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
                    .contentType(MediaType.APPLICATION_JSON)
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
                        ErrorCode.INVALID_INPUT_VALUE.getCode()
                    )
            );

        // 요청 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }
}
