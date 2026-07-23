package com.roompick.domain.room.controller;

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
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void 객실_상세_조회에_성공한다() throws Exception {
        // given: 숙소와 소속 객실이 저장되어 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());

        Room room = roomRepository.save(
            createRoom(accommodation)
        );

        // when & then: 인증 헤더 없이 객실 상세 정보를 조회합니다.
        mockMvc.perform(
                get(
                    "/api/v1/rooms/{roomId}",
                    room.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message")
                .value("객실 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.roomId")
                .value(room.getId()))
            .andExpect(jsonPath("$.data.accommodation.accommodationId")
                .value(accommodation.getId()))
            .andExpect(jsonPath("$.data.accommodation.name")
                .value("룸픽 호텔"))
            .andExpect(jsonPath("$.data.accommodation.address")
                .value("서울특별시 강남구"))
            .andExpect(jsonPath("$.data.roomNumber")
                .value("101"))
            .andExpect(jsonPath("$.data.name")
                .value("디럭스 더블룸"))
            .andExpect(jsonPath("$.data.pricePerNight")
                .value(100000))
            .andExpect(jsonPath("$.data.standardCapacity")
                .value(2))
            .andExpect(jsonPath("$.data.maxCapacity")
                .value(2))
            .andExpect(jsonPath("$.data.status")
                .value("ACTIVE"));
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
