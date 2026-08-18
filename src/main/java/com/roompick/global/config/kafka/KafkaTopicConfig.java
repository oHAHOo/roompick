package com.roompick.global.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String OFFER_OCCUPY_REQUEST_TOPIC = "offer-occupy-request";

    @Bean
    public NewTopic offerOccupyRequestTopic() {
        return TopicBuilder.name(OFFER_OCCUPY_REQUEST_TOPIC).partitions(3).replicas(1).build();
    }

}
