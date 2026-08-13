package com.roompick.domain.timesale.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import com.roompick.domain.timesale.service.TimeSaleService;

/**
 * 타임세일 상태 전환 스케줄러의
 * Service 호출 흐름과 예외 격리를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TimeSaleSchedulerTest {

    @Mock
    private TimeSaleService timeSaleService;

    @InjectMocks
    private TimeSaleScheduler timeSaleScheduler;

    @Test
    @DisplayName(
        "종료 대상 처리 후 활성화 대상 처리를 실행한다"
    )
    void 종료_처리_후_활성화_처리를_실행한다() {
        // given
        given(
            timeSaleService.endDueSales()
        ).willReturn(2);

        given(
            timeSaleService.activateDueSales()
        ).willReturn(3);

        // when
        timeSaleScheduler.updateStatuses();

        // then
        InOrder callOrder =
            inOrder(timeSaleService);

        callOrder.verify(timeSaleService)
            .endDueSales();

        callOrder.verify(timeSaleService)
            .activateDueSales();

        callOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName(
        "변경할 타임세일이 없어도 정상적으로 종료한다"
    )
    void 변경할_타임세일이_없어도_정상_종료한다() {
        // given
        given(
            timeSaleService.endDueSales()
        ).willReturn(0);

        given(
            timeSaleService.activateDueSales()
        ).willReturn(0);

        // when & then
        assertThatCode(() ->
            timeSaleScheduler.updateStatuses()
        ).doesNotThrowAnyException();

        InOrder callOrder =
            inOrder(timeSaleService);

        callOrder.verify(timeSaleService)
            .endDueSales();

        callOrder.verify(timeSaleService)
            .activateDueSales();

        callOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName(
        "종료 처리에 실패해도 활성화 처리를 계속 실행한다"
    )
    void 종료_처리에_실패해도_활성화_처리를_실행한다() {
        // given
        given(
            timeSaleService.endDueSales()
        ).willThrow(
            new IllegalStateException(
                "타임세일 종료 처리 실패"
            )
        );

        given(
            timeSaleService.activateDueSales()
        ).willReturn(1);

        // when & then
        assertThatCode(() ->
            timeSaleScheduler.updateStatuses()
        ).doesNotThrowAnyException();

        InOrder callOrder =
            inOrder(timeSaleService);

        callOrder.verify(timeSaleService)
            .endDueSales();

        callOrder.verify(timeSaleService)
            .activateDueSales();

        callOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName(
        "활성화 처리에 실패해도 예외를 외부로 전파하지 않는다"
    )
    void 활성화_처리에_실패해도_예외를_전파하지_않는다() {
        // given
        given(
            timeSaleService.endDueSales()
        ).willReturn(1);

        given(
            timeSaleService.activateDueSales()
        ).willThrow(
            new IllegalStateException(
                "타임세일 활성화 처리 실패"
            )
        );

        // when & then
        assertThatCode(() ->
            timeSaleScheduler.updateStatuses()
        ).doesNotThrowAnyException();

        InOrder callOrder =
            inOrder(timeSaleService);

        callOrder.verify(timeSaleService)
            .endDueSales();

        callOrder.verify(timeSaleService)
            .activateDueSales();

        callOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName(
        "타임세일 상태 갱신 주기는 설정값과 기본값을 사용한다"
    )
    void 스케줄_실행_설정을_검증한다()
        throws NoSuchMethodException {

        // given
        Method updateStatusesMethod =
            TimeSaleScheduler.class
                .getDeclaredMethod(
                    "updateStatuses"
                );

        // when
        Scheduled scheduled =
            updateStatusesMethod
                .getAnnotation(
                    Scheduled.class
                );

        // then
        assertThat(scheduled)
            .isNotNull();

        assertThat(
            scheduled.fixedDelayString()
        ).isEqualTo(
            "${timesale.scheduler.fixed-delay:30000}"
        );

        assertThat(
            scheduled.initialDelayString()
        ).isEqualTo(
            "${timesale.scheduler.initial-delay:30000}"
        );
    }
}
