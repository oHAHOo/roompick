package com.roompick.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.roompick.domain.accommodation.entity.AccommodationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.PopularAccommodationCacheEvictionService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PopularAccommodationCacheEvictionService
        popularAccommodationCacheEvictionService;

    @Mock
    private Accommodation accommodation;

    @InjectMocks
    private RoomService roomService;

    @Test
    void 객실_상세_조회에_성공한다() {
        // given: 조회할 객실과 소속 숙소가 존재합니다.
        Long roomId = 1L;
        Room room = createRoom();

        given(roomRepository.findPublicById(
            roomId,
            RoomStatus.ACTIVE,
            AccommodationStatus.ACTIVE
        ))
            .willReturn(Optional.of(room));

        // when: 객실 ID로 상세 조회합니다.
        Room result = roomService.findActiveById(roomId);

        // then: Repository에서 조회한 객실이 반환됩니다.
        assertThat(result).isSameAs(room);
    }

    @Test
    void 존재하지_않는_객실을_조회하면_예외가_발생한다() {
        // given: 해당 ID의 객실이 존재하지 않습니다.
        Long roomId = 999L;

        given(roomRepository.findPublicById(
            roomId,
            RoomStatus.ACTIVE,
            AccommodationStatus.ACTIVE
        ))
            .willReturn(Optional.empty());

        // when & then: 객실 없음 공통 예외가 발생합니다.
        assertThatThrownBy(() -> roomService.findActiveById(roomId))
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void 운영_중지된_숙소의_운영_중인_객실은_상세_조회할_수_없다() {
        // given: Repository의 공개 조건에서 숙소 상태가 함께 걸러집니다.
        Long roomId = 1L;

        given(roomRepository.findPublicById(
            roomId,
            RoomStatus.ACTIVE,
            AccommodationStatus.ACTIVE
        )).willReturn(Optional.empty());

        // when & then: 비공개 자원의 존재를 노출하지 않습니다.
        assertThatThrownBy(() -> roomService.findActiveById(roomId))
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

        given(roomRepository.findByIdWithAccommodation(roomId))
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

        given(roomRepository.findByIdWithAccommodation(roomId))
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

        given(roomRepository.findByIdWithAccommodation(roomId))
            .willReturn(Optional.of(room));

        // when
        BusinessException exception =
            assertThrows(
                BusinessException.class,
                () -> roomService.findReservableRoom(
                    roomId,
                    3
                )
            );

        // then: 기존 비즈니스 에러 코드를 유지합니다.
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ROOM_CAPACITY_EXCEEDED
            );

        /*
         * 상단 에러 메시지보다 구체적인 최대 허용 인원을
         * guestCount 필드 상세 오류로 전달하는지 확인합니다.
         */
        assertThat(exception.getFieldErrors())
            .hasSize(1);

        assertThat(
            exception.getFieldErrors()
                .get(0)
                .field()
        ).isEqualTo("guestCount");

        assertThat(
            exception.getFieldErrors()
                .get(0)
                .message()
        ).isEqualTo(
            "선택한 객실은 최대 2명까지 예약할 수 있습니다."
        );
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

        given(roomRepository.findByIdWithAccommodation(roomId))
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

    @Test
    void 운영_중지된_숙소의_운영_중인_객실은_예약_가능_여부를_조회할_수_없다() {
        // given
        Long roomId = 1L;
        Room room = createRoom();
        deactivateAccommodation(room);

        given(roomRepository.findByIdWithAccommodation(roomId))
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

        Room room = Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸",
            100000L,
            2,
            2
        );

        room.activate();

        return room;
    }

    private void deactivateAccommodation(Room room) {
        ReflectionTestUtils.setField(
            room.getAccommodation(),
            "status",
            AccommodationStatus.INACTIVE
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
            4,
            List.of()
        );

        // then
        assertThat(room.getPricePerNight())
            .isZero();
        assertThat(room.getStatus())
            .isEqualTo(RoomStatus.INACTIVE);

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
                    4,
                    List.of()
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
                    4,
                    List.of()
                )
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ACCOMMODATION_INACTIVE
            );

        verifyNoInteractions(roomRepository);
    }

    @Test
    @DisplayName("관리자가 같은 숙소의 객실을 공개할 수 있다")
    void 객실을_공개할_수_있다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        Room room = createRoom();
        room.deactivate();

        given(
            roomRepository.findByIdAndAccommodationIdWithAccommodation(
                roomId,
                accommodationId
            )
        ).willReturn(Optional.of(room));

        // when
        Room result = roomService.activateRoom(
            accommodationId,
            roomId
        );

        // then
        assertThat(result.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        then(roomRepository).should(never()).save(any(Room.class));
    }

    @Test
    @DisplayName("관리자가 같은 숙소의 객실을 비공개할 수 있다")
    void 객실을_비공개할_수_있다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        Room room = createRoom();

        given(
            roomRepository.findByIdAndAccommodationId(
                roomId,
                accommodationId
            )
        ).willReturn(Optional.of(room));

        // when
        Room result = roomService.deactivateRoom(
            accommodationId,
            roomId
        );

        // then
        assertThat(result.getStatus()).isEqualTo(RoomStatus.INACTIVE);
        then(roomRepository).should(never()).save(any(Room.class));
        then(popularAccommodationCacheEvictionService)
            .should()
            .evictAll();
    }

    @Test
    @DisplayName("운영 중지된 숙소의 객실은 공개할 수 없다")
    void 운영_중지된_숙소의_객실은_공개할_수_없다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        Room room = createRoom();
        room.deactivate();
        deactivateAccommodation(room);

        given(
            roomRepository.findByIdAndAccommodationIdWithAccommodation(
                roomId,
                accommodationId
            )
        ).willReturn(Optional.of(room));

        // when & then
        assertThatThrownBy(() ->
            roomService.activateRoom(accommodationId, roomId)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ACCOMMODATION_INACTIVE);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.INACTIVE);
        then(roomRepository).should(never()).save(any(Room.class));
    }

    @Test
    @DisplayName("이미 공개된 객실을 다시 공개해도 성공한다")
    void 객실_공개는_멱등하게_동작한다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        Room room = createRoom();

        given(
            roomRepository.findByIdAndAccommodationIdWithAccommodation(
                roomId,
                accommodationId
            )
        ).willReturn(Optional.of(room));

        // when
        Room result = roomService.activateRoom(accommodationId, roomId);

        // then
        assertThat(result.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        then(roomRepository).should(never()).save(any(Room.class));
    }

    @Test
    @DisplayName("이미 비공개된 객실을 다시 비공개해도 성공한다")
    void 객실_비공개는_멱등하게_동작한다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;
        Room room = createRoom();
        room.deactivate();

        given(
            roomRepository.findByIdAndAccommodationId(
                roomId,
                accommodationId
            )
        ).willReturn(Optional.of(room));

        // when
        Room result = roomService.deactivateRoom(accommodationId, roomId);

        // then
        assertThat(result.getStatus()).isEqualTo(RoomStatus.INACTIVE);
        then(roomRepository).should(never()).save(any(Room.class));
        then(popularAccommodationCacheEvictionService)
            .should()
            .evictAll();
    }

    @Test
    @DisplayName("다른 숙소에 소속된 객실의 상태 변경 요청은 404로 처리한다")
    void 다른_숙소의_객실은_상태를_변경할_수_없다() {
        // given
        Long accommodationId = 1L;
        Long roomId = 10L;

        given(
            roomRepository.findByIdAndAccommodationIdWithAccommodation(
                roomId,
                accommodationId
            )
        ).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            roomService.activateRoom(accommodationId, roomId)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }
}
