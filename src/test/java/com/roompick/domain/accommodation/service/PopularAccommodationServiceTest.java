package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.roompick.domain.accommodation.repository.PopularAccommodationRankingRepository;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;

/**
 * PopularAccommodationService의 인기 숙소 조회 점수 기록 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class PopularAccommodationServiceTest {

    private static final String DAILY_KEY =
        "roompick:popular:accommodations:daily:2026-07-29";

    @Mock
    private PopularAccommodationRankingRepository
        popularAccommodationRankingRepository;

    @Mock
    private PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    @InjectMocks
    private PopularAccommodationService popularAccommodationService;

    @Test
    void 숙소_조회_점수를_정상적으로_기록한다() {
        // given
        Long accommodationId = 1L;

        when(
            popularAccommodationKeyGenerator.generateTodayKey()
        ).thenReturn(
            DAILY_KEY
        );

        // when
        popularAccommodationService.recordView(
            accommodationId
        );

        // then
        verify(
            popularAccommodationRankingRepository
        ).incrementScore(
            DAILY_KEY,
            accommodationId
        );
    }

    @Test
    void Redis_장애가_발생해도_예외를_외부로_전달하지_않는다() {
        // given
        Long accommodationId = 1L;

        when(
            popularAccommodationKeyGenerator.generateTodayKey()
        ).thenReturn(
            DAILY_KEY
        );

        doThrow(
            new DataAccessResourceFailureException(
                "Redis connection failed"
            )
        ).when(
            popularAccommodationRankingRepository
        ).incrementScore(
            DAILY_KEY,
            accommodationId
        );

        // when & then
        assertThatCode(
            () -> popularAccommodationService.recordView(
                accommodationId
            )
        ).doesNotThrowAnyException();
    }
}
