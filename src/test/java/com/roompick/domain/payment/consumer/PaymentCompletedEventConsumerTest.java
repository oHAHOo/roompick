package com.roompick.domain.payment.consumer;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.time.LocalDateTime;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.roompick.domain.payment.event.PaymentCompletedEvent;
import com.roompick.domain.payment.service.PaymentNotificationService;
import com.roompick.global.config.kafka.KafkaTopicConfig;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedEventConsumerTest {

    private static final Long PAYMENT_ID = 1L;

    @Mock
    private PaymentNotificationService paymentNotificationService;

    private PaymentCompletedEventConsumer paymentCompletedEventConsumer;

    @Test
    @DisplayName("이벤트를 소비하면 결제 ID로 완료 이메일 발송을 요청한다")
    void consumeRequestsCompletionEmail() {
        // given
        paymentCompletedEventConsumer =
            new PaymentCompletedEventConsumer(
                paymentNotificationService
            );

        PaymentCompletedEvent event =
            new PaymentCompletedEvent(
                PAYMENT_ID,
                LocalDateTime.now()
            );

        ConsumerRecord<String, PaymentCompletedEvent> record =
            new ConsumerRecord<>(
                KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                0,
                0L,
                PAYMENT_ID.toString(),
                event
            );

        // when
        paymentCompletedEventConsumer.consume(record);

        // then
        then(paymentNotificationService)
            .should()
            .sendCompletionEmail(PAYMENT_ID);
    }

    @Test
    @DisplayName("이메일 발송이 실패해도 예외를 밖으로 던지지 않는다")
    void consumeDoesNotPropagateExceptionWhenEmailSendingFails() {
        /*
         * 컨슈머 밖으로 예외가 새 나가면 Kafka의 기본 에러 핸들러가
         * 같은 메시지를 계속 재전달하며 무한 재시도에 빠질 수 있다.
         * 이메일 발송 실패가 결제·예약 확정에 영향을 주면 안 되므로
         * 여기서 완전히 흡수해야 한다.
         */

        // given
        paymentCompletedEventConsumer =
            new PaymentCompletedEventConsumer(
                paymentNotificationService
            );

        PaymentCompletedEvent event =
            new PaymentCompletedEvent(
                PAYMENT_ID,
                LocalDateTime.now()
            );

        ConsumerRecord<String, PaymentCompletedEvent> record =
            new ConsumerRecord<>(
                KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
                0,
                0L,
                PAYMENT_ID.toString(),
                event
            );

        willThrow(new RuntimeException("메일 서버 연결 실패를 흉내낸 예외입니다."))
            .given(paymentNotificationService)
            .sendCompletionEmail(PAYMENT_ID);

        // when & then
        org.assertj.core.api.Assertions
            .assertThatCode(() ->
                paymentCompletedEventConsumer.consume(record)
            )
            .doesNotThrowAnyException();
    }
}
