package com.roompick.domain.specialOffers.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.domain.waitlist.facade.WaitlistProcessingFacade;
import com.roompick.global.config.kafka.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "roompick.kafka.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OfferOccupyEventConsumer {

    private final WaitlistProcessingFacade waitlistProcessingFacade;

    @KafkaListener(
        topics = KafkaTopicConfig.OFFER_OCCUPY_REQUEST_TOPIC,
        groupId = "special-offer-occupy-consumer",
        concurrency = "6"
    )
    public void consume(ConsumerRecord<String, OfferOccupyRequestEvent> record) {
        OfferOccupyRequestEvent event = record.value();

        log.info(
            "점유 요청 처리 시작. partition={}, offset={}, offerId={}, memberId={}",
            record.partition(),
            record.offset(),
            event.offerId(),
            event.memberId()
        );

        waitlistProcessingFacade.occupy(
            event.offerId(),
            event.memberId(),
            event.requestedAt()
        );
    }
}
