package com.roompick.domain.waitlist.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
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
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;
import com.roompick.domain.specialOffers.repository.SpecialOfferRepository;
import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;
import com.roompick.domain.waitlist.facade.WaitlistProcessingFacade;
import com.roompick.domain.waitlist.repository.WaitlistRepository;

/**
 * HOLD 상태의 점유가 TTL 내 결제되지 않았을 때
 * 만료 감지와 다음 대기자 승계가 실제 MySQL 환경에서
 * 올바르게 동작하는지 검증합니다.
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
class WaitlistExpirationAndPromotionMySqlIntegrationTest {

    private static final int MYSQL_PORT = 3306;
    private static final String DATABASE_NAME = "roompick_waitlist_expiration_test";
    private static final String DATABASE_USERNAME = "roompick";
    private static final String DATABASE_PASSWORD = "roompick-password";
    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Asia/Seoul");

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
    @Autowired
    private Clock clock;

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
    @DisplayName("TTL이 지난 HOLD는 만료되고 가장 먼저 대기한 다음 순번이 HOLD로 승격된다")
    void expiredHoldPromotesEarliestWaitingMember() {
        // given
        LocalDateTime firstRequestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime secondRequestedAt = firstRequestedAt.plusSeconds(1);
        LocalDateTime thirdRequestedAt = firstRequestedAt.plusSeconds(2);

        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), firstRequestedAt);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.secondMemberId(), secondRequestedAt);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.thirdMemberId(), thirdRequestedAt);

        /*
         * holdExpiresAt은 requestedAt이 아니라 occupy() 호출 시점의
         * 실제 Clock 기준으로 계산되므로, 만료 판정도 같은 Clock
         * 기준 "실제 현재 시각"을 기준으로 확인해야 한다.
         */
        LocalDateTime afterHoldExpires = LocalDateTime.now(clock).plusMinutes(6);

        // when
        int expiredCount = waitlistProcessingFacade.expireAndPromote(afterHoldExpires);

        // then
        assertThat(expiredCount).isEqualTo(1);

        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(findWaitlist(testData.secondMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.HOLD);

        assertThat(findWaitlist(testData.thirdMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.WAIT);

        /*
         * 이전 점유자의 결제 대기 예약은 그대로 남아있지만
         * 만료된 PENDING_PAYMENT이므로 새 예약과 겹침으로 취급되지 않는다.
         */
        assertThat(reservationRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("TTL이 지나지 않은 HOLD는 승계되지 않는다")
    void notYetExpiredHoldIsNotPromoted() {
        // given
        LocalDateTime firstRequestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime secondRequestedAt = firstRequestedAt.plusSeconds(1);

        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), firstRequestedAt);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.secondMemberId(), secondRequestedAt);

        LocalDateTime beforeHoldExpires = LocalDateTime.now(clock).plusMinutes(1);

        // when
        int expiredCount = waitlistProcessingFacade.expireAndPromote(beforeHoldExpires);

        // then
        assertThat(expiredCount).isZero();

        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.HOLD);

        assertThat(findWaitlist(testData.secondMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.WAIT);

        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기자가 없으면 만료만 되고 아무도 승격되지 않는다")
    void expiredHoldWithNoWaitersOnlyExpires() {
        // given
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), requestedAt);

        LocalDateTime afterHoldExpires = LocalDateTime.now(clock).plusMinutes(6);

        // when
        int expiredCount = waitlistProcessingFacade.expireAndPromote(afterHoldExpires);

        // then
        assertThat(expiredCount).isEqualTo(1);

        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(reservationRepository.count()).isEqualTo(1L);
    }

    private Waitlist findWaitlist(Long memberId) {
        return waitlistRepository
            .findBySpecialOfferIdAndMemberId(testData.offerId(), memberId)
            .orElseThrow();
    }

    private TestData createTestData() {
        Member firstMember = memberRepository.saveAndFlush(
            Member.create("waitlist-expiration-a@roompick.com", "encoded-password", "대기열 만료 테스트 A")
        );
        Member secondMember = memberRepository.saveAndFlush(
            Member.create("waitlist-expiration-b@roompick.com", "encoded-password", "대기열 만료 테스트 B")
        );
        Member thirdMember = memberRepository.saveAndFlush(
            Member.create("waitlist-expiration-c@roompick.com", "encoded-password", "대기열 만료 테스트 C")
        );

        Accommodation accommodation = Accommodation.create(
            "대기열 만료 테스트 호텔", "서울특별시 테스트구 만료로 1",
            "특가 대기열 만료·승계 통합 테스트용 숙소",
            LocalTime.of(15, 0), LocalTime.of(11, 0)
        );
        ReflectionTestUtils.setField(accommodation, "status", AccommodationStatus.ACTIVE);
        Accommodation savedAccommodation = accommodationRepository.saveAndFlush(accommodation);

        Room room = Room.create(
            savedAccommodation, "301", "대기열 만료 테스트 객실",
            "특가 대기열 만료·승계 통합 테스트용 객실",
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
        ReflectionTestUtils.setField(specialOffer, "status", SpecialOfferStatus.ACTIVE);
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
