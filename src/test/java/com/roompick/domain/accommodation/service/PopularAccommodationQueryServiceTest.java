package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;

@ExtendWith(MockitoExtension.class)
class PopularAccommodationQueryServiceTest {

    @Mock
    private PopularAccommodationRankingService
        popularAccommodationRankingService;

    @Mock
    private AccommodationService accommodationService;

    @InjectMocks
    private PopularAccommodationQueryService
        popularAccommodationQueryService;

    @Test
    @DisplayName("첫 batch에서 limit을 채우면 다음 Redis 범위를 조회하지 않는다")
    void stopAfterFirstBatchWhenLimitIsFilled() {
        // given
        int limit = 2;
        List<Long> firstBatch = List.of(3L, 2L, 1L);

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 9L)
        ).willReturn(firstBatch);

        given(
            accommodationService.findAllActiveSummaryByIds(firstBatch)
        ).willReturn(
            List.of(
                accommodation(2L),
                accommodation(3L),
                accommodation(1L)
            )
        );

        // when
        List<PopularAccommodationResponseDto> result =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then
        assertThat(result)
            .extracting(PopularAccommodationResponseDto::accommodationId)
            .containsExactly(3L, 2L);
        assertThat(result)
            .extracting(PopularAccommodationResponseDto::rank)
            .containsExactly(1, 2);

        then(popularAccommodationRankingService)
            .should(never())
            .findRankedAccommodationIds(limit, 10L, 19L);
    }

    @Test
    @DisplayName("첫 batch의 ACTIVE 숙소가 부족하면 다음 batch로 limit을 채운다")
    void readNextBatchWhenActiveAccommodationsAreInsufficient() {
        // given
        int limit = 2;
        List<Long> firstBatch =
            List.of(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);
        List<Long> secondBatch = List.of(20L, 19L);

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 9L)
        ).willReturn(firstBatch);
        given(
            accommodationService.findAllActiveSummaryByIds(firstBatch)
        ).willReturn(List.of(accommodation(8L)));
        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 10L, 19L)
        ).willReturn(secondBatch);
        given(
            accommodationService.findAllActiveSummaryByIds(secondBatch)
        ).willReturn(List.of(accommodation(19L)));

        // when
        List<PopularAccommodationResponseDto> result =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then
        assertThat(result)
            .extracting(PopularAccommodationResponseDto::accommodationId)
            .containsExactly(8L, 19L);
        assertThat(result)
            .extracting(PopularAccommodationResponseDto::rank)
            .containsExactly(1, 2);

        then(popularAccommodationRankingService)
            .should()
            .findRankedAccommodationIds(limit, 10L, 19L);
    }

    @Test
    @DisplayName("Redis 끝까지 조회해도 부족하면 존재하는 ACTIVE 숙소만 반환한다")
    void returnAvailableResultsAtEndOfRanking() {
        // given
        int limit = 2;
        List<Long> firstBatch =
            List.of(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);
        List<Long> lastBatch = List.of(20L);

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 9L)
        ).willReturn(firstBatch);
        given(
            accommodationService.findAllActiveSummaryByIds(firstBatch)
        ).willReturn(List.of());
        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 10L, 19L)
        ).willReturn(lastBatch);
        given(
            accommodationService.findAllActiveSummaryByIds(lastBatch)
        ).willReturn(List.of(accommodation(20L)));

        // when
        List<PopularAccommodationResponseDto> result =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then
        assertThat(result)
            .extracting(PopularAccommodationResponseDto::accommodationId)
            .containsExactly(20L);
        assertThat(result.get(0).rank()).isEqualTo(1);

        then(popularAccommodationRankingService)
            .should(never())
            .findRankedAccommodationIds(limit, 20L, 29L);
    }

    @Test
    @DisplayName("중복 후보는 다시 DB에서 조회하지 않고 Redis 순서를 유지한다")
    void doNotProcessDuplicateCandidatesAgain() {
        // given
        int limit = 2;
        List<Long> firstBatch =
            List.of(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);
        List<Long> secondBatch = List.of(10L, 11L);

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 9L)
        ).willReturn(firstBatch);
        given(
            accommodationService.findAllActiveSummaryByIds(firstBatch)
        ).willReturn(List.of(accommodation(9L)));
        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 10L, 19L)
        ).willReturn(secondBatch);
        given(
            accommodationService.findAllActiveSummaryByIds(List.of(11L))
        ).willReturn(List.of(accommodation(11L)));

        // when
        List<PopularAccommodationResponseDto> result =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then
        assertThat(result)
            .extracting(PopularAccommodationResponseDto::accommodationId)
            .containsExactly(9L, 11L);
        then(accommodationService)
            .should()
            .findAllActiveSummaryByIds(List.of(11L));
    }

    @Test
    @DisplayName("Redis 인기 숙소 랭킹이 비어 있으면 빈 목록을 반환한다")
    void returnEmptyListWhenRankingIsEmpty() {
        // given
        int limit = 10;

        given(
            popularAccommodationRankingService
                .findRankedAccommodationIds(limit, 0L, 49L)
        ).willReturn(List.of());

        // when
        List<PopularAccommodationResponseDto> result =
            popularAccommodationQueryService
                .getPopularAccommodations(limit);

        // then
        assertThat(result).isEmpty();
        then(accommodationService).shouldHaveNoInteractions();
    }

    private AccommodationListResponseDto accommodation(Long id) {
        return new AccommodationListResponseDto(
            id,
            "룸픽 호텔 " + id,
            "서울특별시"
        );
    }
}
