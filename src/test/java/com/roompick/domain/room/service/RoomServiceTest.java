package com.roompick.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private RoomService roomService;

    @Test
    void 객실_상세_조회에_성공한다() {
        // given: 조회할 객실과 소속 숙소가 존재합니다.
        Long roomId = 1L;
        Room room = createRoom();

        given(roomRepository.findByIdWithAccommodation(roomId))
            .willReturn(Optional.of(room));

        // when: 객실 ID로 상세 조회합니다.
        Room result = roomService.findById(roomId);

        // then: Repository에서 조회한 객실이 반환됩니다.
        assertThat(result).isSameAs(room);
    }

    @Test
    void 존재하지_않는_객실을_조회하면_예외가_발생한다() {
        // given: 해당 ID의 객실이 존재하지 않습니다.
        Long roomId = 999L;

        given(roomRepository.findByIdWithAccommodation(roomId))
            .willReturn(Optional.empty());

        // when & then: 객실 없음 공통 예외가 발생합니다.
        assertThatThrownBy(() -> roomService.findById(roomId))
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void 운영_중인_객실이고_인원이_적절하면_예약용_객실을_반환한다() {
        // given
        Long roomId = 1L;
        Room room = createRoom();

        given(roomRepository.findById(roomId))
            .willReturn(Optional.of(room));

        // when
        Room result = roomService.findReservableRoom(
            roomId,
            2
        );

        // then
        assertThat(result).isSameAs(room);
    }

    @Test
    void 예약_인원이_1명_미만이면_예외가_발생한다() {
        // given
        Long roomId = 1L;
        Room room = createRoom();

        given(roomRepository.findById(roomId))
            .willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() ->
            roomService.findReservableRoom(roomId, 0)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.INVALID_GUEST_COUNT);
    }

    @Test
    void 객실_최대_인원을_초과하면_예외가_발생한다() {
        // given
        Long roomId = 1L;
        Room room = createRoom();

        given(roomRepository.findById(roomId))
            .willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() ->
            roomService.findReservableRoom(roomId, 3)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ROOM_CAPACITY_EXCEEDED);
    }

    @Test
    void 운영_중지된_객실은_예약할_수_없다() {
        // given
        Long roomId = 1L;
        Room room = createRoom();

        // public setter 없이 INACTIVE 상태의 테스트 객체를 만듭니다.
        ReflectionTestUtils.setField(
            room,
            "status",
            RoomStatus.INACTIVE
        );

        given(roomRepository.findById(roomId))
            .willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() ->
            roomService.findReservableRoom(roomId, 2)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ROOM_INACTIVE);
    }

    private Room createRoom() {
        Accommodation accommodation = Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구",
            "RoomPick 테스트 숙소",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );

        return Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸",
            100000L,
            2,
            2
        );
    }
}
