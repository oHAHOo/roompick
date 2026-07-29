package com.roompick.domain.admin.room.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.room.dto.request.RoomCreateRequestDto;
import com.roompick.domain.admin.room.dto.request.RoomStatusUpdateRequestDto;
import com.roompick.domain.admin.room.dto.response.RoomCreateResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.service.RoomService;

@ExtendWith(MockitoExtension.class)
class AdminRoomFacadeTest {

    @Mock
    private AccommodationService accommodationService;

    @Mock
    private RoomService roomService;

    @Mock
    private Accommodation accommodation;

    @Mock
    private Room room;

    @InjectMocks
    private AdminRoomFacade adminRoomFacade;

    @Test
    @DisplayName("관리자 객실 등록 요청을 처리한다")
    void 관리자_객실_등록_요청을_처리한다() {
        // given
        Long accommodationId = 1L;

        RoomCreateRequestDto request =
            new RoomCreateRequestDto(
                "101",
                "디럭스 더블룸",
                "퀸사이즈 침대가 포함된 객실",
                150000L,
                2,
                4
            );

        given(
            accommodationService.findById(accommodationId)
        ).willReturn(accommodation);

        given(
            roomService.createRoom(
                accommodation,
                request.roomNumber(),
                request.name(),
                request.description(),
                request.pricePerNight(),
                request.standardCapacity(),
                request.maxCapacity()
            )
        ).willReturn(room);

        given(room.getId())
            .willReturn(10L);
        given(room.getAccommodation())
            .willReturn(accommodation);
        given(accommodation.getId())
            .willReturn(accommodationId);
        given(room.getRoomNumber())
            .willReturn(request.roomNumber());
        given(room.getName())
            .willReturn(request.name());
        given(room.getDescription())
            .willReturn(request.description());
        given(room.getPricePerNight())
            .willReturn(request.pricePerNight());
        given(room.getStandardCapacity())
            .willReturn(request.standardCapacity());
        given(room.getMaxCapacity())
            .willReturn(request.maxCapacity());
        given(room.getStatus())
            .willReturn(RoomStatus.INACTIVE);

        // when
        RoomCreateResponseDto response =
            adminRoomFacade.createRoom(
                accommodationId,
                request
            );

        // then
        assertThat(response.roomId())
            .isEqualTo(10L);
        assertThat(response.accommodationId())
            .isEqualTo(1L);
        assertThat(response.roomNumber())
            .isEqualTo("101");
        assertThat(response.name())
            .isEqualTo("디럭스 더블룸");
        assertThat(response.description())
            .isEqualTo("퀸사이즈 침대가 포함된 객실");
        assertThat(response.pricePerNight())
            .isEqualTo(150000L);
        assertThat(response.standardCapacity())
            .isEqualTo(2);
        assertThat(response.maxCapacity())
            .isEqualTo(4);
        assertThat(response.status())
            .isEqualTo(RoomStatus.INACTIVE);

        then(accommodationService)
            .should()
            .findById(accommodationId);

        then(roomService)
            .should()
            .createRoom(
                accommodation,
                request.roomNumber(),
                request.name(),
                request.description(),
                request.pricePerNight(),
                request.standardCapacity(),
                request.maxCapacity()
            );
    }

    @Test
    @DisplayName("관리자 객실 공개 요청을 처리한다")
    void 관리자_객실_공개_요청을_처리한다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        RoomStatusUpdateRequestDto request =
            new RoomStatusUpdateRequestDto(RoomStatus.ACTIVE);

        given(
            roomService.activateRoom(accommodationId, roomId)
        ).willReturn(room);
        given(room.getId()).willReturn(roomId);
        given(room.getStatus()).willReturn(RoomStatus.ACTIVE);

        // when
        var response = adminRoomFacade.updateRoomStatus(
            accommodationId,
            roomId,
            request
        );

        // then
        assertThat(response.roomId()).isEqualTo(roomId);
        assertThat(response.status()).isEqualTo(RoomStatus.ACTIVE);
        then(roomService).should()
            .activateRoom(accommodationId, roomId);
    }

    @Test
    @DisplayName("관리자 객실 비공개 요청을 처리한다")
    void 관리자_객실_비공개_요청을_처리한다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        RoomStatusUpdateRequestDto request =
            new RoomStatusUpdateRequestDto(RoomStatus.INACTIVE);

        given(
            roomService.deactivateRoom(accommodationId, roomId)
        ).willReturn(room);
        given(room.getId()).willReturn(roomId);
        given(room.getStatus()).willReturn(RoomStatus.INACTIVE);

        // when
        var response = adminRoomFacade.updateRoomStatus(
            accommodationId,
            roomId,
            request
        );

        // then
        assertThat(response.status()).isEqualTo(RoomStatus.INACTIVE);
        then(roomService).should()
            .deactivateRoom(accommodationId, roomId);
    }
}
