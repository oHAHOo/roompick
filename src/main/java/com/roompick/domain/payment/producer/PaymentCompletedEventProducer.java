package com.roompick.domain.payment.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.roompick.domain.payment.event.PaymentCompletedEvent;
import com.roompick.global.config.kafka.KafkaTopicConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventProducer {

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    /**
     * 이메일 발송은 결제 확정 흐름과 완전히 분리돼야 하므로,
     * Kafka ACK를 기다리지 않고 요청 스레드를 즉시 반환한다.
     * 발행 성공·실패 여부는 콜백에서 로그로만 남긴다.
     */
    public void send(PaymentCompletedEvent event) {
        try {
            kafkaTemplate.send(
                KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                event.paymentId().toString(),
                event
            ).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error(
                        "결제 완료 이벤트 발행 실패. paymentId={}",
                        event.paymentId(),
                        exception
                    );
                    return;
                }

                log.info(
                    "결제 완료 이벤트 발행 완료. partition={}, offset={}, paymentId={}",
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.paymentId()
                );
            });
        } catch (Exception exception) {
            log.error(
                "결제 완료 이벤트 발행 요청 실패. paymentId={}",
                event.paymentId(),
                exception
            );
        }
    }
}
