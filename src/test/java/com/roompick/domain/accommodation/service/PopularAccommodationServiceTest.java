package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.roompick.domain.accommodation.repository.PopularAccommodationRankingRepository;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 인기 숙소 점수 기록과 랭킹 조회 흐름을 검증합니다.
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
    private PopularAccommodationService
        popularAccommodationService;

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

    @Test
    void 인기_숙소_ID를_요청한_limit만큼_조회한다() {
        // given
        int limit = 10;

        List<Long> rankedAccommodationIds =
            List.of(
                3L,
                2L,
                1L
            );

        when(
            popularAccommodationKeyGenerator.generateTodayKey()
        ).thenReturn(
            DAILY_KEY
        );

        when(
            popularAccommodationRankingRepository
                .findTopAccommodationIds(
                    DAILY_KEY,
                    limit
                )
        ).thenReturn(
            rankedAccommodationIds
        );

        // when
        List<Long> result =
            popularAccommodationService
                .findTopAccommodationIds(
                    limit
                );

        // then
        assertThat(result).containsExactly(
            3L,
            2L,
            1L
        );

        verify(
            popularAccommodationRankingRepository
        ).findTopAccommodationIds(
            DAILY_KEY,
            limit
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void 인기_숙소_limit이_허용_범위를_벗어나면_예외가_발생한다(
        int invalidLimit
    ) {
        // when & then
        assertThatThrownBy(
            () -> popularAccommodationService
                .findTopAccommodationIds(
                    invalidLimit
                )
        )
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        /*
         * limit 검증에 실패하면 Redis 키 생성과
         * Redis Repository 조회를 실행하지 않아야 합니다.
         */
        verifyNoInteractions(
            popularAccommodationKeyGenerator,
            popularAccommodationRankingRepository
        );
    }
}
