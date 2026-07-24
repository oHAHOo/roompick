package com.roompick.domain.reservation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

/**
 * 객실 Service와 예약 Service를 연결하는
 * ReservationFacade의 예약 생성 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationFacadeTest {

    @Mock
    private RoomService roomService;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationFacade reservationFacade;

    @Test
    @DisplayName("객실을 조회한 뒤 결제 대기 상태의 예약을 생성한다")
    void 객실을_조회한_뒤_예약을_생성한다() {
        // given
        Long memberId = 1L;
        Long accommodationId = 10L;
        Long roomId = 20L;
        Long reservationId = 30L;

        LocalDate checkInDate =
            LocalDate.of(2026, 8, 10);

        LocalDate checkOutDate =
            LocalDate.of(2026, 8, 12);

        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 12, 10);

        ReservationCreateRequestDto request =
            new ReservationCreateRequestDto(
                roomId,
                checkInDate,
                checkOutDate,
                2
            );

        Accommodation accommodation =
            createAccommodation(accommodationId);

        Room room =
            createRoom(
                roomId,
                accommodation
            );

        Member member =
            createMember(memberId);

        Reservation reservation =
            Reservation.create(
                member,
                room,
                checkInDate,
                checkOutDate,
                2,
                expiresAt
            );

        ReflectionTestUtils.setField(
            reservation,
            "id",
            reservationId
        );

        given(
            roomService.findReservableRoomWithAccommodation(
                roomId,
                2
            )
        ).willReturn(room);

        given(
            reservationService.createReservation(
                memberId,
                room,
                checkInDate,
                checkOutDate,
                2
            )
        ).willReturn(reservation);

        // when
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                memberId,
                request
            );

        // then
        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.memberId())
            .isEqualTo(memberId);

        assertThat(response.accommodation().accommodationId())
            .isEqualTo(accommodationId);

        assertThat(response.accommodation().name())
            .isEqualTo("룸픽 호텔");

        assertThat(response.room().roomId())
            .isEqualTo(roomId);

        assertThat(response.room().name())
            .isEqualTo("디럭스 더블룸");

        assertThat(response.room().roomNumber())
            .isEqualTo("101");

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.pricePerNight())
            .isEqualTo(100_000L);

        assertThat(response.totalAmount())
            .isEqualTo(200_000L);

        assertThat(response.status())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(response.expiresAt())
            .isEqualTo(expiresAt);

        then(roomService)
            .should()
            .findReservableRoomWithAccommodation(
                roomId,
                2
            );

        then(reservationService)
            .should()
            .createReservation(
                memberId,
                room,
                checkInDate,
                checkOutDate,
                2
            );
    }

    /**
     * ID가 필요한 단위 테스트 객체를 생성합니다.
     */
    private Accommodation createAccommodation(
        Long accommodationId
    ) {
        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 강남구",
                "RoomPick 테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        ReflectionTestUtils.setField(
            accommodation,
            "id",
            accommodationId
        );

        return accommodation;
    }

    /**
     * 예약 생성 응답에 사용할 객실을 생성합니다.
     */
    private Room createRoom(
        Long roomId,
        Accommodation accommodation
    ) {
        Room room =
            Room.create(
                accommodation,
                "101",
                "디럭스 더블룸",
                "2인이 이용할 수 있는 더블룸",
                100_000L,
                2,
                2
            );

        ReflectionTestUtils.setField(
            room,
            "id",
            roomId
        );

        return room;
    }

    /**
     * 예약 회원의 ID가 필요한 테스트 객체를 생성합니다.
     */
    private Member createMember(Long memberId) {
        Member member =
            Member.create(
                "roompick@example.com",
                "encoded-password",
                "룸픽 회원"
            );

        ReflectionTestUtils.setField(
            member,
            "id",
            memberId
        );

        return member;
    }
}
