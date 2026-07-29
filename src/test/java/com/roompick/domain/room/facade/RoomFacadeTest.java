package com.roompick.domain.room.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.dto.RoomAvailabilityRequestDto;
import com.roompick.domain.room.dto.RoomAvailabilityResponseDto;
import com.roompick.domain.room.dto.RoomAvailabilityStatus;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

/**
 * 객실과 예약 Service를 연결하는 RoomFacade의 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RoomFacadeTest {

    @Mock
    private RoomService roomService;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private RoomFacade roomFacade;

    @Test
    @DisplayName("객실을 예약할 수 있으면 예상 금액과 available true를 반환한다")
    void getAvailableRoom() {
        // given
        Long roomId = 1L;
        Room room = createRoom(roomId);
        RoomAvailabilityRequestDto request =
            createAvailabilityRequest();

        given(
            roomService.findReservableRoom(
                roomId,
                request.guestCount()
            )
        ).willReturn(room);

        given(
            reservationService.isRoomAvailable(
                roomId,
                request.checkInDate(),
                request.checkOutDate()
            )
        ).willReturn(true);

        // when
        RoomAvailabilityResponseDto response =
            roomFacade.getRoomAvailability(
                roomId,
                request
            );

        // then
        assertThat(response.roomId()).isEqualTo(roomId);
        assertThat(response.nightCount()).isEqualTo(2);
        assertThat(response.pricePerNight()).isEqualTo(100_000L);
        assertThat(response.totalAmount()).isEqualTo(200_000L);
        assertThat(response.available()).isTrue();
        assertThat(response.status())
            .isEqualTo(RoomAvailabilityStatus.ACTIVE);
        assertThat(response.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("겹치는 예약이 있으면 available false와 사유를 반환한다")
    void getUnavailableRoom() {
        // given
        Long roomId = 1L;
        Room room = createRoom(roomId);
        RoomAvailabilityRequestDto request =
            createAvailabilityRequest();

        given(
            roomService.findReservableRoom(
                roomId,
                request.guestCount()
            )
        ).willReturn(room);

        given(
            reservationService.isRoomAvailable(
                roomId,
                request.checkInDate(),
                request.checkOutDate()
            )
        ).willReturn(false);

        // when
        RoomAvailabilityResponseDto response =
            roomFacade.getRoomAvailability(
                roomId,
                request
            );

        // then
        assertThat(response.available()).isFalse();
        assertThat(response.status())
            .isEqualTo(RoomAvailabilityStatus.SOLD_OUT);
        assertThat(response.unavailableReason())
            .isEqualTo("선택한 날짜에 이미 예약된 객실입니다.");
    }

    private RoomAvailabilityRequestDto createAvailabilityRequest() {
        return new RoomAvailabilityRequestDto(
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 12),
            2
        );
    }

    private Room createRoom(Long roomId) {
        Accommodation accommodation = Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구",
            "RoomPick 테스트 숙소",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );

        Room room = Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸",
            100_000L,
            2,
            2
        );

        room.activate();

        // 저장하지 않은 단위 테스트 객체에 ID를 지정합니다.
        ReflectionTestUtils.setField(
            room,
            "id",
            roomId
        );

        return room;
    }
}
