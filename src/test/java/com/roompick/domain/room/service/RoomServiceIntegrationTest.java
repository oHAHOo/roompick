package com.roompick.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationCacheEvictionService;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomStatus;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.JpaConfig;

import jakarta.persistence.EntityManager;

@ActiveProfiles("test")
@DataJpaTest
@Import({
    JpaConfig.class,
    RoomService.class,
    AccommodationService.class
})
class RoomServiceIntegrationTest {

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomService roomService;

    @MockitoBean
    private PopularAccommodationCacheEvictionService
        popularAccommodationCacheEvictionService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("객실 활성화 변경 감지가 실제 DB에 반영된다")
    void activateRoomPersistsByDirtyChecking() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(createAccommodation());
        Room room = roomRepository.save(createRoom(accommodation));

        Long accommodationId = accommodation.getId();
        Long roomId = room.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        roomService.activateRoom(
            accommodationId,
            roomId
        );
        entityManager.flush();
        entityManager.clear();

        // then
        Room persistedRoom = roomRepository.findById(roomId)
            .orElseThrow();
        assertThat(persistedRoom.getStatus())
            .isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    @DisplayName("운영 중지된 숙소의 객실도 비공개로 변경할 수 있다")
    void deactivateRoomInInactiveAccommodation() {
        // given
        Accommodation accommodation = createAccommodation();
        deactivateAccommodation(accommodation);
        accommodationRepository.save(accommodation);

        Room room = createRoom(accommodation);
        room.activate();
        roomRepository.save(room);

        Long accommodationId = accommodation.getId();
        Long roomId = room.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        roomService.deactivateRoom(
            accommodationId,
            roomId
        );
        entityManager.flush();
        entityManager.clear();

        // then
        Room persistedRoom = roomRepository.findById(roomId)
            .orElseThrow();
        assertThat(persistedRoom.getStatus())
            .isEqualTo(RoomStatus.INACTIVE);
        then(popularAccommodationCacheEvictionService)
            .should()
            .evictAll();
    }

    @Test
    @DisplayName("운영 중지된 숙소의 객실 활성화는 거절되고 DB 상태가 유지된다")
    void rejectedActivationKeepsRoomInactive() {
        // given
        Accommodation accommodation = createAccommodation();
        deactivateAccommodation(accommodation);
        accommodationRepository.save(accommodation);
        Room room = roomRepository.save(createRoom(accommodation));

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() ->
            roomService.activateRoom(
                accommodation.getId(),
                room.getId()
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.ACCOMMODATION_INACTIVE);

        entityManager.clear();

        Room persistedRoom = roomRepository.findById(room.getId())
            .orElseThrow();
        assertThat(persistedRoom.getStatus())
            .isEqualTo(RoomStatus.INACTIVE);
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
            100_000L,
            2,
            2
        );
    }

    private void deactivateAccommodation(
        Accommodation accommodation
    ) {
        ReflectionTestUtils.setField(
            accommodation,
            "status",
            AccommodationStatus.INACTIVE
        );
    }
}
