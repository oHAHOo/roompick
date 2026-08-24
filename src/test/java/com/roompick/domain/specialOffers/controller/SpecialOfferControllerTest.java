package com.roompick.domain.specialOffers.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
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
import com.roompick.domain.specialOffers.entity.SpecialOffer;
import com.roompick.domain.specialOffers.repository.SpecialOfferRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpecialOfferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SpecialOfferRepository specialOfferRepository;

    @Test
    @DisplayName("판매 중인 특가만 판매 종료가 임박한 순으로 조회된다")
    void 판매_중인_특가만_판매_종료가_임박한_순으로_조회된다() throws Exception {
        // given
        Accommodation accommodation = accommodationRepository.save(createAccommodation());

        Room soonEndingRoom = roomRepository.save(createRoom(accommodation, "101", "곧 끝나는 특가 객실"));
        Room laterEndingRoom = roomRepository.save(createRoom(accommodation, "102", "나중에 끝나는 특가 객실"));
        Room scheduledRoom = roomRepository.save(createRoom(accommodation, "103", "아직 시작 안 한 특가 객실"));
        Room endedRoom = roomRepository.save(createRoom(accommodation, "104", "이미 끝난 특가 객실"));

        LocalDateTime now = LocalDateTime.now();

        SpecialOffer soonEnding = SpecialOffer.create(
            soonEndingRoom, 80_000L,
            now.minusHours(1), now.plusHours(1),
            LocalDate.now().plusDays(10), LocalDate.now().plusDays(12)
        );
        soonEnding.activate(now);
        specialOfferRepository.save(soonEnding);

        SpecialOffer laterEnding = SpecialOffer.create(
            laterEndingRoom, 90_000L,
            now.minusHours(1), now.plusHours(5),
            LocalDate.now().plusDays(10), LocalDate.now().plusDays(12)
        );
        laterEnding.activate(now);
        specialOfferRepository.save(laterEnding);

        SpecialOffer scheduled = SpecialOffer.create(
            scheduledRoom, 70_000L,
            now.plusHours(1), now.plusHours(2),
            LocalDate.now().plusDays(10), LocalDate.now().plusDays(12)
        );
        specialOfferRepository.save(scheduled);

        SpecialOffer ended = SpecialOffer.create(
            endedRoom, 60_000L,
            now.minusHours(5), now.minusHours(1),
            LocalDate.now().plusDays(10), LocalDate.now().plusDays(12)
        );
        ended.end(now);
        specialOfferRepository.save(ended);

        // when & then: 인증 없이 호출해도 ACTIVE 두 건만, 종료 임박 순으로 반환된다.
        mockMvc.perform(get("/api/v1/special-offers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].specialOfferId").value(soonEnding.getId()))
            .andExpect(jsonPath("$.data[0].accommodationId").value(accommodation.getId()))
            .andExpect(jsonPath("$.data[0].accommodationName").value("룸픽 호텔"))
            .andExpect(jsonPath("$.data[0].roomId").value(soonEndingRoom.getId()))
            .andExpect(jsonPath("$.data[0].roomName").value("곧 끝나는 특가 객실"))
            .andExpect(jsonPath("$.data[0].price").value(80_000))
            .andExpect(jsonPath("$.data[1].specialOfferId").value(laterEnding.getId()));
    }

    @Test
    @DisplayName("판매 중인 특가가 없으면 빈 배열을 반환한다")
    void 판매_중인_특가가_없으면_빈_배열을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/special-offers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(0));
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

    private Room createRoom(Accommodation accommodation, String roomNumber, String name) {
        Room room = Room.create(
            accommodation,
            roomNumber,
            name,
            "테스트 객실",
            100_000L,
            2,
            2
        );
        room.activate();
        return room;
    }
}
