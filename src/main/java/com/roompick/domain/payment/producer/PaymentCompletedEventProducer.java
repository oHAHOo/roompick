package com.roompick.domain.payment.producer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import com.roompick.domain.payment.event.PaymentCompletedEvent;
import com.roompick.global.config.kafka.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventProducer {

    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    public void send(PaymentCompletedEvent event) {
        try {
            SendResult<String, PaymentCompletedEvent> result;
            result = kafkaTemplate.send(
                KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                event.paymentId().toString(),
                event
            ).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info(
                "결제 완료 이벤트 발행 환료. partition={}, offset={}, paymentId={}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                event.paymentId()
            );
        } catch (TimeoutException | ExecutionException exception) {
            log.error("결제 완료 이벤트 발행 실패. paymentId={}", event.paymentId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("결제 완료 이벤트 발행 중 스레드가 중단됐습니다. paymentId={}", event.paymentId(), exception);
        }
    }
}
