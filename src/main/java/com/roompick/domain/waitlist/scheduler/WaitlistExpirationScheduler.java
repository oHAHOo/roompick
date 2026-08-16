package com.roompick.domain.waitlist.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roompick.domain.waitlist.service.WaitlistService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class WaitlistExpirationScheduler {

    private final WaitlistService waitlistService;
    private final Clock clock;

    @Scheduled(
        fixedDelayString = "${waitlist.scheduler.fixed-delay:10000}",
        initialDelayString = "${waitlist.scheduler.initial-delay:10000}"
    )
    public void expireAndPromote() {
        try {
            int expiredCount = waitlistService.expireAndPromote(
                LocalDateTime.now(clock)
            );

            log.info("대기열 만료 및 승계 처리 완료. expiredCount={}", expiredCount);
        } catch (Exception exception) {
            log.error("대기열 만료 및 승계 처리 실패", exception);
        }
    }
}
