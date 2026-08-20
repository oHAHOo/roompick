package com.roompick.domain.specialOffers.scheduler;

import static com.roompick.global.config.SchedulerConfig.SPECIAL_OFFER_TASK_SCHEDULER;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.service.SpecialOfferService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecialOfferScheduler {

    private final SpecialOfferService specialOfferService;

    @Scheduled(
        fixedDelayString =
            "${specialoffer.scheduler.fixed-delay:30000}",
        initialDelayString =
            "${specialoffer.scheduler.initial-delay:30000}",
        scheduler = SPECIAL_OFFER_TASK_SCHEDULER
    )
    public void updateStatueses() {
        int endedCount = endDueOffersSafely();
        int activatedCount = actuvateDueOffersSafely();

        log.info(
            "특가 상태 변경 완료. activated={}, ended={}",
            activatedCount,
            endedCount
        );
    }

    private int actuvateDueOffersSafely() {
        try {
            return specialOfferService.activateDueOffers();
        } catch (Exception exception) {
            log.error("특가 활성 상태 변경 실패", exception);
            return 0;
        }
    }

    private int endDueOffersSafely() {
        try {
            return specialOfferService.endDueOffers();
        } catch (Exception exception) {
            log.error("특가 종료 상태 변경 실패", exception);
            return 0;
        }
    }
}
