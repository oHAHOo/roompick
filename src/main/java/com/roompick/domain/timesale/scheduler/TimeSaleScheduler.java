package com.roompick.domain.timesale.scheduler;

import static com.roompick.global.config.SchedulerConfig.TIME_SALE_TASK_SCHEDULER;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roompick.domain.timesale.service.TimeSaleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 타임세일의 시작·종료 상태를 주기적으로
 * 현재 시각에 맞게 변경합니다.
 *
 * 실제 할인 가격은 시작·종료 시각을 기준으로 계산하므로,
 * 스케줄러 실행이 다소 지연돼도 가격에는 영향을 주지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "roompick.scheduler",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class TimeSaleScheduler {

    private final TimeSaleService timeSaleService;

    /**
     * 종료 시각에 도달한 타임세일을 종료하고,
     * 시작 시각에 도달한 타임세일을 활성화합니다.
     *
     * 한쪽 상태 처리에서 예외가 발생해도
     * 다른 상태 처리는 독립적으로 실행합니다.
     */
    @Scheduled(
        fixedDelayString =
            "${timesale.scheduler.fixed-delay:30000}",
        initialDelayString =
            "${timesale.scheduler.initial-delay:30000}",
        scheduler = TIME_SALE_TASK_SCHEDULER
    )
    public void updateStatuses() {
        int endedCount =
            endDueSalesSafely();

        int activatedCount =
            activateDueSalesSafely();

        log.info(
            "타임세일 상태 변경 완료. "
                + "activated={}, ended={}",
            activatedCount,
            endedCount
        );
    }

    /**
     * 종료 대상 타임세일을 처리합니다.
     *
     * 종료 처리 실패가 활성화 처리까지 막지 않도록
     * 예외를 기록하고 이번 실행의 처리 건수를 0으로 반환합니다.
     */
    private int endDueSalesSafely() {
        try {
            return timeSaleService.endDueSales();
        } catch (Exception exception) {
            log.error(
                "타임세일 종료 상태 변경 실패",
                exception
            );

            return 0;
        }
    }

    /**
     * 활성화 대상 타임세일을 처리합니다.
     *
     * 활성화 처리 중 발생한 예외가 스케줄러 실행 스레드로
     * 전파되지 않도록 기록합니다.
     */
    private int activateDueSalesSafely() {
        try {
            return timeSaleService.activateDueSales();
        } catch (Exception exception) {
            log.error(
                "타임세일 활성 상태 변경 실패",
                exception
            );

            return 0;
        }
    }
}
