package com.roompick.domain.travelplan.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.travelplan.dto.TravelPlanRequestDto;
import com.roompick.domain.travelplan.dto.TravelPlanResponseDto;
import com.roompick.domain.travelplan.service.TravelPlanService;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 여행 계획 Service를 연결하는 TravelPlanFacade의 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanFacadeTest {

    @Mock
    private TravelPlanService travelPlanService;

    @InjectMocks
    private TravelPlanFacade travelPlanFacade;

    @Test
    @DisplayName("여행 계획 생성 요청을 Service에 위임하고 결과를 반환한다")
    void delegateTravelPlanGenerationToService() {
        // given
        TravelPlanRequestDto request = request();

        TravelPlanResponseDto expected =
            new TravelPlanResponseDto(
                1L,
                request.checkInDate(),
                request.checkOutDate(),
                2,
                List.of(),
                List.of()
            );

        given(travelPlanService.generatePlan(request))
            .willReturn(expected);

        // when
        TravelPlanResponseDto actual =
            travelPlanFacade.generatePlan(request);

        // then
        assertThat(actual).isSameAs(expected);

        then(travelPlanService)
            .should()
            .generatePlan(request);
    }

    @Test
    @DisplayName("Service의 여행 계획 생성 예외를 그대로 전파한다")
    void propagateTravelPlanException() {
        // given
        TravelPlanRequestDto request = request();

        BusinessException exception =
            new BusinessException(
                ErrorCode.TRAVEL_PLAN_LLM_UNAVAILABLE
            );

        given(travelPlanService.generatePlan(request))
            .willThrow(exception);

        // when & then
        assertThatThrownBy(
            () -> travelPlanFacade.generatePlan(request)
        ).isSameAs(exception);
    }

    private TravelPlanRequestDto request() {
        return new TravelPlanRequestDto(
            37.5665,
            126.9780,
            LocalDate.now().plusDays(1),
            LocalDate.now().plusDays(3),
            2
        );
    }
}
