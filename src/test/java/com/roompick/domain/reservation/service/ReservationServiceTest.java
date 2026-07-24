package com.roompick.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.member.entity.Member;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import jakarta.persistence.EntityManager;

/**
 * 예약 날짜 검증과 활성 예약 중복 확인 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Room room;

    @Mock
    private Member member;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("겹치는 활성 예약이 없으면 객실을 예약할 수 있다")
    void roomIsAvailableWithoutOverlappingReservation() {
        // given
        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        given(
            reservationRepository.existsActiveOverlappingReservation(
                eq(1L),
                eq(checkInDate),
                eq(checkOutDate),
                any(LocalDateTime.class)
            )
        ).willReturn(false);

        // when
        boolean available = reservationService.isRoomAvailable(
            1L,
            checkInDate,
            checkOutDate
        );

        // then
        assertThat(available).isTrue();
    }

    @Test
    @DisplayName("겹치는 활성 예약이 있으면 객실을 예약할 수 없다")
    void roomIsUnavailableWithOverlappingReservation() {
        // given
        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        given(
            reservationRepository.existsActiveOverlappingReservation(
                eq(1L),
                eq(checkInDate),
                eq(checkOutDate),
                any(LocalDateTime.class)
            )
        ).willReturn(true);

        // when
        boolean available = reservationService.isRoomAvailable(
            1L,
            checkInDate,
            checkOutDate
        );

        // then
        assertThat(available).isFalse();
    }

    @Test
    @DisplayName("체크인 날짜가 과거이면 예약 가능 여부를 확인할 수 없다")
    void rejectPastCheckInDate() {
        // given
        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).minusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        // when
        BusinessException exception = catchThrowableOfType(
            () -> reservationService.isRoomAvailable(
                1L,
                checkInDate,
                checkOutDate
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_STAY_PERIOD);

        // 잘못된 날짜는 DB까지 조회하지 않습니다.
        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("체크인 날짜와 체크아웃 날짜가 같으면 조회할 수 없다")
    void rejectSameCheckInAndCheckOutDate() {
        // given
        LocalDate sameDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);

        // when
        BusinessException exception = catchThrowableOfType(
            () -> reservationService.isRoomAvailable(
                1L,
                sameDate,
                sameDate
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_STAY_PERIOD);

        verifyNoInteractions(reservationRepository);
    }

    @Test
    @DisplayName("예약 가능한 객실이면 결제 대기 상태의 예약을 생성한다")
    void createPendingPaymentReservation() {
        // given
        Long memberId = 1L;
        Long roomId = 1L;

        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);

        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        given(room.getId())
            .willReturn(roomId);

        given(room.getMaxCapacity())
            .willReturn(2);

        given(room.getPricePerNight())
            .willReturn(100_000L);

        given(
            reservationRepository
                .existsActiveOverlappingReservation(
                    eq(roomId),
                    eq(checkInDate),
                    eq(checkOutDate),
                    any(LocalDateTime.class)
                )
        ).willReturn(false);

        /*
         * 회원 담당 Repository를 직접 조회하지 않고,
         * 인증된 회원 ID의 JPA 참조를 반환하도록 설정합니다.
         */
        given(
            entityManager.getReference(
                Member.class,
                memberId
            )
        ).willReturn(member);

        /*
         * 테스트에서는 DB 저장 대신 전달된 예약 객체를
         * 그대로 반환하도록 설정합니다.
         */
        willAnswer(invocation ->
            invocation.getArgument(0)
        ).given(reservationRepository)
            .save(any(Reservation.class));

        // when
        Reservation reservation =
            reservationService.createReservation(
                memberId,
                room,
                checkInDate,
                checkOutDate,
                2
            );

        // then
        assertThat(reservation.getMember())
            .isEqualTo(member);

        assertThat(reservation.getRoom())
            .isEqualTo(room);

        assertThat(reservation.getCheckInDate())
            .isEqualTo(checkInDate);

        assertThat(reservation.getCheckOutDate())
            .isEqualTo(checkOutDate);

        assertThat(reservation.getGuestCount())
            .isEqualTo(2);

        assertThat(reservation.getNightCount())
            .isEqualTo(2);

        assertThat(reservation.getPricePerNight())
            .isEqualTo(100_000L);

        assertThat(reservation.getTotalAmount())
            .isEqualTo(200_000L);

        assertThat(reservation.getStatus())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(reservation.getExpiresAt())
            .isNotNull();

        verify(reservationRepository)
            .save(reservation);
    }

    @Test
    @DisplayName("겹치는 활성 예약이 있으면 예약을 생성할 수 없다")
    void rejectReservationWithOverlappingReservation() {
        // given
        Long memberId = 1L;
        Long roomId = 1L;

        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);

        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        given(room.getId())
            .willReturn(roomId);

        given(
            reservationRepository
                .existsActiveOverlappingReservation(
                    eq(roomId),
                    eq(checkInDate),
                    eq(checkOutDate),
                    any(LocalDateTime.class)
                )
        ).willReturn(true);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> reservationService.createReservation(
                    memberId,
                    room,
                    checkInDate,
                    checkOutDate,
                    2
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ROOM_NOT_AVAILABLE);

        /*
         * 중복 예약이 확인되면 회원 참조 생성과
         * 예약 저장까지 진행하지 않습니다.
         */
        verifyNoInteractions(entityManager);

        verify(reservationRepository, never())
            .save(any(Reservation.class));
    }
}
