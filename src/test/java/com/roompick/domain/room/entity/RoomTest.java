package com.roompick.domain.room.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Room의 생성 규칙을 검증하는 단위 테스트입니다.
 */
class RoomTest {

    @Test
    @DisplayName("정상적인 정보로 객실을 생성할 수 있다")
    void createRoom() {
        // given
        Accommodation accommodation = createAccommodation();

        // when
        Room room = Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸입니다.",
            100_000L,
            2,
            2
        );

        // then
        assertThat(room.getAccommodation()).isEqualTo(accommodation);
        assertThat(room.getRoomNumber()).isEqualTo("101");
        assertThat(room.getName()).isEqualTo("디럭스 더블룸");
        assertThat(room.getPricePerNight()).isEqualTo(100_000L);
        assertThat(room.getStandardCapacity()).isEqualTo(2);
        assertThat(room.getMaxCapacity()).isEqualTo(2);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("소속 숙소가 없으면 객실을 생성할 수 없다")
    void createRoomWithoutAccommodation() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Room.create(
                null,
                "101",
                "디럭스 더블룸",
                "객실 설명",
                100_000L,
                2,
                2
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ROOM_ACCOMMODATION_REQUIRED);
    }

    @Test
    @DisplayName("객실 번호가 공백이면 생성할 수 없다")
    void createRoomWithBlankRoomNumber() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Room.create(
                createAccommodation(),
                " ",
                "디럭스 더블룸",
                "객실 설명",
                100_000L,
                2,
                2
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ROOM_NUMBER_REQUIRED);
    }

    @Test
    @DisplayName("객실 이름이 공백이면 생성할 수 없다")
    void createRoomWithBlankName() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Room.create(
                createAccommodation(),
                "101",
                " ",
                "객실 설명",
                100_000L,
                2,
                2
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ROOM_NAME_REQUIRED);
    }

    @Test
    @DisplayName("객실 가격이 음수이면 생성할 수 없다")
    void createRoomWithNegativePrice() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Room.create(
                createAccommodation(),
                "101",
                "디럭스 더블룸",
                "객실 설명",
                -1L,
                2,
                2
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_ROOM_PRICE);
    }

    @ParameterizedTest
    @CsvSource({
        "0, 2",
        "2, 1"
    })
    @DisplayName("객실 인원 설정이 올바르지 않으면 생성할 수 없다")
    void createRoomWithInvalidCapacity(int standardCapacity, int maxCapacity) {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Room.create(
                createAccommodation(),
                "101",
                "디럭스 더블룸",
                "객실 설명",
                100_000L,
                standardCapacity,
                maxCapacity
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.INVALID_ROOM_CAPACITY);
    }

    // 객실 테스트에서 반복해서 사용할 정상 숙소를 생성합니다.
    private Accommodation createAccommodation() {
        return Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구 테헤란로 123",
            "RoomPick MVP 예약 테스트를 위한 숙소입니다.",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );
    }
}
