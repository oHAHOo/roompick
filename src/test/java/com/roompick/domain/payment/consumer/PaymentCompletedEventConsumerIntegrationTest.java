package com.roompick.domain.payment.consumer;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.roompick.domain.payment.event.PaymentCompletedEvent;
import com.roompick.domain.payment.service.PaymentNotificationService;
import com.roompick.global.config.kafka.KafkaTopicConfig;

/**
 * Producer가 실제 JsonSerializer로 발행한 메시지를 실제 JsonDeserializer가
 * 정상적으로 역직렬화해 Consumer까지 전달하는지 검증한다.
 *
 * spring.json.trusted.packages에 이벤트 패키지가 등록되지 않으면 이 테스트가
 * 실패한다 — Consumer 단위 테스트는 이미 역직렬화된 이벤트 객체를 직접 넘기기
 * 때문에 이런 설정 오류를 잡지 못한다.
 */
@Tag("integration")
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = { KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC },
    brokerProperties = { "listeners=PLAINTEXT://localhost:0", "port=0" }
)
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.roompick.domain.payment.event"
    }
)
@ActiveProfiles("test")
class PaymentCompletedEventConsumerIntegrationTest {

    private static final int MYSQL_PORT = 3306;
    private static final String DATABASE_NAME = "roompick_payment_event_consumer_test";
    private static final String DATABASE_USERNAME = "roompick";
    private static final String DATABASE_PASSWORD = "roompick-password";
    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final Long PAYMENT_ID = 999L;

    @Container
    static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(DATABASE_USERNAME)
            .withPassword(DATABASE_PASSWORD);

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:mysql://" + MYSQL_CONTAINER.getHost()
                + ":" + MYSQL_CONTAINER.getMappedPort(MYSQL_PORT)
                + "/" + DATABASE_NAME
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
        );
        registry.add("spring.datasource.username", () -> DATABASE_USERNAME);
        registry.add("spring.datasource.password", () -> DATABASE_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;

    /*
     * 역직렬화·리스너 라우팅 검증이 목적이므로, DB 조회와 메일 발송을
     * 담당하는 PaymentNotificationService는 mock으로 대체해
     * 실제 메일 서버·결제 데이터 없이도 이 경로만 검증한다.
     */
    @MockitoBean
    private PaymentNotificationService paymentNotificationService;

    @Test
    @DisplayName("실제 JsonSerializer로 발행한 이벤트를 실제 JsonDeserializer가 역직렬화해 Consumer가 처리한다")
    void consumesRealSerializedEvent() {
        // given
        PaymentCompletedEvent event =
            new PaymentCompletedEvent(
                PAYMENT_ID,
                LocalDateTime.now(TEST_ZONE_ID)
            );

        // when
        kafkaTemplate.send(
            KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC,
            event.paymentId().toString(),
            event
        );

        // then
        verify(
            paymentNotificationService,
            timeout(AWAIT_TIMEOUT.toMillis())
        ).sendCompletionEmail(PAYMENT_ID);
    }
}
