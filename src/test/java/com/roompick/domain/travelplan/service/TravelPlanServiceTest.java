package com.roompick.domain.travelplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.service.AccommodationLocationSearchService;
import com.roompick.domain.travelplan.client.TravelPlanLlmClient;
import com.roompick.domain.travelplan.dto.RecommendedAccommodationDto;
import com.roompick.domain.travelplan.dto.TravelPlanRequestDto;
import com.roompick.domain.travelplan.dto.TravelPlanResponseDto;
import com.roompick.domain.travelplan.model.TravelPlanLlmRequest;
import com.roompick.domain.travelplan.model.TravelPlanLlmResult;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 여행 계획 생성 Service의 검증·매핑·저장 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanServiceTest {

    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    @Mock
    private AccommodationLocationSearchService accommodationLocationSearchService;

    @Mock
    private TravelPlanLlmClient travelPlanLlmClient;

    @Mock
    private AiUsageLogService aiUsageLogService;

    @InjectMocks
    private TravelPlanService travelPlanService;

    @Captor
    private ArgumentCaptor<TravelPlanLlmRequest> llmRequestCaptor;

    @Test
    @DisplayName("LLM이 선택한 후보를 실제 숙소 조회 결과와 매핑해 반환한다")
    void mapSelectedCandidatesToRealAccommodations() {
        // given
        AccommodationLocationSearchResponseDto nearby =
            new AccommodationLocationSearchResponseDto(
                10L,
                "룸픽 호텔",
                "서울특별시",
                LATITUDE,
                LONGITUDE,
                1.2,
                "https://cdn.example.com/a.jpg"
            );

        given(
            accommodationLocationSearchService.searchNearby(
                eq(null),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt()
            )
        ).willReturn(List.of(nearby));

        given(travelPlanLlmClient.generate(any()))
            .willReturn(
                new TravelPlanLlmResult(
                    List.of(
                        new TravelPlanLlmResult.ItineraryDay(
                            1,
                            "첫째 날",
                            List.of("경복궁 관람")
                        )
                    ),
                    List.of(
                        new TravelPlanLlmResult.CandidateSelection(
                            0,
                            "일정 중심지와 가깝습니다."
                        )
                    ),
                    new TravelPlanLlmResult.TokenUsage(
                        1200,
                        800
                    )
                )
            );

        given(travelPlanLlmClient.modelName())
            .willReturn("claude-opus-5");

        // when
        TravelPlanResponseDto response =
            travelPlanService.generatePlan(request());

        // then
        assertThat(response.itinerary()).hasSize(1);
        assertThat(response.itinerary().get(0).activities())
            .containsExactly("경복궁 관람");

        assertThat(response.recommendedAccommodations()).hasSize(1);

        RecommendedAccommodationDto recommended =
            response.recommendedAccommodations().get(0);

        assertThat(recommended.accommodationId()).isEqualTo(10L);
        assertThat(recommended.name()).isEqualTo("룸픽 호텔");
        assertThat(recommended.address()).isEqualTo("서울특별시");
        assertThat(recommended.distanceKm()).isEqualTo(1.2);
        assertThat(recommended.reason())
            .isEqualTo("일정 중심지와 가깝습니다.");
    }

    @Test
    @DisplayName("토큰 사용량과 요청 규모를 감사 로그로 저장한다")
    void saveTokenUsageToAuditLog() {
        // given
        given(
            accommodationLocationSearchService.searchNearby(
                eq(null),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt()
            )
        ).willReturn(
            List.of(
                nearbyAccommodation(10L),
                nearbyAccommodation(11L)
            )
        );

        given(travelPlanLlmClient.generate(any()))
            .willReturn(
                new TravelPlanLlmResult(
                    List.of(
                        new TravelPlanLlmResult.ItineraryDay(
                            1,
                            "첫째 날",
                            List.of("경복궁 관람")
                        )
                    ),
                    List.of(
                        new TravelPlanLlmResult.CandidateSelection(
                            1,
                            "일정과 가깝습니다."
                        )
                    ),
                    new TravelPlanLlmResult.TokenUsage(
                        1234,
                        567
                    )
                )
            );

        given(travelPlanLlmClient.modelName())
            .willReturn("claude-opus-5");

        // when
        travelPlanService.generatePlan(request());

        // then
        then(aiUsageLogService)
            .should()
            .save(
                eq("claude-opus-5"),
                eq(1234),
                eq(567),
                anyLong(),
                eq(2),
                eq(2),
                eq(2),
                eq(1)
            );
    }

    @Test
    @DisplayName("반경 내 숙소가 없으면 빈 후보 목록으로 일정만 생성한다")
    void generateItineraryOnlyWhenNoAccommodationNearby() {
        // given
        given(
            accommodationLocationSearchService.searchNearby(
                eq(null),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt()
            )
        ).willReturn(List.of());

        given(travelPlanLlmClient.generate(any()))
            .willReturn(
                new TravelPlanLlmResult(
                    List.of(
                        new TravelPlanLlmResult.ItineraryDay(
                            1,
                            "첫째 날",
                            List.of("해변 산책")
                        )
                    ),
                    List.of(),
                    new TravelPlanLlmResult.TokenUsage(
                        900,
                        400
                    )
                )
            );

        given(travelPlanLlmClient.modelName())
            .willReturn("claude-opus-5");

        // when
        TravelPlanResponseDto response =
            travelPlanService.generatePlan(request());

        // then
        assertThat(response.recommendedAccommodations()).isEmpty();
        assertThat(response.itinerary()).hasSize(1);

        then(travelPlanLlmClient)
            .should()
            .generate(llmRequestCaptor.capture());

        assertThat(llmRequestCaptor.getValue().candidates()).isEmpty();

        /*
         * 후보가 없어도 LLM 호출은 발생했으므로 비용은 기록되어야 한다.
         * candidate_count와 recommended_count는 0으로 남는다.
         */
        then(aiUsageLogService)
            .should()
            .save(
                anyString(),
                eq(900),
                eq(400),
                anyLong(),
                anyInt(),
                anyInt(),
                eq(0),
                eq(0)
            );
    }

    @Test
    @DisplayName("체크아웃 날짜가 체크인 날짜보다 이후가 아니면 LLM을 호출하지 않는다")
    void rejectInvalidStayPeriodBeforeCallingLlm() {
        // given
        LocalDate checkInDate = LocalDate.now().plusDays(1);

        TravelPlanRequestDto invalidRequest =
            new TravelPlanRequestDto(
                LATITUDE,
                LONGITUDE,
                checkInDate,
                checkInDate,
                2
            );

        // when & then
        assertThatThrownBy(
            () -> travelPlanService.generatePlan(invalidRequest)
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        then(travelPlanLlmClient)
            .should(never())
            .generate(any());

        then(accommodationLocationSearchService)
            .should(never())
            .searchNearby(
                any(),
                anyDouble(),
                anyDouble(),
                anyDouble(),
                anyInt()
            );
    }

    @Test
    @DisplayName("과거 체크인 날짜 요청은 LLM을 호출하지 않고 거절한다")
    void rejectPastCheckInDate() {
        // given
        TravelPlanRequestDto invalidRequest =
            new TravelPlanRequestDto(
                LATITUDE,
                LONGITUDE,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                2
            );

        // when & then
        assertThatThrownBy(
            () -> travelPlanService.generatePlan(invalidRequest)
        )
            .isInstanceOf(BusinessException.class);

        then(travelPlanLlmClient)
            .should(never())
            .generate(any());
    }

    @Test
    @DisplayName("허용 범위를 벗어난 위도 요청은 LLM을 호출하지 않고 거절한다")
    void rejectLatitudeOutOfRange() {
        // given
        TravelPlanRequestDto invalidRequest =
            new TravelPlanRequestDto(
                91.0,
                LONGITUDE,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                2
            );

        // when & then
        assertThatThrownBy(
            () -> travelPlanService.generatePlan(invalidRequest)
        )
            .isInstanceOf(BusinessException.class);

        then(travelPlanLlmClient)
            .should(never())
            .generate(any());
    }

    private AccommodationLocationSearchResponseDto nearbyAccommodation(
        Long accommodationId
    ) {
        return new AccommodationLocationSearchResponseDto(
            accommodationId,
            "룸픽 호텔 " + accommodationId,
            "서울특별시",
            LATITUDE,
            LONGITUDE,
            1.2,
            null
        );
    }

    private TravelPlanRequestDto request() {
        return new TravelPlanRequestDto(
            LATITUDE,
            LONGITUDE,
            LocalDate.now().plusDays(1),
            LocalDate.now().plusDays(3),
            2
        );
    }
}
