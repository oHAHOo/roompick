package com.roompick.global.config;

import static com.roompick.global.config.SchedulerConfig.SPECIAL_OFFER_TASK_SCHEDULER;
import static com.roompick.global.config.SchedulerConfig.TIME_SALE_TASK_SCHEDULER;
import static com.roompick.global.config.SchedulerConfig.WAITLIST_TASK_SCHEDULER;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.roompick.domain.specialOffers.scheduler.SpecialOfferScheduler;
import com.roompick.domain.timesale.scheduler.TimeSaleScheduler;
import com.roompick.domain.waitlist.scheduler.WaitlistExpirationScheduler;

class SchedulerConfigTest {

    private final SchedulerConfig schedulerConfig =
        new SchedulerConfig();

    @Test
    @DisplayName("스케줄링 작업마다 서로 다른 실행기를 구성한다")
    void createsIndependentTaskSchedulers() {
        ThreadPoolTaskScheduler timeSaleTaskScheduler =
            schedulerConfig.timeSaleTaskScheduler();

        ThreadPoolTaskScheduler specialOfferTaskScheduler =
            schedulerConfig.specialOfferTaskScheduler();

        ThreadPoolTaskScheduler waitlistTaskScheduler =
            schedulerConfig.waitlistTaskScheduler();

        assertThat(timeSaleTaskScheduler)
            .isNotSameAs(specialOfferTaskScheduler)
            .isNotSameAs(waitlistTaskScheduler);

        assertThat(specialOfferTaskScheduler)
            .isNotSameAs(waitlistTaskScheduler);
    }

    @Test
    @DisplayName("각 실행기의 Pool Size는 1이다")
    void configuresPoolSizeAsOne() {
        assertThat(
            schedulerConfig
                .timeSaleTaskScheduler()
                .getPoolSize()
        ).isEqualTo(1);

        assertThat(
            schedulerConfig
                .specialOfferTaskScheduler()
                .getPoolSize()
        ).isEqualTo(1);

        assertThat(
            schedulerConfig
                .waitlistTaskScheduler()
                .getPoolSize()
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("실행기마다 구분되는 스레드 이름을 설정한다")
    void configuresIndependentThreadNamePrefixes() {
        assertThat(
            schedulerConfig
                .timeSaleTaskScheduler()
                .getThreadNamePrefix()
        ).isEqualTo("timesale-scheduler-");

        assertThat(
            schedulerConfig
                .specialOfferTaskScheduler()
                .getThreadNamePrefix()
        ).isEqualTo("special-offer-scheduler-");

        assertThat(
            schedulerConfig
                .waitlistTaskScheduler()
                .getThreadNamePrefix()
        ).isEqualTo("waitlist-scheduler-");
    }

    @Test
    @DisplayName("각 스케줄링 작업은 지정된 실행기를 사용한다")
    void scheduledMethodsUseDedicatedTaskSchedulers()
        throws NoSuchMethodException {

        assertSchedulerName(
            TimeSaleScheduler.class,
            "updateStatuses",
            TIME_SALE_TASK_SCHEDULER
        );

        assertSchedulerName(
            SpecialOfferScheduler.class,
            "updateStatueses",
            SPECIAL_OFFER_TASK_SCHEDULER
        );

        assertSchedulerName(
            WaitlistExpirationScheduler.class,
            "expireAndPromote",
            WAITLIST_TASK_SCHEDULER
        );
    }

    private void assertSchedulerName(
        Class<?> schedulerClass,
        String methodName,
        String expectedSchedulerName
    ) throws NoSuchMethodException {
        Method scheduledMethod =
            schedulerClass.getDeclaredMethod(methodName);

        Scheduled scheduled =
            scheduledMethod.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.scheduler())
            .isEqualTo(expectedSchedulerName);
    }
}
