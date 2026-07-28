package com.roompick.domain.reservation.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.reservation.dto.ReservationCancelResponseDto;
import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.domain.reservation.dto.ReservationCreateResponseDto;
import com.roompick.domain.reservation.dto.ReservationDetailResponseDto;
import com.roompick.domain.reservation.dto.ReservationListResponseDto;
import com.roompick.domain.reservation.dto.ReservationPageResponseDto;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.entity.ReservationStatus;
import com.roompick.domain.reservation.service.ReservationService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

/**
 * 객실 Service와 예약 Service를 연결하는
 * ReservationFacade의 예약 생성 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationFacadeTest {

    @Mock
    private RoomService roomService;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationFacade reservationFacade;

    @Test
    @DisplayName("객실을 조회한 뒤 결제 대기 상태의 예약을 생성한다")
    void 객실을_조회한_뒤_예약을_생성한다() {
        // given
        Long memberId = 1L;
        Long accommodationId = 10L;
        Long roomId = 20L;
        Long reservationId = 30L;

        LocalDate checkInDate =
            LocalDate.of(2026, 8, 10);

        LocalDate checkOutDate =
            LocalDate.of(2026, 8, 12);

        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 12, 10);

        ReservationCreateRequestDto request =
            new ReservationCreateRequestDto(
                roomId,
                checkInDate,
                checkOutDate,
                2
            );

        Accommodation accommodation =
            createAccommodation(accommodationId);

        Room room =
            createRoom(
                roomId,
                accommodation
            );

        Member member =
            createMember(memberId);

        Reservation reservation =
            Reservation.create(
                member,
                room,
                checkInDate,
                checkOutDate,
                2,
                expiresAt
            );

        ReflectionTestUtils.setField(
            reservation,
            "id",
            reservationId
        );

        given(
            roomService.findReservableRoomWithAccommodation(
                roomId,
                2
            )
        ).willReturn(room);

        given(
            reservationService.createReservation(
                memberId,
                room,
                checkInDate,
                checkOutDate,
                2
            )
        ).willReturn(reservation);

        // when
        ReservationCreateResponseDto response =
            reservationFacade.createReservation(
                memberId,
                request
            );

        // then
        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.memberId())
            .isEqualTo(memberId);

        assertThat(response.accommodation().accommodationId())
            .isEqualTo(accommodationId);

        assertThat(response.accommodation().name())
            .isEqualTo("룸픽 호텔");

        assertThat(response.room().roomId())
            .isEqualTo(roomId);

        assertThat(response.room().name())
            .isEqualTo("디럭스 더블룸");

        assertThat(response.room().roomNumber())
            .isEqualTo("101");

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.pricePerNight())
            .isEqualTo(100_000L);

        assertThat(response.totalAmount())
            .isEqualTo(200_000L);

        assertThat(response.status())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(response.expiresAt())
            .isEqualTo(expiresAt);

        then(roomService)
            .should()
            .findReservableRoomWithAccommodation(
                roomId,
                2
            );

        then(reservationService)
            .should()
            .createReservation(
                memberId,
                room,
                checkInDate,
                checkOutDate,
                2
            );
    }

    @Test
    @DisplayName("인증된 회원의 예약 목록을 페이지 응답 DTO로 변환한다")
    void 인증된_회원의_예약_목록을_조회한다() {
        // given
        Long memberId = 1L;
        Long accommodationId = 10L;
        Long roomId = 20L;
        Long reservationId = 30L;

        int page = 0;
        int size = 10;

        LocalDate checkInDate =
            LocalDate.of(2026, 8, 10);

        LocalDate checkOutDate =
            LocalDate.of(2026, 8, 12);

        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 12, 10);

        LocalDateTime createdAt =
            LocalDateTime.of(2026, 8, 1, 12, 0);

        Accommodation accommodation =
            createAccommodation(accommodationId);

        Room room =
            createRoom(
                roomId,
                accommodation
            );

        Member member =
            createMember(memberId);

        Reservation reservation =
            Reservation.create(
                member,
                room,
                checkInDate,
                checkOutDate,
                2,
                expiresAt
            );

        ReflectionTestUtils.setField(
            reservation,
            "id",
            reservationId
        );

        ReflectionTestUtils.setField(
            reservation,
            "createdAt",
            createdAt
        );

        Page<Reservation> reservationPage =
            new PageImpl<>(
                List.of(reservation),
                PageRequest.of(page, size),
                1
            );

        given(
            reservationService.findMyReservations(
                memberId,
                page,
                size
            )
        ).willReturn(reservationPage);

        // when
        ReservationPageResponseDto response =
            reservationFacade.getMyReservations(
                memberId,
                page,
                size
            );

        // then
        assertThat(response.content())
            .hasSize(1);

        ReservationListResponseDto reservationResponse =
            response.content().get(0);

        assertThat(reservationResponse.reservationId())
            .isEqualTo(reservationId);

        assertThat(reservationResponse.accommodationName())
            .isEqualTo("룸픽 호텔");

        assertThat(reservationResponse.roomName())
            .isEqualTo("디럭스 더블룸");

        assertThat(reservationResponse.checkInDate())
            .isEqualTo(checkInDate);

        assertThat(reservationResponse.checkOutDate())
            .isEqualTo(checkOutDate);

        assertThat(reservationResponse.guestCount())
            .isEqualTo(2);

        assertThat(reservationResponse.totalAmount())
            .isEqualTo(200_000L);

        assertThat(reservationResponse.status())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(reservationResponse.createdAt())
            .isEqualTo(createdAt);

        assertThat(response.pageNumber())
            .isZero();

        assertThat(response.pageSize())
            .isEqualTo(10);

        assertThat(response.totalElements())
            .isEqualTo(1);

        assertThat(response.totalPages())
            .isEqualTo(1);

        assertThat(response.last())
            .isTrue();

        then(reservationService)
            .should()
            .findMyReservations(
                memberId,
                page,
                size
            );
    }

    @Test
    @DisplayName("본인의 예약 상세 정보를 응답 DTO로 변환한다")
    void 본인의_예약_상세_정보를_조회한다() {
        // given
        Long memberId = 1L;
        Long accommodationId = 10L;
        Long roomId = 20L;
        Long reservationId = 30L;

        LocalDate checkInDate =
            LocalDate.of(2026, 8, 10);

        LocalDate checkOutDate =
            LocalDate.of(2026, 8, 12);

        LocalDateTime expiresAt =
            LocalDateTime.of(2026, 8, 1, 12, 10);

        LocalDateTime createdAt =
            LocalDateTime.of(2026, 8, 1, 12, 0);

        Accommodation accommodation =
            createAccommodation(accommodationId);

        Room room =
            createRoom(
                roomId,
                accommodation
            );

        Member member =
            createMember(memberId);

        Reservation reservation =
            Reservation.create(
                member,
                room,
                checkInDate,
                checkOutDate,
                2,
                expiresAt
            );

        ReflectionTestUtils.setField(
            reservation,
            "id",
            reservationId
        );

        ReflectionTestUtils.setField(
            reservation,
            "createdAt",
            createdAt
        );

        given(
            reservationService.findMyReservation(
                memberId,
                reservationId
            )
        ).willReturn(reservation);

        // when
        ReservationDetailResponseDto response =
            reservationFacade.getMyReservation(
                memberId,
                reservationId
            );

        // then
        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(
            response
                .accommodation()
                .accommodationId()
        ).isEqualTo(accommodationId);

        assertThat(
            response
                .accommodation()
                .name()
        ).isEqualTo("룸픽 호텔");

        assertThat(
            response
                .accommodation()
                .address()
        ).isEqualTo("서울특별시 강남구");

        assertThat(response.room().roomId())
            .isEqualTo(roomId);

        assertThat(response.room().name())
            .isEqualTo("디럭스 더블룸");

        assertThat(response.room().roomNumber())
            .isEqualTo("101");

        assertThat(response.checkInDate())
            .isEqualTo(checkInDate);

        assertThat(response.checkOutDate())
            .isEqualTo(checkOutDate);

        assertThat(response.guestCount())
            .isEqualTo(2);

        assertThat(response.nightCount())
            .isEqualTo(2);

        assertThat(response.pricePerNight())
            .isEqualTo(100_000L);

        assertThat(response.totalAmount())
            .isEqualTo(200_000L);

        assertThat(response.status())
            .isEqualTo(
                ReservationStatus.PENDING_PAYMENT
            );

        assertThat(response.expiresAt())
            .isEqualTo(expiresAt);

        assertThat(response.canceledAt())
            .isNull();

        assertThat(response.createdAt())
            .isEqualTo(createdAt);

        then(reservationService)
            .should()
            .findMyReservation(
                memberId,
                reservationId
            );
    }

    @Test
    @DisplayName("취소된 예약을 예약 취소 응답 DTO로 변환한다")
    void 결제_대기_예약을_취소한다() {
        // given: 인증된 회원이 소유한 결제 대기 예약이 있습니다.
        Long memberId = 1L;
        Long reservationId = 30L;

        LocalDateTime canceledAt =
            LocalDateTime.of(
                2026,
                8,
                2,
                10,
                0
            );

        Accommodation accommodation =
            createAccommodation(10L);

        Room room =
            createRoom(
                20L,
                accommodation
            );

        Member member =
            createMember(memberId);

        Reservation reservation =
            Reservation.create(
                member,
                room,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                2,
                LocalDateTime.of(2026, 8, 1, 14, 10)
            );

        ReflectionTestUtils.setField(
            reservation,
            "id",
            reservationId
        );

        reservation.cancelByMember(
            memberId,
            canceledAt
        );

        given(
            reservationService.cancelReservation(
                memberId,
                reservationId
            )
        ).willReturn(reservation);

        // when: Facade를 통해 예약을 취소합니다.
        ReservationCancelResponseDto response =
            reservationFacade.cancelReservation(
                memberId,
                reservationId
            );

        // then: 취소 결과가 응답 DTO로 변환됩니다.
        assertThat(response.reservationId())
            .isEqualTo(reservationId);

        assertThat(response.status())
            .isEqualTo(ReservationStatus.CANCELED);

        assertThat(response.canceledAt())
            .isEqualTo(canceledAt);

        then(reservationService)
            .should()
            .cancelReservation(
                memberId,
                reservationId
            );
    }

    /**
     * ID가 필요한 단위 테스트 객체를 생성합니다.
     */
    private Accommodation createAccommodation(
        Long accommodationId
    ) {
        Accommodation accommodation =
            Accommodation.create(
                "룸픽 호텔",
                "서울특별시 강남구",
                "RoomPick 테스트 숙소",
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
     * 예약 생성 응답에 사용할 객실을 생성합니다.
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
                "2인이 이용할 수 있는 더블룸",
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

    /**
     * 예약 회원의 ID가 필요한 테스트 객체를 생성합니다.
     */
    private Member createMember(Long memberId) {
        Member member =
            Member.create(
                "roompick@example.com",
                "encoded-password",
                "룸픽 회원"
            );

        ReflectionTestUtils.setField(
            member,
            "id",
            memberId
        );

        return member;
    }
}
