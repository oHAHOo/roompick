package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.global.config.JpaConfig;

import jakarta.persistence.EntityManager;

/**
 * AccommodationRepository의 숙소 조회 쿼리를 검증하는 JPA 테스트입니다.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(JpaConfig.class)
class AccommodationRepositoryTest {

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("숙소 ID 목록 중 ACTIVE 숙소의 공개 요약 정보만 조회한다")
    void findAllActiveSummaryByIdIn() {
        // given
        Accommodation activeAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "룸픽 서울 호텔",
                    "서울특별시 중구",
                    "운영 중인 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Accommodation inactiveAccommodation =
            Accommodation.create(
                "룸픽 비공개 호텔",
                "서울특별시 강남구",
                "운영 중지된 숙소입니다.",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        /*
         * 현재 숙소 Entity에는 비활성화 메서드가 없으므로
         * Repository 조회 조건 테스트를 위해 상태만 변경합니다.
         */
        ReflectionTestUtils.setField(
            inactiveAccommodation,
            "status",
            AccommodationStatus.INACTIVE
        );

        accommodationRepository.save(
            inactiveAccommodation
        );

        entityManager.flush();
        entityManager.clear();

        List<Long> accommodationIds =
            List.of(
                activeAccommodation.getId(),
                inactiveAccommodation.getId()
            );

        // when
        List<AccommodationListResponseDto> result =
            accommodationRepository
                .findAllActiveSummaryByIdIn(
                    accommodationIds
                );

        // then
        assertThat(result).hasSize(
            1
        );

        assertThat(result.get(0).accommodationId())
            .isEqualTo(
                activeAccommodation.getId()
            );

        assertThat(result.get(0).name())
            .isEqualTo(
                "룸픽 서울 호텔"
            );

        assertThat(result.get(0).address())
            .isEqualTo(
                "서울특별시 중구"
            );
    }
}
