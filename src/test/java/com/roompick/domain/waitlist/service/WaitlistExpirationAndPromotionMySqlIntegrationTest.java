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

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.entity.SpecialOfferStatus;
import com.roompick.domain.specialOffers.repository.SpecialOfferRepository;
import com.roompick.domain.waitlist.entity.Waitlist;
import com.roompick.domain.waitlist.entity.WaitlistStatus;
import com.roompick.domain.waitlist.facade.WaitlistProcessingFacade;
import com.roompick.domain.waitlist.repository.WaitlistRepository;
import com.roompick.testsupport.SharedMySqlTestContainer;

/**
 * HOLD 상태의 점유가 TTL 내 결제되지 않았을 때
 * 만료 감지와 다음 대기자 승계가 실제 MySQL 환경에서
 * 올바르게 동작하는지 검증합니다.
 */
@Tag("integration")
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false"
    }
)
@ActiveProfiles("test")
class WaitlistExpirationAndPromotionMySqlIntegrationTest {

    private static final String DATABASE_NAME = "roompick_waitlist_expiration_test";
    private static final ZoneId TEST_ZONE_ID = ZoneId.of("Asia/Seoul");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        SharedMySqlTestContainer.createDatabaseIfAbsent(DATABASE_NAME);
        registry.add("spring.datasource.url", () -> SharedMySqlTestContainer.jdbcUrl(DATABASE_NAME));
        registry.add("spring.datasource.username", () -> SharedMySqlTestContainer.USERNAME);
        registry.add("spring.datasource.password", () -> SharedMySqlTestContainer.PASSWORD);
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
    private ReservationService reservationService;
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

    @Test
    @DisplayName("HOLD 상태에서 결제가 성공하면 CONFIRMED로 전환되고 이후 만료 대상에서 제외된다")
    void confirmedHoldIsNotExpiredAfterTtl() {
        // given
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), requestedAt);

        Long reservationId = findWaitlist(testData.firstMemberId()).getReservationId();

        // when — 결제 성공 처리
        waitlistProcessingFacade.confirmByReservationId(reservationId);

        // then
        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.CONFIRMED);

        // when — TTL이 지난 뒤 만료 스케줄러가 실행돼도
        LocalDateTime afterHoldExpires = LocalDateTime.now(clock).plusMinutes(6);
        int expiredCount = waitlistProcessingFacade.expireAndPromote(afterHoldExpires);

        // then — CONFIRMED는 만료 대상이 아니므로 그대로 유지된다
        assertThat(expiredCount).isZero();
        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.CONFIRMED);
    }

    @Test
    @DisplayName("HOLD 상태에서 결제가 실패(취소)하면 EXPIRED로 전환되고 다음 대기자가 승격된다")
    void expiredByPaymentFailurePromotesNextWaiter() {
        // given
        LocalDateTime firstRequestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime secondRequestedAt = firstRequestedAt.plusSeconds(1);

        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), firstRequestedAt);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.secondMemberId(), secondRequestedAt);

        Long reservationId = findWaitlist(testData.firstMemberId()).getReservationId();

        // when — 결제 실패(또는 사용자 취소)로 예약이 취소된 상황을 흉내냄.
        // 실제 PaymentFacade/ReservationFacade도 예약 취소를 먼저 수행한 뒤
        // waitlistProcessingFacade를 호출하므로 같은 순서로 재현한다.
        //
        // reservationRepository.findById()로 먼저 엔티티를 꺼내 별도
        // 트랜잭션에 넘기면 detached 상태라 상태 변경이 반영되지 않으므로,
        // 조회와 취소를 한 트랜잭션에서 함께 처리하는 서비스 메서드를 쓴다.
        LocalDateTime failedAt = LocalDateTime.now(clock);
        reservationService.cancelReservation(testData.firstMemberId(), reservationId);

        waitlistProcessingFacade.expireByReservationIdAndPromoteNext(reservationId, failedAt);

        // then
        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(findWaitlist(testData.secondMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.HOLD);
    }

    @Test
    @DisplayName("HOLD 만료 승계와 새 점유 요청이 같은 특가에 동시에 실행돼도 HOLD는 정확히 1건만 남는다")
    void concurrentExpirationAndOccupyDoNotLeaveDanglingWait() throws Exception {
        // given — 만료 대상 HOLD 하나만 있고 아직 아무도 WAIT으로 대기하지 않는다
        LocalDateTime firstRequestedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        waitlistProcessingFacade.occupy(testData.offerId(), testData.firstMemberId(), firstRequestedAt);

        LocalDateTime afterHoldExpires = LocalDateTime.now(clock).plusMinutes(6);

        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newFixedThreadPool(2);

        // when — 만료 스케줄러와 신규 점유 요청을 동시에 실행
        java.util.concurrent.Future<Integer> expireFuture = executor.submit(() -> {
            startLatch.await();
            return waitlistProcessingFacade.expireAndPromote(afterHoldExpires);
        });
        java.util.concurrent.Future<?> occupyFuture = executor.submit(() -> {
            startLatch.await();
            waitlistProcessingFacade.occupy(
                testData.offerId(), testData.secondMemberId(), firstRequestedAt.plusSeconds(1)
            );
            return null;
        });

        startLatch.countDown();
        expireFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
        occupyFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // then — 특가 행 락으로 두 트랜잭션이 직렬화되므로, 어느 순서로 실행되든
        // 최종적으로 두 번째 회원은 HOLD 상태이고(WAIT으로 영구히 남지 않는다)
        // 이 특가에 HOLD는 정확히 1건만 존재한다
        assertThat(findWaitlist(testData.firstMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.EXPIRED);

        assertThat(findWaitlist(testData.secondMemberId()).getStatus())
            .isEqualTo(WaitlistStatus.HOLD);

        long holdCount = waitlistRepository.findAll().stream()
            .filter(waitlist -> waitlist.getStatus() == WaitlistStatus.HOLD)
            .count();
        assertThat(holdCount).isEqualTo(1L);
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
