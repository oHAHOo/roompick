package com.roompick.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationIdempotency;
import com.roompick.domain.reservation.repository.ReservationIdempotencyRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 예약 생성 요청의 멱등성 처리 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationIdempotencyServiceTest {

    private static final String IDEMPOTENCY_KEY =
        "reservation-create-20260810-0001";

    private static final String REQUEST_HASH =
        "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef";

    @Mock
    private ReservationIdempotencyRepository
        reservationIdempotencyRepository;

    @Mock
    private ReservationRequestHasher
        reservationRequestHasher;

    @Mock
    private ReservationIdempotency
        reservationIdempotency;

    @Mock
    private Reservation reservation;

    @InjectMocks
    private ReservationIdempotencyService
        reservationIdempotencyService;

    @Test
    @DisplayName(
        "최초 요청이면 처리 정보를 생성하고 "
            + "잠금 조회 결과를 반환한다"
    )
    void 최초_요청이면_처리_정보를_생성한다() {
        // given
        Long memberId = 1L;

        ReservationCreateRequestDto request =
            createRequest();

        given(
            reservationRequestHasher.hash(request)
        ).willReturn(REQUEST_HASH);

        given(
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    IDEMPOTENCY_KEY,
                    REQUEST_HASH
                )
        ).willReturn(1);

        given(
            reservationIdempotencyRepository
                .findByMemberIdAndIdempotencyKeyForUpdate(
                    memberId,
                    IDEMPOTENCY_KEY
                )
        ).willReturn(
            Optional.of(
                reservationIdempotency
            )
        );

        given(
            reservationIdempotency
                .matchesRequestHash(
                    REQUEST_HASH
                )
        ).willReturn(true);

        // when
        ReservationIdempotency result =
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                );

        // then
        assertThat(result)
            .isSameAs(reservationIdempotency);

        then(reservationRequestHasher)
            .should()
            .hash(request);

        then(reservationIdempotencyRepository)
            .should()
            .insertProcessingIfAbsent(
                memberId,
                IDEMPOTENCY_KEY,
                REQUEST_HASH
            );

        then(reservationIdempotencyRepository)
            .should()
            .findByMemberIdAndIdempotencyKeyForUpdate(
                memberId,
                IDEMPOTENCY_KEY
            );

        then(reservationIdempotency)
            .should()
            .matchesRequestHash(
                REQUEST_HASH
            );
    }

    @Test
    @DisplayName(
        "기존 멱등성 처리 정보가 있으면 "
            + "잠금 조회한 기존 정보를 반환한다"
    )
    void 기존_처리_정보가_있으면_기존_정보를_반환한다() {
        // given
        Long memberId = 1L;

        ReservationCreateRequestDto request =
            createRequest();

        given(
            reservationRequestHasher.hash(request)
        ).willReturn(REQUEST_HASH);

        /*
         * INSERT가 수행되지 않았다는 것은
         * 동일한 회원과 멱등성 키의 행이 이미 존재한다는 의미입니다.
         */
        given(
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    IDEMPOTENCY_KEY,
                    REQUEST_HASH
                )
        ).willReturn(0);

        given(
            reservationIdempotencyRepository
                .findByMemberIdAndIdempotencyKeyForUpdate(
                    memberId,
                    IDEMPOTENCY_KEY
                )
        ).willReturn(
            Optional.of(
                reservationIdempotency
            )
        );

        given(
            reservationIdempotency
                .matchesRequestHash(
                    REQUEST_HASH
                )
        ).willReturn(true);

        // when
        ReservationIdempotency result =
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                );

        // then
        assertThat(result)
            .isSameAs(reservationIdempotency);

        then(reservationIdempotencyRepository)
            .should()
            .insertProcessingIfAbsent(
                memberId,
                IDEMPOTENCY_KEY,
                REQUEST_HASH
            );

        then(reservationIdempotencyRepository)
            .should()
            .findByMemberIdAndIdempotencyKeyForUpdate(
                memberId,
                IDEMPOTENCY_KEY
            );

        then(reservationIdempotency)
            .should()
            .matchesRequestHash(
                REQUEST_HASH
            );
    }

    @Test
    @DisplayName(
        "같은 멱등성 키의 요청 해시가 다르면 "
            + "멱등성 충돌 예외를 반환한다"
    )
    void 같은_키의_요청_해시가_다르면_충돌한다() {
        // given
        Long memberId = 1L;

        ReservationCreateRequestDto request =
            createRequest();

        given(
            reservationRequestHasher.hash(request)
        ).willReturn(REQUEST_HASH);

        given(
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    IDEMPOTENCY_KEY,
                    REQUEST_HASH
                )
        ).willReturn(0);

        given(
            reservationIdempotencyRepository
                .findByMemberIdAndIdempotencyKeyForUpdate(
                    memberId,
                    IDEMPOTENCY_KEY
                )
        ).willReturn(
            Optional.of(
                reservationIdempotency
            )
        );

        given(
            reservationIdempotency
                .matchesRequestHash(
                    REQUEST_HASH
                )
        ).willReturn(false);

        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode
                    .RESERVATION_IDEMPOTENCY_CONFLICT
            );

        then(reservationIdempotency)
            .should()
            .matchesRequestHash(
                REQUEST_HASH
            );
    }

    @Test
    @DisplayName(
        "처리 정보를 생성한 뒤 조회할 수 없으면 "
            + "내부 상태 예외를 반환한다"
    )
    void 처리_정보를_조회할_수_없으면_예외가_발생한다() {
        // given
        Long memberId = 1L;

        ReservationCreateRequestDto request =
            createRequest();

        given(
            reservationRequestHasher.hash(request)
        ).willReturn(REQUEST_HASH);

        given(
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    IDEMPOTENCY_KEY,
                    REQUEST_HASH
                )
        ).willReturn(1);

        given(
            reservationIdempotencyRepository
                .findByMemberIdAndIdempotencyKeyForUpdate(
                    memberId,
                    IDEMPOTENCY_KEY
                )
        ).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "예약 멱등성 처리 정보를 조회할 수 없습니다."
            );

        then(reservationIdempotency)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "멱등성 처리 정보 생성 중 락 대기 시간이 초과되면 "
            + "예약 락 타임아웃 예외를 반환한다"
    )
    void 처리_정보_생성_중_락_타임아웃을_변환한다() {
        // given
        Long memberId = 1L;

        ReservationCreateRequestDto request =
            createRequest();

        given(
            reservationRequestHasher.hash(request)
        ).willReturn(REQUEST_HASH);

        given(
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    IDEMPOTENCY_KEY,
                    REQUEST_HASH
                )
        ).willThrow(
            new PessimisticLockingFailureException(
                "멱등성 처리 정보 생성 락 타임아웃"
            )
        );

        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.RESERVATION_LOCK_TIMEOUT
            );

        then(reservationIdempotencyRepository)
            .should()
            .insertProcessingIfAbsent(
                memberId,
                IDEMPOTENCY_KEY,
                REQUEST_HASH
            );

        then(reservationIdempotencyRepository)
            .shouldHaveNoMoreInteractions();

        then(reservationIdempotency)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "멱등성 처리 정보 잠금 조회 중 락 대기 시간이 초과되면 "
            + "예약 락 타임아웃 예외를 반환한다"
    )
    void 처리_정보_잠금_조회_중_락_타임아웃을_변환한다() {
        // given
        Long memberId = 1L;

        ReservationCreateRequestDto request =
            createRequest();

        given(
            reservationRequestHasher.hash(request)
        ).willReturn(REQUEST_HASH);

        given(
            reservationIdempotencyRepository
                .insertProcessingIfAbsent(
                    memberId,
                    IDEMPOTENCY_KEY,
                    REQUEST_HASH
                )
        ).willReturn(0);

        given(
            reservationIdempotencyRepository
                .findByMemberIdAndIdempotencyKeyForUpdate(
                    memberId,
                    IDEMPOTENCY_KEY
                )
        ).willThrow(
            new PessimisticLockingFailureException(
                "멱등성 처리 정보 조회 락 타임아웃"
            )
        );

        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.RESERVATION_LOCK_TIMEOUT
            );

        then(reservationIdempotencyRepository)
            .should()
            .findByMemberIdAndIdempotencyKeyForUpdate(
                memberId,
                IDEMPOTENCY_KEY
            );

        then(reservationIdempotency)
            .shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        longs = {
            0L,
            -1L
        }
    )
    @DisplayName(
        "회원 ID가 올바르지 않으면 요청을 처리하지 않는다"
    )
    void 회원_ID가_올바르지_않으면_요청을_처리하지_않는다(
        Long memberId
    ) {
        // given
        ReservationCreateRequestDto request =
            createRequest();

        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService
                .getOrCreate(
                    memberId,
                    IDEMPOTENCY_KEY,
                    request
                )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        then(reservationRequestHasher)
            .shouldHaveNoInteractions();

        then(reservationIdempotencyRepository)
            .shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("invalidIdempotencyKeys")
    @DisplayName(
        "멱등성 키가 올바르지 않으면 요청을 처리하지 않는다"
    )
    void 멱등성_키가_올바르지_않으면_요청을_처리하지_않는다(
        String idempotencyKey
    ) {
        // given
        ReservationCreateRequestDto request =
            createRequest();

        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService
                .getOrCreate(
                    1L,
                    idempotencyKey,
                    request
                )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );

        then(reservationRequestHasher)
            .shouldHaveNoInteractions();

        then(reservationIdempotencyRepository)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "예약 생성이 완료되면 처리 정보에 예약을 연결한다"
    )
    void 예약_생성이_완료되면_처리_정보를_완료한다() {
        // when
        reservationIdempotencyService.complete(
            reservationIdempotency,
            reservation
        );

        // then
        then(reservationIdempotency)
            .should()
            .complete(reservation);
    }

    @Test
    @DisplayName(
        "멱등성 처리 정보가 없으면 완료 처리할 수 없다"
    )
    void 멱등성_처리_정보가_없으면_완료할_수_없다() {
        // when & then
        assertThatThrownBy(() ->
            reservationIdempotencyService.complete(
                null,
                reservation
            )
        )
            .isInstanceOf(
                BusinessException.class
            )
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_INPUT_VALUE
            );
    }

    private ReservationCreateRequestDto createRequest() {
        return new ReservationCreateRequestDto(
            20L,
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 12),
            2
        );
    }

    private static Stream<String>
    invalidIdempotencyKeys() {
        return Stream.of(
            null,
            "",
            "   ",
            "a".repeat(101)
        );
    }
}
