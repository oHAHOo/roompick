package com.roompick.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.roompick.domain.payment.dto.response.PaymentFailResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.payment.dto.response.PaymentApproveResponseDto;
import com.roompick.domain.payment.entity.Payment;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.repository.PaymentRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 실제 Spring Context와 H2 DB를 사용하여
 * 결제 승인 및 실패 처리 트랜잭션의
 * 커밋과 롤백을 검증합니다.
 *
 * 이 테스트 클래스에는 @Transactional을 붙이지 않습니다.
 * PaymentFacade의 트랜잭션이 실제로 종료된 다음
 * DB를 다시 조회해야 하기 때문입니다.
 */
@SpringBootTest(
    properties = {
        "spring.datasource.url="
            + "jdbc:h2:mem:payment-facade-integration-test;"
            + "MODE=MySQL;"
            + "DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false"
    }
)
@ActiveProfiles("test")
class PaymentFacadeIntegrationTest {

    private static final ZoneId TEST_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    /**
     * 외래키 제약조건을 고려하여 자식 테이블부터 삭제합니다.
     */
    @AfterEach
    void tearDown() {
        paymentRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        accommodationRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "결제 승인 성공 시 Payment와 Reservation 상태가 함께 커밋된다"
    )
    void approvePaymentCommitsPaymentAndReservationTogether() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        // when
        PaymentApproveResponseDto response =
            paymentFacade.approvePayment(
                testData.paymentId(),
                testData.memberId(),
                testData.amount()
            );

        // then
        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(savedPayment.getApprovedAt())
            .isNotNull();

        assertThat(savedPayment.getFailedAt())
            .isNull();

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(response.paymentId())
            .isEqualTo(testData.paymentId());

        assertThat(response.reservationId())
            .isEqualTo(testData.reservationId());

