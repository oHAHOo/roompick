package com.roompick.domain.specialOffers.producer;

import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.global.config.kafka.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OfferOccupyEventProducer {

    private final KafkaTemplate<String, OfferOccupyRequestEvent> kafkaTemplate;

    public void send(OfferOccupyRequestEvent event) {
        kafkaTemplate.send(
            KafkaTopicConfig.OFFER_OCCUPY_REQUEST_TOPIC,
            event.offerId().toString(),
            event
        );
    }
}
