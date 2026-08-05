package com.roompick.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 예약 생성에 사용하는 객실 비관적 락 조회와
 * 객실·숙소 상태 및 예약 인원 검증을 확인합니다.
 *
 * 실제 DB의 락 대기와 트랜잭션 직렬화는
 * MySQL 동시성 통합 테스트에서 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class RoomServicePessimisticLockTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private Room room;

    @Mock
    private Accommodation accommodation;

    @InjectMocks
    private RoomService roomService;

    @Test
    @DisplayName(
        "예약 가능한 객실을 비관적 쓰기 락으로 조회한다"
    )
    void 예약_가능한_객실을_비관적_락으로_조회한다() {
        // given
        Long roomId = 1L;
        int guestCount = 2;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willReturn(
            Optional.of(room)
        );

        given(room.getStatus())
            .willReturn(RoomStatus.ACTIVE);

        given(room.getAccommodation())
            .willReturn(accommodation);

        given(accommodation.getStatus())
            .willReturn(
                AccommodationStatus.ACTIVE
            );

        given(room.getMaxCapacity())
            .willReturn(2);

        // when
        Room result =
            roomService
                .findReservableRoomForUpdate(
                    roomId,
                    guestCount
                );

        // then
        assertThat(result)
            .isSameAs(room);

        then(roomRepository)
            .should()
            .findByIdForUpdate(roomId);

        then(room)
            .should()
            .getStatus();

        then(room)
            .should()
            .getAccommodation();

        then(accommodation)
            .should()
            .getStatus();

        then(room)
            .should()
            .getMaxCapacity();
    }

    @Test
    @DisplayName(
        "존재하지 않는 객실은 비관적 락으로 조회할 수 없다"
    )
    void 존재하지_않는_객실은_락으로_조회할_수_없다() {
        // given
        Long roomId = 999L;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willReturn(
            Optional.empty()
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    roomService
                        .findReservableRoomForUpdate(
                            roomId,
                            2
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ROOM_NOT_FOUND
            );

        then(roomRepository)
            .should()
            .findByIdForUpdate(roomId);

        then(room)
            .shouldHaveNoInteractions();

        then(accommodation)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "비활성 객실은 락을 획득해도 예약할 수 없다"
    )
    void 비활성_객실은_락을_획득해도_예약할_수_없다() {
        // given
        Long roomId = 1L;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willReturn(
            Optional.of(room)
        );

        given(room.getStatus())
            .willReturn(RoomStatus.INACTIVE);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    roomService
                        .findReservableRoomForUpdate(
                            roomId,
                            2
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ROOM_INACTIVE
            );

        then(roomRepository)
            .should()
            .findByIdForUpdate(roomId);

        /*
         * OR 조건의 단축 평가로 객실이 비활성이면
         * 숙소 상태와 최대 인원을 조회하지 않습니다.
         */
        then(room)
            .should(never())
            .getAccommodation();

        then(room)
            .should(never())
            .getMaxCapacity();

        then(accommodation)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "비활성 숙소의 객실은 락을 획득해도 예약할 수 없다"
    )
    void 비활성_숙소의_객실은_락을_획득해도_예약할_수_없다() {
        // given
        Long roomId = 1L;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willReturn(
            Optional.of(room)
        );

        given(room.getStatus())
            .willReturn(RoomStatus.ACTIVE);

        given(room.getAccommodation())
            .willReturn(accommodation);

        given(accommodation.getStatus())
            .willReturn(
                AccommodationStatus.INACTIVE
            );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    roomService
                        .findReservableRoomForUpdate(
                            roomId,
                            2
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ROOM_INACTIVE
            );

        then(roomRepository)
            .should()
            .findByIdForUpdate(roomId);

        then(room)
            .should(never())
            .getMaxCapacity();
    }

    @Test
    @DisplayName(
        "객실 최대 인원을 초과하면 락을 획득해도 예약할 수 없다"
    )
    void 최대_인원을_초과하면_예약할_수_없다() {
        // given
        Long roomId = 1L;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willReturn(
            Optional.of(room)
        );

        given(room.getStatus())
            .willReturn(RoomStatus.ACTIVE);

        given(room.getAccommodation())
            .willReturn(accommodation);

        given(accommodation.getStatus())
            .willReturn(
                AccommodationStatus.ACTIVE
            );

        given(room.getMaxCapacity())
            .willReturn(2);

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    roomService
                        .findReservableRoomForUpdate(
                            roomId,
                            3
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.ROOM_CAPACITY_EXCEEDED
            );

        assertThat(exception.getFieldErrors())
            .hasSize(1);

        assertThat(
            exception
                .getFieldErrors()
                .get(0)
                .field()
        ).isEqualTo("guestCount");

        then(roomRepository)
            .should()
            .findByIdForUpdate(roomId);
    }

    @Test
    @DisplayName(
        "예약 인원이 1명 미만이면 최대 인원을 조회하지 않는다"
    )
    void 예약_인원이_1명_미만이면_거절한다() {
        // given
        Long roomId = 1L;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willReturn(
            Optional.of(room)
        );

        given(room.getStatus())
            .willReturn(RoomStatus.ACTIVE);

        given(room.getAccommodation())
            .willReturn(accommodation);

        given(accommodation.getStatus())
            .willReturn(
                AccommodationStatus.ACTIVE
            );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    roomService
                        .findReservableRoomForUpdate(
                            roomId,
                            0
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.INVALID_GUEST_COUNT
            );

        then(room)
            .should(never())
            .getMaxCapacity();
    }

    @Test
    @DisplayName(
        "객실 락 대기 시간이 초과되면 예약 전용 예외로 변환한다"
    )
    void 객실_락_대기_시간_초과를_예약_예외로_변환한다() {
        // given
        Long roomId = 1L;

        given(
            roomRepository.findByIdForUpdate(
                roomId
            )
        ).willThrow(
            new PessimisticLockingFailureException(
                "lock timeout"
            )
        );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () ->
                    roomService
                        .findReservableRoomForUpdate(
                            roomId,
                            2
                        ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_LOCK_TIMEOUT
            );

        then(roomRepository)
            .should()
            .findByIdForUpdate(roomId);

        then(room)
            .shouldHaveNoInteractions();

        then(accommodation)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "비관적 락 조회는 기존 트랜잭션 안에서만 실행된다"
    )
    void 비관적_락_조회는_기존_트랜잭션을_요구한다()
        throws NoSuchMethodException {

        // given
        Transactional transactional =
            RoomService.class
                .getMethod(
                    "findReservableRoomForUpdate",
                    Long.class,
                    int.class
                )
                .getAnnotation(
                    Transactional.class
                );

        // when & then
        assertThat(transactional)
            .isNotNull();

        assertThat(transactional.propagation())
            .isEqualTo(
                Propagation.MANDATORY
            );
    }
}
