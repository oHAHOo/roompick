package com.roompick.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로그래밍 방식으로 트랜잭션 범위를 제어하기 위한 설정입니다.
 */
@Configuration
public class TransactionTemplateConfig {

    @Bean
    public TransactionTemplate transactionTemplate(
        PlatformTransactionManager transactionManager
    ) {
        return new TransactionTemplate(
            transactionManager
        );
    }
}
