package com.roompick.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;

/**
 * 예약 생성 요청 내용으로 생성하는
 * SHA-256 요청 해시를 검증합니다.
 */
class ReservationRequestHasherTest {

    private final ReservationRequestHasher
        reservationRequestHasher =
        new ReservationRequestHasher();

    @Test
    @DisplayName(
        "동일한 예약 요청은 항상 동일한 해시를 생성한다"
    )
    void 동일한_예약_요청은_동일한_해시를_생성한다() {
        // given
        ReservationCreateRequestDto firstRequest =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2
            );

        ReservationCreateRequestDto secondRequest =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2
            );

        // when
        String firstHash =
            reservationRequestHasher.hash(
                firstRequest
            );

        String secondHash =
            reservationRequestHasher.hash(
                secondRequest
            );

        // then
        assertThat(firstHash)
            .isEqualTo(secondHash);
    }

    @Test
    @DisplayName(
        "예약 요청 내용이 다르면 서로 다른 해시를 생성한다"
    )
    void 예약_요청_내용이_다르면_다른_해시를_생성한다() {
        // given
        ReservationCreateRequestDto originalRequest =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2
            );

        ReservationCreateRequestDto differentRoomRequest =
            new ReservationCreateRequestDto(
                21L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2
            );

        ReservationCreateRequestDto differentCheckInRequest =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 12),
                2
            );

        ReservationCreateRequestDto differentCheckOutRequest =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 13),
                2
            );

        ReservationCreateRequestDto differentGuestCountRequest =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                1
            );

        // when
        List<String> requestHashes =
            List.of(
                reservationRequestHasher.hash(
                    originalRequest
                ),
                reservationRequestHasher.hash(
                    differentRoomRequest
                ),
                reservationRequestHasher.hash(
                    differentCheckInRequest
                ),
                reservationRequestHasher.hash(
                    differentCheckOutRequest
                ),
                reservationRequestHasher.hash(
                    differentGuestCountRequest
                )
            );

        // then
        assertThat(requestHashes)
            .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName(
        "예약 요청 해시는 64자리 소문자 16진수이다"
    )
    void 예약_요청_해시는_SHA_256_형식이다() {
        // given
        ReservationCreateRequestDto request =
            new ReservationCreateRequestDto(
                20L,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2
            );

        // when
        String requestHash =
            reservationRequestHasher.hash(
                request
            );

        // then
        assertThat(requestHash)
            .hasSize(64)
            .matches("[0-9a-f]{64}");
    }
}
