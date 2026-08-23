package com.roompick.domain.accommodation.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.security.JwtTokenProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

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
            .andExpect(jsonPath("$.data.content[0].imageUrl")
                .value(org.hamcrest.Matchers.nullValue()))
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
        // given: 운영 중인 숙소가 저장되어 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        // when & then:
        // 숙소 상세 조회는 숙소 기본 정보만 반환하고,
        // 객실 목록과 운영 상태는 응답에 포함하지 않습니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}",
                    accommodation.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success")
                .value(true))
            .andExpect(jsonPath("$.message")
                .value("숙소 상세 조회에 성공했습니다."))
            .andExpect(jsonPath("$.data.accommodationId")
                .value(accommodation.getId()))
            .andExpect(jsonPath("$.data.name")
                .value("룸픽 호텔"))
            .andExpect(jsonPath("$.data.address")
                .value("서울특별시 강남구"))
            .andExpect(jsonPath("$.data.description")
                .value("RoomPick 테스트 숙소"))
            .andExpect(jsonPath("$.data.checkInTime")
                .value("15:00:00"))
            .andExpect(jsonPath("$.data.checkOutTime")
                .value("11:00:00"))
            .andExpect(jsonPath("$.data.imageUrls")
                .isArray())
            .andExpect(jsonPath("$.data.imageUrls")
                .isEmpty())
            .andExpect(jsonPath("$.data.status")
                .doesNotExist())
            .andExpect(jsonPath("$.data.rooms")
                .doesNotExist());
    }

    @Test
    @DisplayName("비공개 숙소 상세는 익명 요청에서 운영 중지 오류를 반환한다")
    void 비공개_숙소_상세는_익명_요청에서_운영_중지_오류를_반환한다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        deactivateAccommodation(accommodation);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}",
                    accommodation.getId()
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("ACCOMMODATION_INACTIVE"));
    }

    @Test
    @DisplayName("비공개 숙소 상세는 일반 회원 요청에서도 운영 중지 오류를 반환한다")
    void 비공개_숙소_상세는_일반_회원_요청에서도_운영_중지_오류를_반환한다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        deactivateAccommodation(accommodation);

        String userToken =
            jwtTokenProvider.createAccessToken(1L, MemberRole.USER);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}",
                    accommodation.getId()
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + userToken
                    )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("ACCOMMODATION_INACTIVE"));
    }

    @Test
    @DisplayName("관리자는 비공개 숙소 상세를 상태와 함께 조회할 수 있다")
    void 관리자는_비공개_숙소_상세를_조회할_수_있다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        deactivateAccommodation(accommodation);

        String adminToken =
            jwtTokenProvider.createAccessToken(1L, MemberRole.ADMIN);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}",
                    accommodation.getId()
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + adminToken
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accommodationId")
                .value(accommodation.getId()))
            .andExpect(jsonPath("$.data.status")
                .value("INACTIVE"));
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
        Room secondRoom = Room.create(
                accommodation,
                "202",
                "스위트룸",
                "넓은 스위트 객실",
                200000L,
                2,
                4
            );
        secondRoom.activate();
        roomRepository.save(secondRoom);

        Room firstRoom = Room.create(
                accommodation,
                "101",
                "디럭스 더블룸",
                "2인이 이용할 수 있는 더블룸",
                100000L,
                2,
                2
            );
        firstRoom.activate();
        roomRepository.save(firstRoom);

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
            .andExpect(jsonPath("$.data[0].imageUrl")
                .value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[1].roomId")
                .value(secondRoom.getId()))
            .andExpect(jsonPath("$.data[1].name")
                .value("스위트룸"))
            .andExpect(jsonPath("$.data[1].imageUrl")
                .value(org.hamcrest.Matchers.nullValue()));
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

    @Test
    void 비활성_숙소는_전체_숙소_목록에서_제외된다()
        throws Exception {
        // given: 운영 중인 숙소와 운영이 중단된 숙소가 저장되어 있습니다.
        Accommodation activeAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "운영 중인 숙소",
                    "서울특별시 강남구",
                    "운영 중인 테스트 숙소",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Accommodation inactiveAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "운영 중단 숙소",
                    "서울특별시 종로구",
                    "운영 중단 테스트 숙소",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        // 저장된 변경 내용을 DB에 반영한 뒤
        // 테스트 목적으로 한 숙소의 상태를 INACTIVE로 변경합니다.
        entityManager.flush();

        entityManager.createQuery(
                """
                UPDATE Accommodation accommodation
                SET accommodation.status = :status
                WHERE accommodation.id = :accommodationId
                """
            )
            .setParameter(
                "status",
                AccommodationStatus.INACTIVE
            )
            .setParameter(
                "accommodationId",
                inactiveAccommodation.getId()
            )
            .executeUpdate();

        // 벌크 쿼리는 영속성 컨텍스트를 거치지 않으므로
        // 기존 Entity 상태가 남지 않도록 초기화합니다.
        entityManager.clear();

        // when & then:
        // 전체 숙소 목록에는 ACTIVE 숙소만 포함됩니다.
        mockMvc.perform(
                get("/api/v1/accommodations")
                    .param("page", "0")
                    .param("size", "20")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()")
                .value(1))
            .andExpect(jsonPath("$.data.content[0].accommodationId")
                .value(activeAccommodation.getId()))
            .andExpect(jsonPath("$.data.content[0].name")
                .value("운영 중인 숙소"))
            .andExpect(jsonPath("$.data.totalElements")
                .value(1));
    }

    @Test
    void 비활성_객실은_숙소별_객실_목록에서_제외된다()
        throws Exception {
        // given: 운영 중인 숙소에 ACTIVE 객실과
        // 운영이 중단된 객실이 각각 저장되어 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(
                createAccommodation()
            );

        Room activeRoom = Room.create(
                accommodation,
                "101",
                "운영 중인 객실",
                "운영 중인 테스트 객실",
                100000L,
                2,
                2
            );
        activeRoom.activate();
        roomRepository.save(activeRoom);

        Room inactiveRoom = roomRepository.save(
            Room.create(
                accommodation,
                "202",
                "운영 중단 객실",
                "운영 중단 테스트 객실",
                200000L,
                2,
                4
            )
        );

        // 저장 내용을 DB에 반영한 뒤
        // 테스트 목적으로 한 객실의 상태를 INACTIVE로 변경합니다.
        entityManager.flush();

        entityManager.createQuery(
                """
                UPDATE Room room
                SET room.status = :status
                WHERE room.id = :roomId
                """
            )
            .setParameter(
                "status",
                RoomStatus.INACTIVE
            )
            .setParameter(
                "roomId",
                inactiveRoom.getId()
            )
            .executeUpdate();

        // 벌크 쿼리 이후 영속성 컨텍스트의 오래된 상태를 제거합니다.
        entityManager.clear();

        // when & then:
        // 숙소별 객실 목록에는 ACTIVE 객실만 포함되고, status 필드는 노출되지 않습니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}/rooms",
                    accommodation.getId()
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success")
                .value(true))
            .andExpect(jsonPath("$.data.length()")
                .value(1))
            .andExpect(jsonPath("$.data[0].roomId")
                .value(activeRoom.getId()))
            .andExpect(jsonPath("$.data[0].name")
                .value("운영 중인 객실"))
            .andExpect(jsonPath("$.data[0].status")
                .doesNotExist());
    }

    @Test
    @DisplayName("비공개 숙소의 객실 목록은 익명 요청에서 운영 중지 오류를 반환한다")
    void 비공개_숙소의_객실_목록은_익명_요청에서_운영_중지_오류를_반환한다()
        throws Exception {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        deactivateAccommodation(accommodation);

        // when & then
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}/rooms",
                    accommodation.getId()
                )
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("ACCOMMODATION_INACTIVE"));
    }

    @Test
    @DisplayName("관리자는 비공개 숙소의 객실 목록을 상태와 함께 조회할 수 있다")
    void 관리자는_비공개_숙소의_객실_목록을_조회할_수_있다()
        throws Exception {
        // given: 비공개 숙소에 ACTIVE 객실과 INACTIVE 객실이 함께 있습니다.
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());

        Room activeRoom = Room.create(
            accommodation,
            "101",
            "운영 중인 객실",
            "운영 중인 테스트 객실",
            100000L,
            2,
            2
        );
        activeRoom.activate();
        roomRepository.save(activeRoom);

        Room inactiveRoom = roomRepository.save(
            Room.create(
                accommodation,
                "202",
                "운영 중단 객실",
                "운영 중단 테스트 객실",
                200000L,
                2,
                4
            )
        );

        deactivateAccommodation(accommodation);

        String adminToken =
            jwtTokenProvider.createAccessToken(1L, MemberRole.ADMIN);

        // when & then: 숙소가 비공개여도 관리자는 소속 객실 전체를 조회할 수 있습니다.
        mockMvc.perform(
                get(
                    "/api/v1/accommodations/{accommodationId}/rooms",
                    accommodation.getId()
                )
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + adminToken
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()")
                .value(2))
            .andExpect(jsonPath("$.data[0].roomId")
                .value(activeRoom.getId()))
            .andExpect(jsonPath("$.data[0].status")
                .value("ACTIVE"))
            .andExpect(jsonPath("$.data[1].roomId")
                .value(inactiveRoom.getId()))
            .andExpect(jsonPath("$.data[1].status")
                .value("INACTIVE"));
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

    private void deactivateAccommodation(
        Accommodation accommodation
    ) {
        accommodation.inactivate();
        accommodationRepository.save(accommodation);
    }

    private Room createRoom(Accommodation accommodation) {
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
