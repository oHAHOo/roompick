package com.roompick.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SchedulerConfig {

    public static final String TIME_SALE_TASK_SCHEDULER =
        "timeSaleTaskScheduler";

    public static final String SPECIAL_OFFER_TASK_SCHEDULER =
        "specialOfferTaskScheduler";

    public static final String WAITLIST_TASK_SCHEDULER =
        "waitlistTaskScheduler";

    private static final int POOL_SIZE = 1;
    private static final int AWAIT_TERMINATION_SECONDS = 10;

    @Bean(name = TIME_SALE_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler timeSaleTaskScheduler() {
        return createTaskScheduler(
            "timesale-scheduler-"
        );
    }

    @Bean(name = SPECIAL_OFFER_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler specialOfferTaskScheduler() {
        return createTaskScheduler(
            "special-offer-scheduler-"
        );
    }

    @Bean(name = WAITLIST_TASK_SCHEDULER)
    public ThreadPoolTaskScheduler waitlistTaskScheduler() {
        return createTaskScheduler(
            "waitlist-scheduler-"
        );
    }

    private ThreadPoolTaskScheduler createTaskScheduler(
        String threadNamePrefix
    ) {
        ThreadPoolTaskScheduler taskScheduler =
            new ThreadPoolTaskScheduler();

        taskScheduler.setPoolSize(POOL_SIZE);
        taskScheduler.setThreadNamePrefix(
            threadNamePrefix
        );
        taskScheduler.setWaitForTasksToCompleteOnShutdown(
            true
        );
        taskScheduler.setAwaitTerminationSeconds(
            AWAIT_TERMINATION_SECONDS
        );
        taskScheduler.setRemoveOnCancelPolicy(true);

        return taskScheduler;
    }
}
