package com.roompick.global.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String OFFER_OCCUPY_REQUEST_TOPIC = "offer-occupy-request";
    public static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";

    @Bean
    public NewTopic offerOccupyRequestTopic() {
        return TopicBuilder.name(OFFER_OCCUPY_REQUEST_TOPIC).partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(PAYMENT_COMPLETED_TOPIC).partitions(1).replicas(1).build();
    }

}
