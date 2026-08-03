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

import com.roompick.domain.accommodation.exception.PopularAccommodationRankingUnavailableException;
import com.roompick.domain.accommodation.repository.PopularAccommodationRankingRepository;
import com.roompick.domain.accommodation.support.PopularAccommodationKeyGenerator;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 인기 숙소 점수 기록과 랭킹 조회 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class PopularAccommodationRankingServiceTest {

    private static final String DAILY_KEY =
        "roompick:popular:accommodations:daily:2026-07-29";

    private static final String WEEKLY_KEY =
        "roompick:popular:accommodations:weekly:2026-07-27";

    @Mock
    private PopularAccommodationRankingRepository
        popularAccommodationRankingRepository;

    @Mock
    private PopularAccommodationKeyGenerator
        popularAccommodationKeyGenerator;

    @InjectMocks
    private PopularAccommodationRankingService
        popularAccommodationRankingService;

    @Test
    void 숙소_조회_점수를_정상적으로_기록한다() {
        // given
        Long accommodationId = 1L;

        when(
            popularAccommodationKeyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.DAILY
            )
        ).thenReturn(
            DAILY_KEY
        );
        when(
            popularAccommodationKeyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.WEEKLY
            )
        ).thenReturn(WEEKLY_KEY);

        // when
        popularAccommodationRankingService.recordView(
            accommodationId
        );

        // then
        verify(
            popularAccommodationRankingRepository
        ).incrementScore(
            DAILY_KEY,
            accommodationId
        );
        verify(
            popularAccommodationRankingRepository
        ).incrementScore(
            WEEKLY_KEY,
            accommodationId
        );
    }

    @Test
    void Redis_장애가_발생해도_예외를_외부로_전달하지_않는다() {
        // given
        Long accommodationId = 1L;

        when(
            popularAccommodationKeyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.DAILY
            )
        ).thenReturn(
            DAILY_KEY
        );
        when(
            popularAccommodationKeyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.WEEKLY
            )
        ).thenReturn(WEEKLY_KEY);

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
            () -> popularAccommodationRankingService.recordView(
                accommodationId
            )
        ).doesNotThrowAnyException();

        verify(
            popularAccommodationRankingRepository
        ).incrementScore(
            WEEKLY_KEY,
            accommodationId
        );
    }

    @Test
    void 인기_숙소_ID를_지정_범위에서_점수_순서대로_조회한다() {
        // given
        int limit = 10;

        List<Long> rankedAccommodationIds =
            List.of(
                3L,
                2L,
                1L
            );

        when(
            popularAccommodationKeyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.DAILY
            )
        ).thenReturn(
            DAILY_KEY
        );

        when(
            popularAccommodationRankingRepository
                .findRankedAccommodationIds(
                    DAILY_KEY,
                    0L,
                    49L
                )
        ).thenReturn(
            rankedAccommodationIds
        );

        // when
        List<Long> result =
            popularAccommodationRankingService
                .findRankedAccommodationIds(
                    PopularAccommodationPeriod.DAILY,
                    limit,
                    0L,
                    49L
                );

        // then
        assertThat(result).containsExactly(
            3L,
            2L,
            1L
        );

        verify(
            popularAccommodationRankingRepository
        ).findRankedAccommodationIds(
            DAILY_KEY,
            0L,
            49L
        );
    }

    @Test
    void Redis_랭킹_조회_장애를_전용_예외로_변환하고_cause를_보존한다() {
        // given
        int limit = 10;
        DataAccessResourceFailureException redisException =
            new DataAccessResourceFailureException(
                "Redis connection failed"
            );

        when(
            popularAccommodationKeyGenerator.generateCurrentKey(
                PopularAccommodationPeriod.WEEKLY
            )
        ).thenReturn(DAILY_KEY);

        when(
            popularAccommodationRankingRepository
                .findRankedAccommodationIds(
                    DAILY_KEY,
                    0L,
                    49L
                )
        ).thenThrow(redisException);

        // when & then
        assertThatThrownBy(() ->
            popularAccommodationRankingService
                .findRankedAccommodationIds(
                    PopularAccommodationPeriod.WEEKLY,
                    limit,
                    0L,
                    49L
                )
        )
            .isInstanceOf(
                PopularAccommodationRankingUnavailableException.class
            )
            .hasCause(redisException);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void 인기_숙소_limit이_허용_범위를_벗어나면_예외가_발생한다(
        int invalidLimit
    ) {
        // when & then
        assertThatThrownBy(
            () -> popularAccommodationRankingService
                .findRankedAccommodationIds(
                    PopularAccommodationPeriod.DAILY,
                    invalidLimit,
                    0L,
                    99L
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
