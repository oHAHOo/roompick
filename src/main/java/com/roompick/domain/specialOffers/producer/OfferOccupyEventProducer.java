package com.roompick.domain.specialOffers.producer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.kafka.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfferOccupyEventProducer {

    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, OfferOccupyRequestEvent> kafkaTemplate;

    public void send(OfferOccupyRequestEvent event) {
        try{
            SendResult<String, OfferOccupyRequestEvent> result;
            result = kafkaTemplate.send(
                KafkaTopicConfig.OFFER_OCCUPY_REQUEST_TOPIC,
                event.offerId().toString(),
                event
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info(
                "점유 요청 이벤트 발행 완료. partition={}, offset={}, offerId={}, memberId={}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                event.offerId(),
                event.memberId()
                );
        } catch (TimeoutException exception) {
            log.error(
                "점유 요청 이벤트 발행이 시간 내 완료되지 않았습니다. offerId={}, memberId={}",
                event.offerId(),
                event.memberId(),
                exception
            );
            throw new BusinessException(ErrorCode.OFFER_OCCUPY_PUBLISH_TIMEOUT);
        } catch (ExecutionException exception) {
            log.error(
                "점유 요청 이벤트 발행에 실패했습니다. offerId={}, memberId={}",
                event.offerId(),
                event.memberId(),
                exception
            );
            throw new BusinessException(ErrorCode.OFFER_OCCUPY_PUBLISH_FAILED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error(
                "점유 요청 이벤트 발행 중 스레드가 중단됐습니다. offerId={}, memberId={}",
                event.offerId(),
                event.memberId(),
                exception
            );
            throw new BusinessException(ErrorCode.OFFER_OCCUPY_PUBLISH_FAILED);
        }
    }
}
