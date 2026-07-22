package com.roompick.domain.accommodation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Accommodation의 생성 규칙을 검증하는 단위 테스트입니다.
 */
class AccommodationTest {

    @Test
    @DisplayName("정상적인 정보로 숙소를 생성할 수 있다")
    void createAccommodation() {
        // given
        String name = "룸픽 호텔";
        String address = "서울특별시 강남구 테헤란로 123";
        String description = "RoomPick MVP 예약 테스트를 위한 숙소입니다.";
        LocalTime checkInTime = LocalTime.of(15, 0);
        LocalTime checkOutTime = LocalTime.of(11, 0);

        // when
        Accommodation accommodation = Accommodation.create(
            name,
            address,
            description,
            checkInTime,
            checkOutTime
        );

        // then
        assertThat(accommodation.getName()).isEqualTo(name);
        assertThat(accommodation.getAddress()).isEqualTo(address);
        assertThat(accommodation.getDescription()).isEqualTo(description);
        assertThat(accommodation.getCheckInTime()).isEqualTo(checkInTime);
        assertThat(accommodation.getCheckOutTime()).isEqualTo(checkOutTime);
        assertThat(accommodation.getStatus()).isEqualTo(AccommodationStatus.ACTIVE);
    }

    @Test
    @DisplayName("숙소 이름이 공백이면 생성할 수 없다")
    void createAccommodationWithBlankName() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Accommodation.create(
                " ",
                "서울특별시 강남구 테헤란로 123",
                "숙소 설명",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ACCOMMODATION_NAME_REQUIRED);
    }

    @Test
    @DisplayName("숙소 주소가 없으면 생성할 수 없다")
    void createAccommodationWithoutAddress() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Accommodation.create(
                "룸픽 호텔",
                null,
                "숙소 설명",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ACCOMMODATION_ADDRESS_REQUIRED);
    }

    @Test
    @DisplayName("체크인 시간이 없으면 숙소를 생성할 수 없다")
    void createAccommodationWithoutCheckInTime() {
        // when
        BusinessException exception = catchThrowableOfType(
            () -> Accommodation.create(
                "룸픽 호텔",
                "서울특별시 강남구 테헤란로 123",
                "숙소 설명",
                null,
                LocalTime.of(11, 0)
            ),
            BusinessException.class
        );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.ACCOMMODATION_TIME_REQUIRED);
    }
}
