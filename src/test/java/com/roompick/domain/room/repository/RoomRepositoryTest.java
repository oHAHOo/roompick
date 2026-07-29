package com.roompick.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Persistence;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * RoomRepository의 저장 및 조회 쿼리를 검증하는 JPA 테스트입니다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(JpaConfig.class)
class RoomRepositoryTest {

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("객실 상세 조회 시 숙소를 fetch join으로 함께 조회한다")
    void findByIdWithAccommodation() {
        // given
        Accommodation accommodation = accommodationRepository.save(createAccommodation());
        Room room = roomRepository.save(createRoom(accommodation));

        // 영속성 컨텍스트를 비워 DB에서 다시 조회하도록 만듭니다.
        entityManager.flush();
        entityManager.clear();

        // when
        Room foundRoom = roomRepository.findByIdWithAccommodation(room.getId())
            .orElseThrow();

        // then
        assertThat(foundRoom.getName()).isEqualTo("디럭스 더블룸");
        assertThat(foundRoom.getAccommodation().getName()).isEqualTo("룸픽 호텔");

        // fetch join으로 숙소 Entity가 이미 로딩됐는지 확인합니다.
        boolean accommodationLoaded = Persistence.getPersistenceUtil()
            .isLoaded(foundRoom.getAccommodation());
        assertThat(accommodationLoaded).isTrue();
    }

    @Test
    @DisplayName("숙소 ID로 해당 숙소의 객실 목록을 조회한다")
    void findAllByAccommodationId() {
        // given
        Accommodation accommodation = accommodationRepository.save(createAccommodation());
        roomRepository.save(createRoom(accommodation));

        entityManager.flush();
        entityManager.clear();

        // when
        List<Room> rooms = roomRepository.findAllByAccommodationId(
            accommodation.getId()
        );

        // then
        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).getRoomNumber()).isEqualTo("101");
        assertThat(rooms.get(0).getName()).isEqualTo("디럭스 더블룸");
    }

    @Test
    @DisplayName("사용자 객실 목록에는 ACTIVE 객실만 조회한다")
    void findAllActiveSummaryByAccommodationId() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        Room activeRoom = createRoom(accommodation);
        activeRoom.activate();
        roomRepository.save(activeRoom);
        roomRepository.save(
            Room.create(
                accommodation,
                "102",
                "비공개 객실",
                "비공개 객실 설명",
                120_000L,
                2,
                2
            )
        );

        entityManager.flush();
        entityManager.clear();

        // when
        var rooms = roomRepository
            .findAllActiveSummaryByAccommodationId(
                accommodation.getId()
            );

        // then
        assertThat(rooms).hasSize(1);
        assertThat(rooms.get(0).roomId()).isEqualTo(activeRoom.getId());
    }

    @Test
    @DisplayName("roomId와 accommodationId가 모두 일치하는 객실만 조회한다")
    void findByIdAndAccommodationId() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        Accommodation otherAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "다른 호텔",
                    "서울특별시 종로구",
                    "다른 테스트 숙소",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );
        Room room = roomRepository.save(createRoom(accommodation));

        // when
        var matchingRoom = roomRepository.findByIdAndAccommodationId(
            room.getId(),
            accommodation.getId()
        );
        var mismatchedRoom = roomRepository.findByIdAndAccommodationId(
            room.getId(),
            otherAccommodation.getId()
        );

        // then
        assertThat(matchingRoom).isPresent();
        assertThat(mismatchedRoom).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE 상태인 객실만 사용자 상세 조회 조건에 일치한다")
    void findByIdAndStatus() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        Room room = roomRepository.save(createRoom(accommodation));

        // when & then
        assertThat(
            roomRepository.findByIdAndStatus(
                room.getId(),
                RoomStatus.ACTIVE
            )
        ).isEmpty();
    }

    private Accommodation createAccommodation() {
        return Accommodation.create(
            "룸픽 호텔",
            "서울특별시 강남구 테헤란로 123",
            "RoomPick MVP 예약 테스트를 위한 숙소입니다.",
            LocalTime.of(15, 0),
            LocalTime.of(11, 0)
        );
    }

    private Room createRoom(Accommodation accommodation) {
        return Room.create(
            accommodation,
            "101",
            "디럭스 더블룸",
            "2인이 이용할 수 있는 더블룸입니다.",
            100_000L,
            2,
            2
        );
    }
}
