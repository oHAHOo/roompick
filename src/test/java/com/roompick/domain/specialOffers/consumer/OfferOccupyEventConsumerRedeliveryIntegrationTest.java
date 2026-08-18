package com.roompick.domain.specialOffers.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;
import com.roompick.domain.specialOffers.event.OfferOccupyRequestEvent;
import com.roompick.domain.specialOffers.repository.SpecialOfferRepository;
import com.roompick.domain.waitlist.facade.WaitlistProcessingFacade;
import com.roompick.domain.waitlist.repository.WaitlistRepository;
import com.roompick.global.config.kafka.KafkaTopicConfig;

/**
 * 컨슈머가 메시지 처리 도중 실패한 뒤 Kafka가 같은 메시지를 다시
 * 전달했을 때, 재처리 멱등성이 실제 Kafka 재전달 경로에서도
 * 유지되는지 검증합니다.
 *
 * WaitlistProcessingFacade.occupy()를 스파이해서 첫 호출에서만 예외를
 * 던지게 만들어 리스너 처리 실패를 재현합니다. Spring Kafka의 기본
 * 에러 핸들러(DefaultErrorHandler)가 같은 레코드를 오프셋 커밋 없이
 * 재시도하므로, 컨슈머가 죽었다가 재시작해 같은 메시지를 다시 받는
 * 상황과 동일한 코드 경로(WaitlistProcessingFacade.occupy() 재호출)를 탄다.
 */
@Tag("integration")
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = { KafkaTopicConfig.OFFER_OCCUPY_REQUEST_TOPIC },
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
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.roompick.domain.specialOffers.event"
    }
)
@ActiveProfiles("test")
class OfferOccupyEventConsumerRedeliveryIntegrationTest {

    private static final int MYSQL_PORT = 3306;
    private static final String DATABASE_NAME = "roompick_consumer_redelivery_test";
    private static final String DATABASE_USERNAME = "roompick";
    private static final String DATABASE_PASSWORD = "roompick-password";
    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

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
    private KafkaTemplate<String, OfferOccupyRequestEvent> kafkaTemplate;
    @Autowired
    private WaitlistRepository waitlistRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SpecialOfferRepository specialOfferRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AccommodationRepository accommodationRepository;
    @Autowired
    private RoomRepository roomRepository;

    @MockitoSpyBean
    private WaitlistProcessingFacade waitlistProcessingFacade;

    private TestData testData;

    @BeforeEach
    void setUp() {
        testData = createTestData();
    }

    @AfterEach
    void tearDown() {
        waitlistRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        specialOfferRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("컨슈머가 첫 처리에 실패해 메시지가 재전달돼도 중복 예약이 생기지 않는다")
    void redeliveredMessageAfterProcessingFailureDoesNotDuplicate() throws Exception {
        // given
        AtomicInteger callCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                throw new RuntimeException("첫 처리 시도 실패를 흉내낸 예외입니다.");
            }
            return invocation.callRealMethod();
        }).when(waitlistProcessingFacade).occupy(
            eq(testData.offerId()), eq(testData.memberId()), any()
        );

        OfferOccupyRequestEvent event = new OfferOccupyRequestEvent(
            testData.offerId(), testData.memberId(), LocalDateTime.now(TEST_ZONE_ID)
        );

        // when
        kafkaTemplate.send(
            KafkaTopicConfig.OFFER_OCCUPY_REQUEST_TOPIC,
            testData.offerId().toString(),
            event
        );

        awaitWaitlistRowCreated();

        // then
        assertThat(waitlistRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(1L);

        verify(waitlistProcessingFacade, times(2))
            .occupy(eq(testData.offerId()), eq(testData.memberId()), any());
    }

    private void awaitWaitlistRowCreated() throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT.toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (waitlistRepository.count() > 0) {
                return;
            }
            Thread.sleep(200);
        }

        throw new IllegalStateException(
            "재전달된 메시지가 처리되어 waitlist 행이 생성되기를 기다리는 중 시간이 초과됐습니다."
        );
    }

    private TestData createTestData() {
        Member member = memberRepository.saveAndFlush(
            Member.create("consumer-redelivery@roompick.com", "encoded-password", "재전달 테스트 회원")
        );

        Accommodation accommodation = Accommodation.create(
            "재전달 테스트 호텔", "서울특별시 테스트구 재전달로 1",
            "컨슈머 재전달 통합 테스트용 숙소",
            LocalTime.of(15, 0), LocalTime.of(11, 0)
        );
        ReflectionTestUtils.setField(accommodation, "status", AccommodationStatus.ACTIVE);
        Accommodation savedAccommodation = accommodationRepository.saveAndFlush(accommodation);

        Room room = Room.create(
            savedAccommodation, "401", "재전달 테스트 객실",
            "컨슈머 재전달 통합 테스트용 객실",
            300_000L, 2, 2
        );
        room.activate();
        Room savedRoom = roomRepository.saveAndFlush(room);

        LocalDateTime now = LocalDateTime.now(TEST_ZONE_ID);
        SpecialOffer specialOffer = SpecialOffer.create(
            savedRoom, 150_000L,
            now.minusMinutes(1), now.plusHours(1),
            now.toLocalDate().plusDays(20), now.toLocalDate().plusDays(22)
        );
        ReflectionTestUtils.setField(specialOffer, "status", SpecialOfferStatus.ACTIVE);
        SpecialOffer savedOffer = specialOfferRepository.saveAndFlush(specialOffer);

        return new TestData(savedOffer.getId(), member.getId());
    }

    private record TestData(Long offerId, Long memberId) {
    }
}
