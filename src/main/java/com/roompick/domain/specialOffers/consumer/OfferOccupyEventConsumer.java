package com.roompick.domain.specialOffers.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.domain.waitlist.service.WaitlistService;
import com.roompick.global.config.kafka.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfferOccupyEventConsumer {

    private final WaitlistService waitlistService;

    @KafkaListener(
        topics = KafkaTopicConfig.OFFER_OCCUPY_REQUEST_TOPIC,
        groupId = "special-offer-occupy-consumer",
        concurrency = "3"
    )
    public void consume(OfferOccupyRequestEvent event) {
        log.info(
            "점유 요청 처리 시작. offerId={}, memberId={}",
            event.offerId(),
            event.memberId()
        );

        waitlistService.occupy(
            event.offerId(),
            event.memberId(),
            event.requestedAt()
        );
    }
}
