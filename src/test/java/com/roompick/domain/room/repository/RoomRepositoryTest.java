package com.roompick.domain.room.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    @DisplayName("관리자 객실 활성화 조회 시 소속 숙소를 fetch join한다")
    void findByIdAndAccommodationIdWithAccommodation() {
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

        entityManager.flush();
        entityManager.clear();

        // when
        var matchingRoom = roomRepository
            .findByIdAndAccommodationIdWithAccommodation(
                room.getId(),
                accommodation.getId()
            );
        var mismatchedRoom = roomRepository
            .findByIdAndAccommodationIdWithAccommodation(
                room.getId(),
                otherAccommodation.getId()
            );

        // then
        assertThat(matchingRoom).isPresent();
        assertThat(mismatchedRoom).isEmpty();
        assertThat(
            Persistence.getPersistenceUtil().isLoaded(
                matchingRoom.orElseThrow().getAccommodation()
            )
        ).isTrue();
    }

    @Test
    @DisplayName("숙소의 ACTIVE 객실을 벌크 UPDATE로 비활성화한다")
    void deactivateAllByAccommodationId() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        Room activeRoom = createRoom(accommodation);
        activeRoom.activate();
        roomRepository.save(activeRoom);
        Room inactiveRoom = Room.create(
            accommodation,
            "102",
            "이미 비공개된 객실",
            "비공개 객실 설명",
            120_000L,
            2,
            2
        );
        roomRepository.save(inactiveRoom);

        entityManager.flush();
        entityManager.clear();

        // when
        int updatedCount =
            roomRepository.deactivateAllByAccommodationId(
                accommodation.getId()
            );

        // then
        assertThat(updatedCount).isEqualTo(1);
        assertThat(
            roomRepository.findById(activeRoom.getId())
                .orElseThrow()
                .getStatus()
        ).isEqualTo(RoomStatus.INACTIVE);
        assertThat(
            roomRepository.findById(inactiveRoom.getId())
                .orElseThrow()
                .getStatus()
        ).isEqualTo(RoomStatus.INACTIVE);
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
    @DisplayName("예약 생성용 비관적 락 조회는 이미지 컬렉션을 초기화하지 않는다")
    void findByIdForUpdate_이미지를_로딩하지_않는다() {
        // given
        Accommodation accommodation = accommodationRepository.save(createAccommodation());
        Room room = roomRepository.save(createRoom(accommodation));

        entityManager.flush();
        entityManager.clear();

        // when
        Room foundRoom = roomRepository.findByIdForUpdate(room.getId())
            .orElseThrow();

        // then
        // 락 획득 경로에서는 이미지가 필요 없으므로 LAZY 컬렉션이
        // 초기화되지 않은 채로 남아 있어야 락 보유 시간이 불필요하게 늘어나지 않는다.
        boolean imagesLoaded = Persistence.getPersistenceUtil()
            .isLoaded(foundRoom, "images");
        assertThat(imagesLoaded).isFalse();
    }

    @Test
    @DisplayName("사용자 상세 조회는 이미지 컬렉션을 fetch join으로 함께 초기화한다")
    void findPublicById_이미지를_함께_로딩한다() {
        // given
        Accommodation accommodation = accommodationRepository.save(createAccommodation());
        Room room = createRoom(accommodation);
        room.activate();
        room.addImages(List.of("https://example.com/room.jpg"));
        roomRepository.save(room);

        entityManager.flush();
        entityManager.clear();

        // when
        Room foundRoom = roomRepository.findPublicById(
            room.getId(),
            RoomStatus.ACTIVE,
            AccommodationStatus.ACTIVE
        ).orElseThrow();

        // then
        boolean imagesLoaded = Persistence.getPersistenceUtil()
            .isLoaded(foundRoom, "images");
        assertThat(imagesLoaded).isTrue();
        assertThat(foundRoom.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("객실과 숙소가 모두 ACTIVE인 경우에만 사용자 상세 조회 조건에 일치한다")
    void findPublicById() {
        // given
        Accommodation activeAccommodation =
            accommodationRepository.save(createAccommodation());
        Room publicRoom = createRoom(activeAccommodation);
        publicRoom.activate();
        roomRepository.save(publicRoom);

        Accommodation inactiveAccommodation = createAccommodation();
        ReflectionTestUtils.setField(
            inactiveAccommodation,
            "status",
            AccommodationStatus.INACTIVE
        );
        accommodationRepository.save(inactiveAccommodation);

        Room hiddenRoom = createRoom(inactiveAccommodation);
        hiddenRoom.activate();
        roomRepository.save(hiddenRoom);

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThat(
            roomRepository.findPublicById(
                publicRoom.getId(),
                RoomStatus.ACTIVE,
                AccommodationStatus.ACTIVE
            )
        ).isPresent();

        assertThat(
            roomRepository.findPublicById(
                hiddenRoom.getId(),
                RoomStatus.ACTIVE,
                AccommodationStatus.ACTIVE
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
