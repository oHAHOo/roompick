package com.roompick.domain.accommodation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccommodationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void 전체_숙소_목록_조회에_성공한다() throws Exception {
        // given: 여러 개의 운영 중인 숙소가 저장되어 있습니다.
        Accommodation firstAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "룸픽 서울 호텔",
                    "서울특별시 강남구",
                    "서울 테스트 숙소",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        accommodationRepository.save(
            Accommodation.create(
                "룸픽 부산 리조트",
                "부산광역시 해운대구",
                "부산 테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            )
        );

        // when & then:
        // 인증 헤더 없이 첫 번째 페이지의 숙소 목록을 조회합니다.
        mockMvc.perform(
                get("/api/v1/accommodations")
                    .param("page", "0")
                    .param("size", "1")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message")
                .value("숙소 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.content.length()")
                .value(1))
            .andExpect(jsonPath("$.data.content[0].accommodationId")
                .value(firstAccommodation.getId()))
            .andExpect(jsonPath("$.data.content[0].name")
                .value("룸픽 서울 호텔"))
            .andExpect(jsonPath("$.data.content[0].address")
                .value("서울특별시 강남구"))
            .andExpect(jsonPath("$.data.pageNumber")
                .value(0))
            .andExpect(jsonPath("$.data.pageSize")
                .value(1))
            .andExpect(jsonPath("$.data.totalElements")
                .value(2))
            .andExpect(jsonPath("$.data.totalPages")
                .value(2))
            .andExpect(jsonPath("$.data.last")
                .value(false));
    }

    @Test
    void 숙소_상세_조회에_성공한다() throws Exception {
        // given: 숙소와 소속 객실이 저장되어 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        // when & then: 인증 헤더 없이 숙소 상세 정보를 조회합니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}",
                    accommodation.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message")
                .value("숙소 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.accommodationId")
                .value(accommodation.getId()))
            .andExpect(jsonPath("$.data.name")
                .value("룸픽 호텔"))
            .andExpect(jsonPath("$.data.address")
                .value("서울특별시 강남구"))
            .andExpect(jsonPath("$.data.status")
                .value("ACTIVE"))
            .andExpect(jsonPath("$.data.rooms.length()")
                .value(1))
            .andExpect(jsonPath("$.data.rooms[0].roomId")
                .value(room.getId()))
            .andExpect(jsonPath("$.data.rooms[0].name")
                .value("디럭스 더블룸"))
            .andExpect(jsonPath("$.data.rooms[0].pricePerNight")
                .value(100000))
            .andExpect(jsonPath("$.data.rooms[0].status")
                .value("ACTIVE"));
    }

    @Test
    void 존재하지_않는_숙소를_조회하면_404를_반환한다()
        throws Exception {
        // when & then: 존재하지 않는 ID로 조회하면 공통 에러를 반환합니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}",
                    999L
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code")
                .value("ACCOMMODATION_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("숙소를 찾을 수 없습니다."));
    }

    @Test
    void 숙소별_객실_목록_조회에_성공한다() throws Exception {
        // given: 운영 중인 숙소와 객실 두 개가 저장되어 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        // 객실 번호가 큰 객실을 먼저 저장해
        // 저장 순서가 아닌 객실 번호 순서로 조회되는지 확인합니다.
        Room secondRoom = roomRepository.save(
            Room.create(
                accommodation,
                "202",
                "스위트룸",
                "넓은 스위트 객실",
                200000L,
                2,
                4
            )
        );

        Room firstRoom = roomRepository.save(
            Room.create(
                accommodation,
                "101",
                "디럭스 더블룸",
                "2인이 이용할 수 있는 더블룸",
                100000L,
                2,
                2
            )
        );

        // when & then:
        // 인증 헤더 없이 해당 숙소의 운영 중인 객실 목록을 조회합니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}/rooms",
                    accommodation.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message")
                .value("객실 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.length()")
                .value(2))
            .andExpect(jsonPath("$.data[0].roomId")
                .value(firstRoom.getId()))
            .andExpect(jsonPath("$.data[0].name")
                .value("디럭스 더블룸"))
            .andExpect(jsonPath("$.data[0].pricePerNight")
                .value(100000))
            .andExpect(jsonPath("$.data[0].standardCapacity")
                .value(2))
            .andExpect(jsonPath("$.data[0].maxCapacity")
                .value(2))
            .andExpect(jsonPath("$.data[1].roomId")
                .value(secondRoom.getId()))
            .andExpect(jsonPath("$.data[1].name")
                .value("스위트룸"));
    }

    @Test
    void 존재하지_않는_숙소의_객실_목록을_조회하면_404를_반환한다()
        throws Exception {
        // when & then:
        // 존재하지 않는 숙소 ID로 객실 목록을 요청하면
        // 숙소 없음 공통 에러를 반환합니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}/rooms",
                    999L
                )
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success")
                .value(false))
            .andExpect(jsonPath("$.code")
                .value("ACCOMMODATION_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("숙소를 찾을 수 없습니다."));
    }

    @Test
    void 객실이_없는_숙소의_객실_목록을_조회하면_빈_목록을_반환한다()
        throws Exception {
        // given: 운영 중인 숙소만 저장되어 있고
        // 해당 숙소에 등록된 객실은 없습니다.
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        // when & then:
        // 객실이 없어도 오류가 아닌 빈 배열을 정상 반환합니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}/rooms",
                    accommodation.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success")
                .value(true))
            .andExpect(jsonPath("$.message")
                .value("객실 목록 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.length()")
                .value(0));
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

    private Room createRoom(Accommodation accommodation) {
        return Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸",
            100000L,
            2,
            2
        );
    }
}
