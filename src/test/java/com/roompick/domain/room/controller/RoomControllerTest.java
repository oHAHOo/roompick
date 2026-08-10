package com.roompick.domain.room.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.Member;
import com.roompick.domain.member.repository.MemberRepository;
import com.roompick.domain.reservation.entity.Reservation;
import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccommodationRepository accommodationRepository;

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void 객실_상세_조회에_성공한다() throws Exception {
        // given: 숙소와 소속 객실이 저장되어 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        // when & then:
        // 객실 상세 화면에 필요한 객실 정보만 반환합니다.
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}",
                    room.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success")
                .value(true))
            .andExpect(jsonPath("$.message")
                .value("객실 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.roomId")
                .value(room.getId()))
            .andExpect(jsonPath("$.data.roomNumber")
                .value("101"))
            .andExpect(jsonPath("$.data.name")
                .value("디럭스 더블룸"))
            .andExpect(jsonPath("$.data.description")
                .value("2인이 이용할 수 있는 더블룸"))
            .andExpect(jsonPath("$.data.pricePerNight")
                .value(100000))
            .andExpect(jsonPath("$.data.standardCapacity")
                .value(2))
            .andExpect(jsonPath("$.data.maxCapacity")
                .value(2))
            .andExpect(jsonPath("$.data.imageUrls")
                .isArray())
            .andExpect(jsonPath("$.data.imageUrls")
                .isEmpty())
            .andExpect(jsonPath("$.data.accommodation")
                .doesNotExist())
            .andExpect(jsonPath("$.data.status")
                .doesNotExist());
    }

    @Test
    void 존재하지_않는_객실을_조회하면_404를_반환한다()
        throws Exception {
        // when & then: 존재하지 않는 객실 ID는 공통 에러를 반환합니다.
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}",
                    999L
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code")
                .value("ROOM_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("객실을 찾을 수 없습니다."));
    }

    @Test
    void 비공개_객실을_상세_조회하면_404를_반환한다()
        throws Exception {
        // given: 새 객실은 INACTIVE 상태로 저장됩니다.
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room room = roomRepository.save(
            Room.create(
                accommodation,
                "102",
                "비공개 객실",
                "아직 공개하지 않은 객실",
                100000L,
                2,
                2
            )
        );

        // when & then
        mockMvc.perform(
                get("/api/v1/rooms/{roomId}", room.getId())
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("ROOM_NOT_FOUND"));
    }

    @Test
    void 날짜_형식이_올바르지_않으면_400을_반환한다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        LocalDate checkOutDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(2);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}/availability",
                    room.getId()
                )
                    .queryParam(
                        "checkInDate",
                        "2026/08/10"
                    )
                    .queryParam(
                        "checkOutDate",
                        checkOutDate.toString()
                    )
                    .queryParam("guestCount", "2")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success")
                .value(false))
            .andExpect(jsonPath("$.code")
                .value("COMMON_001"))
            .andExpect(jsonPath("$.message")
                .value("요청 값이 올바르지 않습니다."));
    }

    private Accommodation createAccommodation() {
        return Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구",
            "RoomPick 테스트 숙소",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );
    }

    @Test
    void 겹치는_예약이_없으면_객실을_예약할_수_있다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}/availability",
                    room.getId()
                )
                    .queryParam(
                        "checkInDate",
                        checkInDate.toString()
                    )
                    .queryParam(
                        "checkOutDate",
                        checkOutDate.toString()
                    )
                    .queryParam("guestCount", "2")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message")
                .value("객실 예약 가능 여부 확인에 성공했습니다."))
            .andExpect(jsonPath("$.data.roomId")
                .value(room.getId()))
            .andExpect(jsonPath("$.data.nightCount")
                .value(2))
            .andExpect(jsonPath("$.data.pricePerNight")
                .value(100000))
            .andExpect(jsonPath("$.data.totalAmount")
                .value(200000))
            .andExpect(jsonPath("$.data.available")
                .value(true))
            .andExpect(jsonPath("$.data.status")
                .value("ACTIVE"))
            .andExpect(jsonPath("$.data.unavailableReason")
                .doesNotExist());
    }

    @Test
    void 겹치는_활성_예약이_있으면_객실을_예약할_수_없다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        Member member = memberRepository.save(
            Member.create(
                "roompick@example.com",
                "encoded-password",
                "룸픽 회원"
            )
        );

        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        reservationRepository.save(
            Reservation.create(
                member,
                room,
                checkInDate,
                checkOutDate,
                2,
                LocalDateTime.now(SERVICE_ZONE_ID)
                    .plusMinutes(10)
            )
        );

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}/availability",
                    room.getId()
                )
                    .queryParam(
                        "checkInDate",
                        checkInDate.toString()
                    )
                    .queryParam(
                        "checkOutDate",
                        checkOutDate.toString()
                    )
                    .queryParam("guestCount", "2")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.available")
                .value(false))
            .andExpect(jsonPath("$.data.status")
                .value("SOLD_OUT"))
            .andExpect(jsonPath("$.data.unavailableReason")
                .value("선택한 날짜에 이미 예약된 객실입니다."));
    }

    @Test
    void 객실_최대_인원을_초과하면_400을_반환한다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        LocalDate checkInDate =
            LocalDate.now(SERVICE_ZONE_ID).plusDays(1);
        LocalDate checkOutDate =
            checkInDate.plusDays(2);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}/availability",
                    room.getId()
                )
                    .queryParam(
                        "checkInDate",
                        checkInDate.toString()
                    )
                    .queryParam(
                        "checkOutDate",
                        checkOutDate.toString()
                    )
                    .queryParam("guestCount", "3")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success")
                .value(false))
            .andExpect(jsonPath("$.code")
                .value("ROOM_CAPACITY_EXCEEDED"))
            .andExpect(jsonPath("$.message")
                .value("객실 최대 인원을 초과했습니다."));
    }

    private Room createRoom(
        Accommodation accommodation
    ) {
        Room room = Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸",
            100000L,
            2,
            2
        );

        room.activate();

        return room;
    }
}
