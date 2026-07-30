package com.roompick.domain.reservation.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
import com.roompick.domain.reservation.dto.ReservationCancelResponseDto;
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("roomId")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("객실 ID는 필수입니다.")
            );

        // 요청 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("인증된 회원은 자신의 결제 대기 예약을 취소할 수 있다")
    void 인증된_회원은_예약을_취소할_수_있다()
        throws Exception {

        // given
        Long memberId = 1L;
        Long reservationId = 30L;

        LocalDateTime canceledAt =
            LocalDateTime.of(
                2026,
                8,
                2,
                10,
                0
            );

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        ReservationCancelResponseDto response =
            new ReservationCancelResponseDto(
                reservationId,
                ReservationStatus.CANCELED,
                canceledAt
            );

        given(
            reservationFacade.cancelReservation(
                memberId,
                reservationId
            )
        ).willReturn(response);

        // when & then
        mockMvc.perform(
                patch(
                    "/api/v1/reservations/{reservationId}/cancel",
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
                    .value("예약이 취소되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(reservationId)
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("CANCELED")
            )
            .andExpect(
                jsonPath("$.data.canceledAt")
                    .value("2026-08-02T10:00:00")
            );

        verify(reservationFacade)
            .cancelReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("인증되지 않은 회원은 예약을 취소할 수 없다")
    void 인증되지_않은_회원은_예약을_취소할_수_없다()
        throws Exception {

        // when & then
        mockMvc.perform(
                patch(
                    "/api/v1/reservations/{reservationId}/cancel",
                    30L
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

        // 인증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("다른 회원의 예약은 취소할 수 없다")
    void 다른_회원의_예약은_취소할_수_없다()
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
            reservationFacade.cancelReservation(
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
                patch(
                    "/api/v1/reservations/{reservationId}/cancel",
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
            .cancelReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("취소할 수 없는 상태의 예약은 취소 요청이 거절된다")
    void 취소할_수_없는_예약_상태이면_요청이_거절된다()
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
            reservationFacade.cancelReservation(
                memberId,
                reservationId
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.RESERVATION_NOT_CANCELABLE
            )
        );

        // when & then
        mockMvc.perform(
                patch(
                    "/api/v1/reservations/{reservationId}/cancel",
                    reservationId
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.RESERVATION_NOT_CANCELABLE
                            .getCode()
                    )
            );

        verify(reservationFacade)
            .cancelReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("예약 인원이 누락되면 예약 생성에 실패한다")
    void 예약_인원이_누락되면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        // 필수값인 guestCount를 누락한 요청입니다.
        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-12"
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("guestCount")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("예약 인원은 필수입니다.")
            );

        // 요청 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("예약 인원이 0명이면 예약 생성에 실패한다")
    void 예약_인원이_0명이면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-12",
          "guestCount": 0
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("guestCount")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("예약 인원은 1명 이상이어야 합니다.")
            );

        // DTO 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("예약 인원이 음수이면 예약 생성에 실패한다")
    void 예약_인원이_음수이면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-12",
          "guestCount": -1
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("guestCount")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("예약 인원은 1명 이상이어야 합니다.")
            );

        // DTO 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("여러 필수 요청값이 동시에 누락되면 모든 오류를 반환한다")
    void 여러_필수_요청값이_동시에_누락되면_모든_오류를_반환한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        // 예약 생성의 모든 필수 요청값을 누락했습니다.
        String requestBody = """
        {
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(4)
            )
            .andExpect(
                jsonPath(
                    "$.errors[*].field",
                    containsInAnyOrder(
                        "roomId",
                        "checkInDate",
                        "checkOutDate",
                        "guestCount"
                    )
                )
            )
            .andExpect(
                jsonPath(
                    "$.errors[*].message",
                    containsInAnyOrder(
                        "객실 ID는 필수입니다.",
                        "체크인 날짜는 필수입니다.",
                        "체크아웃 날짜는 필수입니다.",
                        "예약 인원은 필수입니다."
                    )
                )
            );

        // DTO 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("체크인 날짜가 과거이면 예약 생성에 실패한다")
    void 체크인_날짜가_과거이면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2020-01-01",
          "checkOutDate": "2020-01-02",
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("checkInDate")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value(
                        "체크인 날짜는 오늘 이전일 수 없습니다."
                    )
            );

        // DTO 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("체크인과 체크아웃 날짜가 같으면 예약 생성에 실패한다")
    void 체크인과_체크아웃_날짜가_같으면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-10",
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("checkOutDate")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value(
                        "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다."
                    )
            );

        // DTO 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("체크아웃 날짜가 체크인 날짜보다 빠르면 예약 생성에 실패한다")
    void 체크아웃_날짜가_체크인_날짜보다_빠르면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-12",
          "checkOutDate": "2026-08-10",
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("checkOutDate")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value(
                        "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다."
                    )
            );

        // DTO 검증 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("체크인 날짜 형식이 올바르지 않으면 예약 생성에 실패한다")
    void 체크인_날짜_형식이_올바르지_않으면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        /*
         * LocalDate가 요구하는 yyyy-MM-dd 형식이 아닌
         * 슬래시 형식으로 체크인 날짜를 전달합니다.
         */
        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026/08/10",
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("checkInDate")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("날짜 형식은 yyyy-MM-dd여야 합니다.")
            );

        /*
         * 요청 Body를 DTO로 변환하는 단계에서 실패하므로
         * 예약 Facade는 호출되지 않아야 합니다.
         */
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("객실 ID를 숫자가 아닌 값으로 입력하면 예약 생성에 실패한다")
    void 객실_ID를_숫자가_아닌_값으로_입력하면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": "room-20",
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("roomId")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("숫자 형식으로 입력해야 합니다.")
            );

        /*
         * DTO 변환 단계에서 요청이 차단되므로
         * 예약 Facade는 호출되지 않아야 합니다.
         */
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("예약 인원을 숫자가 아닌 값으로 입력하면 예약 생성에 실패한다")
    void 예약_인원을_숫자가_아닌_값으로_입력하면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2026-08-10",
          "checkOutDate": "2026-08-12",
          "guestCount": "two"
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("guestCount")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value("숫자 형식으로 입력해야 합니다.")
            );

        // DTO 변환 단계에서 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("JSON 문법이 올바르지 않으면 예약 생성에 실패한다")
    void JSON_문법이_올바르지_않으면_예약_생성에_실패한다()
        throws Exception {

        // given
        String accessToken =
            jwtTokenProvider.createAccessToken(
                1L,
                MemberRole.USER
            );

        // roomId 뒤의 쉼표를 누락한 잘못된 JSON 요청입니다.
        String requestBody = """
        {
          "roomId": 20
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
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.INVALID_INPUT_VALUE.getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("requestBody")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value(
                        "요청 Body의 JSON 형식이 올바르지 않습니다."
                    )
            );

        // DTO 생성 전에 요청이 차단되므로 Facade는 호출되지 않습니다.
        verifyNoInteractions(reservationFacade);
    }

    @Test
    @DisplayName("객실 최대 인원을 초과하면 구체적인 인원 오류를 반환한다")
    void 객실_최대_인원을_초과하면_구체적인_인원_오류를_반환한다()
        throws Exception {

        // given
        Long memberId = 1L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        /*
         * Facade 내부의 RoomService에서 최대 인원 초과가 발생한 상황을
         * Controller 테스트에서는 BusinessException으로 재현합니다.
         */
        given(
            reservationFacade.createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.ROOM_CAPACITY_EXCEEDED,
                List.of(
                    new BusinessException.BusinessFieldError(
                        "guestCount",
                        "선택한 객실은 최대 2명까지 예약할 수 있습니다."
                    )
                )
            )
        );

        /*
         * 날짜 검증이 테스트 실행 시점에 영향을 받지 않도록
         * 충분히 미래의 날짜를 사용합니다.
         */
        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2099-08-10",
          "checkOutDate": "2099-08-12",
          "guestCount": 3
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
                        ErrorCode.ROOM_CAPACITY_EXCEEDED
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.ROOM_CAPACITY_EXCEEDED
                            .getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(1)
            )
            .andExpect(
                jsonPath("$.errors[0].field")
                    .value("guestCount")
            )
            .andExpect(
                jsonPath("$.errors[0].message")
                    .value(
                        "선택한 객실은 최대 2명까지 예약할 수 있습니다."
                    )
            );

        verify(reservationFacade)
            .createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            );
    }

    @Test
    @DisplayName("존재하지 않는 객실을 예약하면 상세 오류는 빈 배열로 반환한다")
    void 존재하지_않는_객실을_예약하면_상세_오류는_빈_배열로_반환한다()
        throws Exception {

        // given
        Long memberId = 1L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        /*
         * 존재하지 않는 객실은 특정 필드에 추가로 제공할 정보가 없으므로
         * 기존 에러 메시지만 반환하고 errors 배열은 비워둡니다.
         */
        given(
            reservationFacade.createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.ROOM_NOT_FOUND
            )
        );

        String requestBody = """
        {
          "roomId": 999,
          "checkInDate": "2099-08-10",
          "checkOutDate": "2099-08-12",
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
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.ROOM_NOT_FOUND
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.ROOM_NOT_FOUND
                            .getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(0)
            );

        verify(reservationFacade)
            .createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            );
    }

    @Test
    @DisplayName("운영 중지된 객실을 예약하면 상세 오류는 빈 배열로 반환한다")
    void 운영_중지된_객실을_예약하면_상세_오류는_빈_배열로_반환한다()
        throws Exception {

        // given
        Long memberId = 1L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        /*
         * 비활성 객실은 상단 에러 메시지만으로 원인이 충분하므로
         * 같은 내용을 errors 배열에 반복하지 않습니다.
         */
        given(
            reservationFacade.createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.ROOM_INACTIVE
            )
        );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2099-08-10",
          "checkOutDate": "2099-08-12",
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
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.ROOM_INACTIVE
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.ROOM_INACTIVE
                            .getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(0)
            );

        verify(reservationFacade)
            .createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            );
    }

    @Test
    @DisplayName("이미 예약된 기간을 요청하면 상세 오류는 빈 배열로 반환한다")
    void 이미_예약된_기간을_요청하면_상세_오류는_빈_배열로_반환한다()
        throws Exception {

        // given
        Long memberId = 1L;

        String accessToken =
            jwtTokenProvider.createAccessToken(
                memberId,
                MemberRole.USER
            );

        /*
         * 예약 불가 기간은 상단 메시지만으로 원인이 충분하므로
         * 같은 내용을 errors 배열에 반복하지 않습니다.
         */
        given(
            reservationFacade.createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            )
        ).willThrow(
            new BusinessException(
                ErrorCode.ROOM_NOT_AVAILABLE
            )
        );

        String requestBody = """
        {
          "roomId": 20,
          "checkInDate": "2099-08-10",
          "checkOutDate": "2099-08-12",
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
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.success")
                    .value(false)
            )
            .andExpect(
                jsonPath("$.code")
                    .value(
                        ErrorCode.ROOM_NOT_AVAILABLE
                            .getCode()
                    )
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        ErrorCode.ROOM_NOT_AVAILABLE
                            .getMessage()
                    )
            )
            .andExpect(
                jsonPath("$.errors.length()")
                    .value(0)
            );

        verify(reservationFacade)
            .createReservation(
                eq(memberId),
                any(ReservationCreateRequestDto.class)
            );
    }
}
