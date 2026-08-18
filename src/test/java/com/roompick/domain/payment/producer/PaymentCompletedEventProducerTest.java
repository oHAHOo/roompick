package com.roompick.domain.payment.producer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.roompick.domain.payment.event.PaymentCompletedEvent;
import com.roompick.global.config.kafka.KafkaTopicConfig;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedEventProducerTest {

    private static final Long PAYMENT_ID = 1L;

    @Mock
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    private PaymentCompletedEventProducer paymentCompletedEventProducer;

    @Test
    @DisplayName("결제 완료 이벤트를 결제 ID를 키로 하여 결제 완료 토픽에 발행한다")
    void sendPublishesEventToPaymentCompletedTopic() {
        // given
        paymentCompletedEventProducer =
            new PaymentCompletedEventProducer(kafkaTemplate);

        PaymentCompletedEvent event =
            new PaymentCompletedEvent(
                PAYMENT_ID,
                LocalDateTime.now()
            );

        SendResult<String, PaymentCompletedEvent> sendResult =
            new SendResult<>(
                new ProducerRecord<>(
                    KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                    event.paymentId().toString(),
                    event
                ),
                new RecordMetadata(
                    new TopicPartition(
                        KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                        0
                    ),
                    0L, 0, 0L, 0, 0
                )
            );

        given(
            kafkaTemplate.send(
                eq(KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC),
                eq(event.paymentId().toString()),
                eq(event)
            )
        ).willReturn(
            CompletableFuture.completedFuture(sendResult)
        );

        // when
        paymentCompletedEventProducer.send(event);

        // then
        then(kafkaTemplate)
            .should()
            .send(
                KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                event.paymentId().toString(),
                event
            );
    }

    @Test
    @DisplayName("발행이 실패해도 예외를 던지지 않고 삼킨다")
    void sendDoesNotThrowWhenPublishFails() {
        /*
         * 이메일 발송(알림)은 결제·예약 확정에 영향을 주면 안 되므로,
         * OfferOccupyEventProducer와 달리 발행 실패 시에도
         * BusinessException을 던지지 않고 로그만 남긴다.
         */

        // given
        paymentCompletedEventProducer =
            new PaymentCompletedEventProducer(kafkaTemplate);

        PaymentCompletedEvent event =
            new PaymentCompletedEvent(
                PAYMENT_ID,
                LocalDateTime.now()
            );

        CompletableFuture<SendResult<String, PaymentCompletedEvent>>
            failedFuture = CompletableFuture.failedFuture(
                new RuntimeException("브로커 연결 실패를 흉내낸 예외입니다.")
            );

        given(
            kafkaTemplate.send(
                any(String.class),
                any(String.class),
                any(PaymentCompletedEvent.class)
            )
        ).willReturn(failedFuture);

        // when & then
        org.assertj.core.api.Assertions
            .assertThatCode(() ->
                paymentCompletedEventProducer.send(event)
            )
            .doesNotThrowAnyException();
    }
}
