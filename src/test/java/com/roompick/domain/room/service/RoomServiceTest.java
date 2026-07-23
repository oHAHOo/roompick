package com.roompick.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalTime;
import java.util.Optional;

import com.roompick.domain.accommodation.entity.AccommodationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private Accommodation accommodation;

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

    @Test
    @DisplayName("객실 가격이 0원이어도 객실을 등록할 수 있다")
    void 객실_가격이_0원이어도_객실을_등록할_수_있다() {
        // given
        given(accommodation.getId())
            .willReturn(1L);
        given(accommodation.getStatus())
            .willReturn(AccommodationStatus.ACTIVE);

        given(
            roomRepository
                .existsByAccommodationIdAndRoomNumber(
                    1L,
                    "101"
                )
        ).willReturn(false);

        given(roomRepository.save(any(Room.class)))
            .willAnswer(invocation ->
                invocation.getArgument(0)
            );

        // when
        Room room = roomService.createRoom(
            accommodation,
            "101",
            "디럭스 더블룸",
            "퀸사이즈 침대가 포함된 객실",
            0L,
            2,
            4
        );

        // then
        assertThat(room.getPricePerNight())
            .isZero();
        assertThat(room.getStatus())
            .isEqualTo(
                com.roompick.domain.room.entity.RoomStatus.ACTIVE
            );

        then(roomRepository)
            .should()
            .save(any(Room.class));
    }

    @Test
    @DisplayName("같은 숙소에 동일한 객실 번호가 존재하면 등록에 실패한다")
    void 동일한_객실_번호가_존재하면_등록에_실패한다() {
        // given
        given(accommodation.getId())
            .willReturn(1L);
        given(accommodation.getStatus())
            .willReturn(AccommodationStatus.ACTIVE);

        given(
            roomRepository
                .existsByAccommodationIdAndRoomNumber(
                    1L,
                    "101"
                )
        ).willReturn(true);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> roomService.createRoom(
                    accommodation,
                    "101",
                    "디럭스 더블룸",
                    "객실 설명",
                    150000L,
                    2,
                    4
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ROOM_NUMBER_DUPLICATED
            );

        then(roomRepository)
            .should(never())
            .save(any(Room.class));
    }

    @Test
    @DisplayName("운영 중지된 숙소에는 객실을 등록할 수 없다")
    void 운영_중지된_숙소에는_객실을_등록할_수_없다() {
        // given
        given(accommodation.getStatus())
            .willReturn(AccommodationStatus.INACTIVE);

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> roomService.createRoom(
                    accommodation,
                    "101",
                    "디럭스 더블룸",
                    "객실 설명",
                    150000L,
                    2,
                    4
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ACCOMMODATION_INACTIVE
            );

        verifyNoInteractions(roomRepository);
    }
}
