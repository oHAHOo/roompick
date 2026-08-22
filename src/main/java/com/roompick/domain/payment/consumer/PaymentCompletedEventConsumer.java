package com.roompick.domain.payment.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.roompick.domain.payment.event.PaymentCompletedEvent;
import com.roompick.domain.payment.service.PaymentNotificationService;
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
public class PaymentCompletedEventConsumer {

    private final PaymentNotificationService paymentNotificationService;

    @KafkaListener(
        topics = KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
        groupId = "payment-completed-notification-consumer",
        concurrency = "1"
    )
    public void consume(ConsumerRecord<String, PaymentCompletedEvent> record) {
        PaymentCompletedEvent event = record.value();

        log.info(
            "결제 완료 알림 처리 시작. partition={}, offset={}, paymentId={}",
            record.partition(),
            record.offset(),
            event.paymentId()
        );

        try {
            paymentNotificationService.sendCompletionEmail(event.paymentId());
        } catch (Exception exception) {
            log.error("결제 완료 이메일 발송 실패. paymentId={}", event.paymentId(), exception);
        }
    }
}
