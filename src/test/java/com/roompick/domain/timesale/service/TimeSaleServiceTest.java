package com.roompick.domain.timesale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.entity.TimeSaleStatus;
import com.roompick.domain.timesale.repository.TimeSaleRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 타임세일 등록과 상태 전환을 담당하는
 * TimeSaleService의 동작을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class TimeSaleServiceTest {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    /**
     * 테스트에서 사용하는 현재 시각입니다.
     *
     * 2026-08-12T03:00:00Z는
     * 한국 시간으로 2026-08-12 12:00입니다.
     */
    private static final Instant FIXED_INSTANT =
        Instant.parse("2026-08-12T03:00:00Z");

    private static final LocalDateTime NOW =
        LocalDateTime.of(
            2026,
            8,
            12,
            12,
            0
        );

    @Mock
    private TimeSaleRepository timeSaleRepository;

    private TimeSaleService timeSaleService;

    @BeforeEach
    void setUp() {
        Clock fixedClock =
            Clock.fixed(
                FIXED_INSTANT,
                SERVICE_ZONE_ID
            );

        timeSaleService =
            new TimeSaleService(
                timeSaleRepository,
                fixedClock
            );
    }

    @Test
    @DisplayName(
        "숙소 전체 타임세일을 등록하면 "
            + "SCHEDULED 상태로 저장한다"
    )
    void 숙소_전체_타임세일을_등록한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        LocalDateTime startAt =
            NOW.plusHours(1);

        LocalDateTime endAt =
            NOW.plusHours(3);

        given(
            timeSaleRepository
                .existsOverlappingAccommodationSale(
                    accommodation.getId(),
                    startAt,
                    endAt
                )
        ).willReturn(false);

        given(
            timeSaleRepository.save(any(TimeSale.class))
        ).willAnswer(invocation ->
            invocation.getArgument(0)
        );

        // when
        TimeSale timeSale =
            timeSaleService.create(
                accommodation,
                null,
                20,
                startAt,
                endAt
            );

        // then
        assertThat(timeSale.getAccommodation())
            .isSameAs(accommodation);

        assertThat(timeSale.getRoom())
            .isNull();

        assertThat(timeSale.getDiscountRate())
            .isEqualTo(20);

        assertThat(timeSale.getStartAt())
            .isEqualTo(startAt);

        assertThat(timeSale.getEndAt())
            .isEqualTo(endAt);

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.SCHEDULED
            );

        then(timeSaleRepository)
            .should()
            .existsOverlappingAccommodationSale(
                accommodation.getId(),
                startAt,
                endAt
            );

        then(timeSaleRepository)
            .should()
            .save(timeSale);
    }

    @Test
    @DisplayName(
        "시작 시각에 도달한 객실 타임세일을 "
            + "등록하면 ACTIVE 상태로 저장한다"
    )
    void 시작된_객실_타임세일을_등록한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        Room room =
            createRoom(
                10L,
                accommodation
            );

        LocalDateTime startAt =
            NOW.minusMinutes(30);

        LocalDateTime endAt =
            NOW.plusHours(2);

        given(
            timeSaleRepository
                .existsOverlappingRoomSale(
                    room.getId(),
                    startAt,
                    endAt
                )
        ).willReturn(false);

        given(
            timeSaleRepository.save(any(TimeSale.class))
        ).willAnswer(invocation ->
            invocation.getArgument(0)
        );

        // when
        TimeSale timeSale =
            timeSaleService.create(
                accommodation,
                room,
                30,
                startAt,
                endAt
            );

        // then
        assertThat(timeSale.getAccommodation())
            .isSameAs(accommodation);

        assertThat(timeSale.getRoom())
            .isSameAs(room);

        assertThat(timeSale.getDiscountRate())
            .isEqualTo(30);

        assertThat(timeSale.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );

        assertThat(timeSale.appliesAt(NOW))
            .isTrue();

        then(timeSaleRepository)
            .should()
            .existsOverlappingRoomSale(
                room.getId(),
                startAt,
                endAt
            );

        then(timeSaleRepository)
            .should()
            .save(timeSale);
    }

    @Test
    @DisplayName(
        "같은 숙소의 전체 타임세일 기간이 겹치면 "
            + "등록하지 않는다"
    )
    void 숙소_전체_타임세일_기간이_겹치면_실패한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        LocalDateTime startAt =
            NOW.plusHours(1);

        LocalDateTime endAt =
            NOW.plusHours(3);

        given(
            timeSaleRepository
                .existsOverlappingAccommodationSale(
                    accommodation.getId(),
                    startAt,
                    endAt
                )
        ).willReturn(true);

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                accommodation,
                null,
                20,
                startAt,
                endAt
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP
            );

        then(timeSaleRepository)
            .should()
            .existsOverlappingAccommodationSale(
                accommodation.getId(),
                startAt,
                endAt
            );

        then(timeSaleRepository)
            .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName(
        "같은 객실의 타임세일 기간이 겹치면 "
            + "등록하지 않는다"
    )
    void 객실_타임세일_기간이_겹치면_실패한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        Room room =
            createRoom(
                10L,
                accommodation
            );

        LocalDateTime startAt =
            NOW.plusHours(1);

        LocalDateTime endAt =
            NOW.plusHours(3);

        given(
            timeSaleRepository
                .existsOverlappingRoomSale(
                    room.getId(),
                    startAt,
                    endAt
                )
        ).willReturn(true);

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                accommodation,
                room,
                20,
                startAt,
                endAt
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.TIME_SALE_PERIOD_OVERLAP
            );

        then(timeSaleRepository)
            .should()
            .existsOverlappingRoomSale(
                room.getId(),
                startAt,
                endAt
            );

        then(timeSaleRepository)
            .shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName(
        "객실이 요청한 숙소에 소속되지 않으면 "
            + "타임세일을 등록하지 않는다"
    )
    void 객실이_다른_숙소에_속하면_실패한다() {
        // given
        Accommodation requestedAccommodation =
            createAccommodation(1L);

        Accommodation roomAccommodation =
            createAccommodation(2L);

        Room room =
            createRoom(
                10L,
                roomAccommodation
            );

        LocalDateTime startAt =
            NOW.plusHours(1);

        LocalDateTime endAt =
            NOW.plusHours(3);

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                requestedAccommodation,
                room,
                20,
                startAt,
                endAt
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.TIME_SALE_TARGET_MISMATCH
            );

        /*
         * Entity 생성 단계에서 대상 불일치가 검증되므로
         * Repository는 호출되지 않아야 합니다.
         */
        then(timeSaleRepository)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "할인율이 허용 범위를 벗어나면 "
            + "타임세일을 등록하지 않는다"
    )
    void 할인율이_허용_범위를_벗어나면_실패한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        LocalDateTime startAt =
            NOW.plusHours(1);

        LocalDateTime endAt =
            NOW.plusHours(3);

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                accommodation,
                null,
                100,
                startAt,
                endAt
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode
                    .INVALID_TIME_SALE_DISCOUNT_RATE
            );

        then(timeSaleRepository)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "종료 시각이 시작 시각보다 빠르면 "
            + "타임세일을 등록하지 않는다"
    )
    void 잘못된_타임세일_기간이면_실패한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        LocalDateTime startAt =
            NOW.plusHours(3);

        LocalDateTime endAt =
            NOW.plusHours(1);

        // when & then
        assertThatThrownBy(() ->
            timeSaleService.create(
                accommodation,
                null,
                20,
                startAt,
                endAt
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception)
                    .getErrorCode()
            )
            .isEqualTo(
                ErrorCode.INVALID_TIME_SALE_PERIOD
            );

        then(timeSaleRepository)
            .shouldHaveNoInteractions();
    }

    @Test
    @DisplayName(
        "시작 시각에 도달한 SCHEDULED 타임세일을 "
            + "ACTIVE 상태로 변경한다"
    )
    void 시작_시각에_도달한_타임세일을_활성화한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        /*
         * 타임세일 생성 당시에는 시작 전이므로
         * SCHEDULED 상태로 생성합니다.
         */
        TimeSale target =
            TimeSale.create(
                accommodation,
                null,
                20,
                NOW.minusMinutes(30),
                NOW.plusHours(1),
                NOW.minusHours(1)
            );

        assertThat(target.getStatus())
            .isEqualTo(
                TimeSaleStatus.SCHEDULED
            );

        given(
            timeSaleRepository.findStartTargets(
                TimeSaleStatus.SCHEDULED,
                NOW
            )
        ).willReturn(List.of(target));

        // when
        int activatedCount =
            timeSaleService.activateDueSales();

        // then
        assertThat(activatedCount)
            .isEqualTo(1);

        assertThat(target.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );

        then(timeSaleRepository)
            .should()
            .findStartTargets(
                TimeSaleStatus.SCHEDULED,
                NOW
            );
    }

    @Test
    @DisplayName(
        "활성화 대상이 없으면 0을 반환한다"
    )
    void 활성화_대상이_없으면_0을_반환한다() {
        // given
        given(
            timeSaleRepository.findStartTargets(
                TimeSaleStatus.SCHEDULED,
                NOW
            )
        ).willReturn(List.of());

        // when
        int activatedCount =
            timeSaleService.activateDueSales();

        // then
        assertThat(activatedCount)
            .isZero();

        then(timeSaleRepository)
            .should()
            .findStartTargets(
                TimeSaleStatus.SCHEDULED,
                NOW
            );
    }

    @Test
    @DisplayName(
        "종료 시각에 도달한 ACTIVE 타임세일을 "
            + "ENDED 상태로 변경한다"
    )
    void 종료_시각에_도달한_타임세일을_종료한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        /*
         * 생성 시각에는 이미 시작됐지만 종료 전이므로
         * ACTIVE 상태로 생성합니다.
         */
        TimeSale target =
            TimeSale.create(
                accommodation,
                null,
                20,
                NOW.minusHours(2),
                NOW.minusMinutes(30),
                NOW.minusHours(1)
            );

        assertThat(target.getStatus())
            .isEqualTo(
                TimeSaleStatus.ACTIVE
            );

        List<TimeSaleStatus> targetStatuses =
            List.of(
                TimeSaleStatus.SCHEDULED,
                TimeSaleStatus.ACTIVE
            );

        given(
            timeSaleRepository.findEndTargets(
                targetStatuses,
                NOW
            )
        ).willReturn(List.of(target));

        // when
        int endedCount =
            timeSaleService.endDueSales();

        // then
        assertThat(endedCount)
            .isEqualTo(1);

        assertThat(target.getStatus())
            .isEqualTo(
                TimeSaleStatus.ENDED
            );

        assertThat(target.appliesAt(NOW))
            .isFalse();

        then(timeSaleRepository)
            .should()
            .findEndTargets(
                targetStatuses,
                NOW
            );
    }

    @Test
    @DisplayName(
        "활성화되지 못하고 종료 시각이 지난 "
            + "SCHEDULED 타임세일도 ENDED 상태로 변경한다"
    )
    void 누락된_SCHEDULED_타임세일도_종료한다() {
        // given
        Accommodation accommodation =
            createAccommodation(1L);

        /*
         * 생성 당시에는 시작 전이어서 SCHEDULED였지만,
         * 활성화 스케줄 실행 없이 종료 시각까지 지난 상황입니다.
         */
        TimeSale target =
            TimeSale.create(
                accommodation,
                null,
                20,
                NOW.minusHours(1),
                NOW.minusMinutes(30),
                NOW.minusHours(2)
            );

        assertThat(target.getStatus())
            .isEqualTo(
                TimeSaleStatus.SCHEDULED
            );

        List<TimeSaleStatus> targetStatuses =
            List.of(
                TimeSaleStatus.SCHEDULED,
                TimeSaleStatus.ACTIVE
            );

        given(
            timeSaleRepository.findEndTargets(
                targetStatuses,
                NOW
            )
        ).willReturn(List.of(target));

        // when
        int endedCount =
            timeSaleService.endDueSales();

        // then
        assertThat(endedCount)
            .isEqualTo(1);

        assertThat(target.getStatus())
            .isEqualTo(
                TimeSaleStatus.ENDED
            );

        then(timeSaleRepository)
            .should()
            .findEndTargets(
                targetStatuses,
                NOW
            );
    }

    @Test
    @DisplayName(
        "종료 대상이 없으면 0을 반환한다"
    )
    void 종료_대상이_없으면_0을_반환한다() {
        // given
        List<TimeSaleStatus> targetStatuses =
            List.of(
                TimeSaleStatus.SCHEDULED,
                TimeSaleStatus.ACTIVE
            );

        given(
            timeSaleRepository.findEndTargets(
                targetStatuses,
                NOW
            )
        ).willReturn(List.of());

        // when
        int endedCount =
            timeSaleService.endDueSales();

        // then
        assertThat(endedCount)
            .isZero();

        then(timeSaleRepository)
            .should()
            .findEndTargets(
                targetStatuses,
                NOW
            );
    }

    /**
     * ID가 필요한 테스트 숙소를 생성합니다.
     */
    private Accommodation createAccommodation(
        Long accommodationId
    ) {
        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔 " + accommodationId,
                "서울특별시 강남구",
                "타임세일 테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        ReflectionTestUtils.setField(
            accommodation,
            "id",
            accommodationId
        );

        return accommodation;
    }

    /**
     * ID와 숙소 정보가 필요한 테스트 객실을 생성합니다.
     */
    private Room createRoom(
        Long roomId,
        Accommodation accommodation
    ) {
        Room room =
            Room.create(
                accommodation,
                "101",
                "디럭스 더블룸",
                "타임세일 테스트 객실",
                100_000L,
                2,
                2
            );

        ReflectionTestUtils.setField(
            room,
            "id",
            roomId
        );

        return room;
    }
}
