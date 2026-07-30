package com.roompick.domain.accommodation.service;

import static org.mockito.BDDMockito.then;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AccommodationServiceTest {

    @Mock
    private AccommodationRepository accommodationRepository;

    @InjectMocks
    private AccommodationService accommodationService;

    @Test
    void 숙소_조회에_성공한다() {
        // given: 조회할 숙소가 존재합니다.
        Long accommodationId = 1L;
        Accommodation accommodation = createAccommodation();

        given(accommodationRepository.findById(accommodationId))
            .willReturn(Optional.of(accommodation));

        // when: 숙소 ID로 조회합니다.
        Accommodation result =
            accommodationService.findById(accommodationId);

        // then: Repository에서 조회한 숙소가 반환됩니다.
        assertThat(result).isSameAs(accommodation);
    }

    @Test
    void 존재하지_않는_숙소를_조회하면_예외가_발생한다() {
        // given: 해당 ID의 숙소가 존재하지 않습니다.
        Long accommodationId = 999L;

        given(accommodationRepository.findById(accommodationId))
            .willReturn(Optional.empty());

        // when & then: 숙소 없음 공통 예외가 발생합니다.
        assertThatThrownBy(
            () -> accommodationService.findById(accommodationId)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ACCOMMODATION_NOT_FOUND);
    }

    private Accommodation createAccommodation() {
        return Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구",
            "RoomPick 테스트 숙소",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );
    }

    @Test
    void 운영_중인_숙소_목록_조회에_성공한다() {
        // given: 요청한 페이지에 운영 중인 숙소가 존재합니다.
        int page = 0;
        int size = 20;

        PageRequest pageable = PageRequest.of(
            page,
            size
        );

        AccommodationListResponseDto accommodationResponse =
            new AccommodationListResponseDto(
                1L,
                "룸픽 호텔",
                "서울특별시 강남구"
            );

        Page<AccommodationListResponseDto> accommodationPage =
            new PageImpl<>(
                List.of(accommodationResponse),
                pageable,
                1
            );

        given(accommodationRepository.findAllActive(pageable))
            .willReturn(accommodationPage);

        // when: 운영 중인 숙소 목록을 조회합니다.
        Page<AccommodationListResponseDto> result =
            accommodationService.findAllActive(
                page,
                size
            );

        // then: Repository에서 조회한 페이지 결과가 반환됩니다.
        assertThat(result).isSameAs(accommodationPage);
        assertThat(result.getContent())
            .containsExactly(accommodationResponse);
    }

    @Test
    void 숙소_목록의_페이지_번호가_음수이면_예외가_발생한다() {
        // given: 페이지 번호가 허용 범위보다 작습니다.
        int page = -1;
        int size = 20;

        // when & then: 잘못된 요청값 공통 예외가 발생합니다.
        assertThatThrownBy(
            () -> accommodationService.findAllActive(
                page,
                size
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void 숙소_목록의_페이지_크기가_허용_범위를_벗어나면_예외가_발생한다(
        int size
    ) {
        // given: 페이지 크기가 1 미만이거나 100을 초과합니다.
        int page = 0;

        // when & then: 잘못된 요청값 공통 예외가 발생합니다.
        assertThatThrownBy(
            () -> accommodationService.findAllActive(
                page,
                size
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 숙소_ID_목록이_비어_있으면_DB를_조회하지_않고_빈_목록을_반환한다() {
        // given: Redis 인기 숙소 랭킹에 숙소 ID가 없습니다.
        List<Long> accommodationIds =
            List.of();

        // when: 운영 중인 숙소 요약 정보를 조회합니다.
        List<AccommodationListResponseDto> result =
            accommodationService.findAllActiveSummaryByIds(
                accommodationIds
            );

        // then: 빈 목록을 반환하고 불필요한 DB 조회는 실행하지 않습니다.
        assertThat(result).isEmpty();

        then(accommodationRepository)
            .shouldHaveNoInteractions();
    }
}