        assertThat(response.amount())
            .isEqualTo(testData.amount());

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(response.approvedAt())
            .isEqualTo(
                savedPayment.getApprovedAt()
            );
    }

    @Test
    @DisplayName(
        "결제 금액 불일치 시 Payment와 Reservation 변경이 모두 롤백된다"
    )
    void amountMismatchRollsBackPaymentAndReservation() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        long wrongAmount =
            testData.amount() - 1_000L;

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.approvePayment(
                    testData.paymentId(),
                    testData.memberId(),
                    wrongAmount
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.PAYMENT_AMOUNT_MISMATCH
            );

        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        assertThat(savedPayment.getApprovedAt())
            .isNull();

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );
    }

    @Test
    @DisplayName(
        "이미 승인된 결제를 같은 금액으로 다시 승인하면 기존 성공 결과를 반환한다"
    )
    void duplicatedApprovalReturnsExistingSuccess() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        paymentFacade.approvePayment(
            testData.paymentId(),
            testData.memberId(),
            testData.amount()
        );

        Payment firstApprovedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        LocalDateTime firstApprovedAt =
            firstApprovedPayment.getApprovedAt();

        // when
        PaymentApproveResponseDto response =
            paymentFacade.approvePayment(
                testData.paymentId(),
                testData.memberId(),
                testData.amount()
            );

        // then
        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(savedPayment.getApprovedAt())
            .isEqualTo(firstApprovedAt);

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(response.paymentId())
            .isEqualTo(testData.paymentId());

        assertThat(response.reservationId())
            .isEqualTo(testData.reservationId());

        assertThat(response.amount())
            .isEqualTo(testData.amount());

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.PAID);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CONFIRMED
            );

        assertThat(response.approvedAt())
            .isEqualTo(firstApprovedAt);
    }

    @Test
    @DisplayName(
        "만료된 예약의 결제 승인 실패 시 Payment 변경도 롤백된다"
    )
    void expiredReservationRollsBackPaymentApproval() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.minusMinutes(1)
            );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.approvePayment(
                    testData.paymentId(),
                    testData.memberId(),
                    testData.amount()
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_PAYMENT_EXPIRED
            );

        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        /*
         * Payment가 먼저 PAID로 변경되지만,
         * 예약 만료 검증에서 예외가 발생하므로
         * Facade 트랜잭션이 롤백되어 READY 상태가 유지됩니다.
         */
        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        assertThat(savedPayment.getApprovedAt())
            .isNull();

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );
    }

    @Test
    @DisplayName(
        "결제 실패 처리 성공 시 Payment와 Reservation 상태가 함께 커밋된다"
    )
    void failPaymentCommitsPaymentAndReservationTogether() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        // when
        PaymentFailResponseDto response =
            paymentFacade.failPayment(
                testData.paymentId(),
                testData.memberId()
            );

        // then
        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(savedPayment.getFailedAt())
            .isNotNull();

        assertThat(savedPayment.getApprovedAt())
            .isNull();

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );

        assertThat(savedReservation.getCanceledAt())
            .isNotNull();

        /*
         * 결제 실패와 예약 취소에는
         * Facade에서 생성한 동일한 시각이 사용됩니다.
         */
        assertThat(savedPayment.getFailedAt())
            .isEqualTo(
                savedReservation.getCanceledAt()
            );

        assertThat(response.paymentId())
            .isEqualTo(testData.paymentId());

        assertThat(response.reservationId())
            .isEqualTo(testData.reservationId());

        assertThat(response.amount())
            .isEqualTo(testData.amount());

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );

        assertThat(response.failedAt())
            .isEqualTo(
                savedPayment.getFailedAt()
            );

        assertThat(response.canceledAt())
            .isEqualTo(
                savedReservation.getCanceledAt()
            );
    }

    @Test
    @DisplayName(
        "다른 회원의 결제 실패 처리 요청 시 Payment 변경도 롤백된다"
    )
    void unauthorizedFailureRollsBackPaymentAndReservation() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        Long otherMemberId =
            testData.memberId() + 1L;

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> paymentFacade.failPayment(
                    testData.paymentId(),
                    otherMemberId
                ),
                BusinessException.class
            );

        // then
        assertThat(exception.getErrorCode())
            .isEqualTo(
                ErrorCode.RESERVATION_ACCESS_DENIED
            );

        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        /*
         * Payment가 먼저 FAILED로 변경되지만
         * 예약 소유자 검증에서 예외가 발생하므로
         * 전체 트랜잭션이 롤백됩니다.
         */
        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.READY);

        assertThat(savedPayment.getFailedAt())
            .isNull();

        assertThat(savedPayment.getApprovedAt())
            .isNull();

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(savedReservation.getCanceledAt())
            .isNull();
    }

    @Test
    @DisplayName(
        "이미 실패 처리된 결제를 다시 처리하면 기존 성공 결과를 반환한다"
    )
    void duplicatedFailureReturnsExistingSuccess() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.plusMinutes(10)
            );

        paymentFacade.failPayment(
            testData.paymentId(),
            testData.memberId()
        );

        Payment firstFailedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation firstCanceledReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        LocalDateTime firstFailedAt =
            firstFailedPayment.getFailedAt();

        LocalDateTime firstCanceledAt =
            firstCanceledReservation.getCanceledAt();

        // when
        PaymentFailResponseDto response =
            paymentFacade.failPayment(
                testData.paymentId(),
                testData.memberId()
            );

        // then
        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(savedPayment.getFailedAt())
            .isEqualTo(firstFailedAt);

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );

        assertThat(savedReservation.getCanceledAt())
            .isEqualTo(firstCanceledAt);

        assertThat(response.paymentId())
            .isEqualTo(testData.paymentId());

        assertThat(response.reservationId())
            .isEqualTo(testData.reservationId());

        assertThat(response.amount())
            .isEqualTo(testData.amount());

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );

        assertThat(response.failedAt())
            .isEqualTo(firstFailedAt);

        assertThat(response.canceledAt())
            .isEqualTo(firstCanceledAt);
    }

    @Test
    @DisplayName(
        "결제 대기 시간이 만료된 예약도 결제 실패 처리할 수 있다"
    )
    void expiredReservationCanBeFailedAndCanceled() {
        // given
        LocalDateTime now =
            LocalDateTime.now(TEST_ZONE_ID);

        TestData testData =
            createTestData(
                now.minusMinutes(1)
            );

        // when
        PaymentFailResponseDto response =
            paymentFacade.failPayment(
                testData.paymentId(),
                testData.memberId()
            );

        // then
        Payment savedPayment =
            paymentRepository
                .findById(testData.paymentId())
                .orElseThrow();

        Reservation savedReservation =
            reservationRepository
                .findById(testData.reservationId())
                .orElseThrow();

        /*
         * 이미 결제 대기 시간이 만료되었더라도
         * 객실 점유를 해제하기 위해 실패 처리를 허용합니다.
         */
        assertThat(savedPayment.getStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(savedPayment.getFailedAt())
            .isNotNull();

        assertThat(savedReservation.getStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );

        assertThat(savedReservation.getCanceledAt())
            .isNotNull();

        assertThat(response.paymentStatus())
            .isEqualTo(PaymentStatus.FAILED);

        assertThat(response.reservationStatus())
            .isEqualTo(
                ReservationStatus.CANCELED
            );
    }

    /**
     * 통합 테스트에 필요한 회원, 숙소, 객실, 예약, 결제를
     * 실제 Repository에 순서대로 저장합니다.
     */
    private TestData createTestData(
        LocalDateTime expiresAt
    ) {
        Member member =
            memberRepository.saveAndFlush(
                Member.create(
                    "payment-test@roompick.com",
                    "encoded-password",
                    "결제 테스트 회원"
                )
            );

        Accommodation accommodation =
            accommodationRepository.saveAndFlush(
                Accommodation.create(
                    "룸픽 테스트 호텔",
                    "서울특별시 테스트구 테스트로 1",
                    "결제 통합 테스트용 숙소",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Room room =
            roomRepository.saveAndFlush(
                Room.create(
                    accommodation,
                    "101",
                    "테스트 객실",
                    "결제 통합 테스트용 객실",
                    100_000L,
                    2,
                    2
                )
            );

        LocalDate checkInDate =
            LocalDate.now(TEST_ZONE_ID)
                .plusDays(1);

        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        Reservation reservation =
            reservationRepository.saveAndFlush(
                Reservation.create(
                    member,
                    room,
                    checkInDate,
                    checkOutDate,
                    2,
                    expiresAt
                )
            );

        Payment payment =
            paymentRepository.saveAndFlush(
                Payment.create(reservation)
            );

        return new TestData(
            member.getId(),
            reservation.getId(),
            payment.getId(),
            payment.getAmount()
        );
    }

    /**
     * 테스트 실행에 필요한 ID와 금액을 보관합니다.
     */
    private record TestData(
        Long memberId,
        Long reservationId,
        Long paymentId,
        long amount
    ) {
    }
}
