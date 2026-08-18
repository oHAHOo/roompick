package com.roompick.domain.waitlist.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import com.roompick.domain.specialOffers.repository.SpecialOfferRepository;
import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;
import com.roompick.domain.waitlist.facade.WaitlistProcessingFacade;
import com.roompick.domain.waitlist.repository.WaitlistRepository;

/**
 * 동일 특가 상품에 대한 점유 요청이 도착 순서대로
 * 처리되는지 실제 MySQL 환경에서 검증합니다.
 *
 * Kafka 파티션 순서 보장 자체는 라이브러리 책임이므로,
 * 여기서는 WaitlistProcessingFacade.occupy()가 호출된 순서대로
 * 첫 요청만 HOLD가 되고 이후 요청은 WAIT으로 쌓이는지
 * 애플리케이션 로직만 검증합니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false"
    }
)
@ActiveProfiles("test")
class WaitlistOccupyOrderMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;
    private static final String DATABASE_NAME = "roompick_waitlist_order_test";
    private static final String DATABASE_USERNAME = "roompick";
    private static final String DATABASE_PASSWORD = "roompick-password";
    @Container
    static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(DATABASE_USERNAME)
            .withPassword(DATABASE_PASSWORD);
    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Asia/Seoul");
    @Autowired
    private WaitlistProcessingFacade waitlistProcessingFacade;
    @Autowired
    private WaitlistRepository waitlistRepository;
    @Autowired
    private SpecialOfferRepository specialOfferRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private AccommodationRepository accommodationRepository;
    @Autowired
    private RoomRepository roomRepository;
    private TestData testData;

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
    @DisplayName("먼저 도착한 점유 요청만 HOLD가 되고 이후 요청은 WAIT으로 쌓인다")
    void firstArrivedRequestBecomesHoldAndRestBecomeWait() {
        // given
        LocalDateTime baseTime = LocalDateTime.of(2026, 1, 1, 10, 0);

        // when — 파티션에 적재된 순서를 그대로 흉내내어 순차 호출
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), baseTime);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.secondMemberId(), baseTime.plusSeconds(1));
        waitlistProcessingFacade.occupy(testData.offerId(), testData.thirdMemberId(), baseTime.plusSeconds(2));

        // then
        Waitlist firstWaitlist = findWaitlist(testData.firstMemberId());
        Waitlist secondWaitlist = findWaitlist(testData.secondMemberId());
        Waitlist thirdWaitlist = findWaitlist(testData.thirdMemberId());

        assertThat(firstWaitlist.getStatus()).isEqualTo(WaitlistStatus.HOLD);
        assertThat(secondWaitlist.getStatus()).isEqualTo(WaitlistStatus.WAIT);
        assertThat(thirdWaitlist.getStatus()).isEqualTo(WaitlistStatus.WAIT);

        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("HOLD로 생성되는 예약은 객실 가격이 아니라 특가 가격을 적용한다")
    void holdReservationUsesSpecialOfferPriceNotRoomPrice() {
        // given — createTestData()가 만드는 객실 가격은 300,000원, 특가 가격은 150,000원
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);

        // when
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), requestedAt);

        // then
        assertThat(reservationRepository.findAll())
            .hasSize(1)
            .first()
            .extracting(reservation -> reservation.getPricePerNight())
            .isEqualTo(150_000L);
    }

    @Test
    @DisplayName("같은 회원의 재처리 요청은 무시되고 중복 예약이 생성되지 않는다")
    void retriedRequestFromSameMemberIsIgnored() {
        // given
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);

        // when — 같은 이벤트가 두 번 처리된 상황을 흉내냄
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), requestedAt);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), requestedAt);

        // then
        assertThat(waitlistRepository.count()).isEqualTo(1L);
        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    private Waitlist findWaitlist(Long memberId) {
        return waitlistRepository
            .findBySpecialOfferIdAndMemberId(testData.offerId(), memberId)
            .orElseThrow();
    }

    private TestData createTestData() {
        Member firstMember = memberRepository.saveAndFlush(
            Member.create("waitlist-order-a@roompick.com", "encoded-password", "대기열 순서 테스트 A")
        );
        Member secondMember = memberRepository.saveAndFlush(
            Member.create("waitlist-order-b@roompick.com", "encoded-password", "대기열 순서 테스트 B")
        );
        Member thirdMember = memberRepository.saveAndFlush(
            Member.create("waitlist-order-c@roompick.com", "encoded-password", "대기열 순서 테스트 C")
        );

        Accommodation accommodation = Accommodation.create(
            "대기열 순서 테스트 호텔", "서울특별시 테스트구 대기열로 1",
            "특가 대기열 순서 통합 테스트용 숙소",
            LocalTime.of(15, 0), LocalTime.of(11, 0)
        );
        ReflectionTestUtils.setField(accommodation, "status", AccommodationStatus.ACTIVE);
        Accommodation savedAccommodation = accommodationRepository.saveAndFlush(accommodation);

        Room room = Room.create(
            savedAccommodation, "201", "대기열 순서 테스트 객실",
            "특가 대기열 순서 통합 테스트용 객실",
            300_000L, 2, 2
        );
        room.activate();
        Room savedRoom = roomRepository.saveAndFlush(room);

        LocalDateTime now = LocalDateTime.now(TEST_ZONE_ID);
        SpecialOffer specialOffer = SpecialOffer.create(
            savedRoom, 150_000L,
            now.minusMinutes(1), now.plusHours(1),
            now.toLocalDate().plusDays(10), now.toLocalDate().plusDays(12)
        );
        ReflectionTestUtils.setField(
            specialOffer, "status",
            com.roompick.domain.specialOffers.entity.SpecialOfferStatus.ACTIVE
        );
        SpecialOffer savedOffer = specialOfferRepository.saveAndFlush(specialOffer);

        return new TestData(
            savedOffer.getId(),
            firstMember.getId(), secondMember.getId(), thirdMember.getId()
        );
    }

    private record TestData(
        Long offerId,
        Long firstMemberId, Long secondMemberId, Long thirdMemberId
    ) {
    }
}
