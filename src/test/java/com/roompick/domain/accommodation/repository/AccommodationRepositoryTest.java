package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
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
    @DisplayName(
        "숙소 ID 목록 중 ACTIVE 숙소의 "
            + "공개 요약 정보만 조회한다"
    )
    void findAllActiveSummaryByIdIn() {
        // given: 운영 중인 숙소를 저장합니다.
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

        /*
         * 운영 중단된 숙소도 함께 저장합니다.
         *
         * Repository 쿼리가 ACTIVE 상태만 반환하는지
         * 확인하기 위한 테스트 데이터입니다.
         */
        Accommodation inactiveAccommodation =
            Accommodation.create(
                "룸픽 비공개 호텔",
                "서울특별시 강남구",
                "운영 중지된 숙소입니다.",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        inactiveAccommodation.inactivate();

        accommodationRepository.save(
            inactiveAccommodation
        );

        /*
         * 저장 내용을 DB에 반영한 후 영속성 컨텍스트를 비워
         * Repository 쿼리 결과를 실제 DB 기준으로 검증합니다.
         */
        entityManager.flush();
        entityManager.clear();

        List<Long> accommodationIds =
            List.of(
                activeAccommodation.getId(),
                inactiveAccommodation.getId()
            );

        // when: 전달한 ID에 해당하는 ACTIVE 숙소를 조회합니다.
        List<AccommodationListResponseDto> result =
            accommodationRepository
                .findAllActiveSummaryByIdIn(
                    accommodationIds
                );

        // then: 운영 중인 숙소 한 건만 반환됩니다.
        assertThat(result)
            .hasSize(1);

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

    @Test
    @DisplayName(
        "Redis 장애 fallback은 최신 ACTIVE 숙소를 "
            + "생성일과 ID 내림차순으로 limit만큼 조회한다"
    )
    void findLatestActive() {
        // given: 가장 오래된 운영 숙소를 저장합니다.
        Accommodation oldestActiveAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "오래된 룸픽 호텔",
                    "서울특별시 중구",
                    "가장 오래된 운영 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        /*
         * 생성 시각이 같은 두 운영 숙소를 저장합니다.
         *
         * 생성 시각이 같을 경우 ID가 큰 숙소가
         * 먼저 반환되는지 확인합니다.
         */
        Accommodation sameTimeLowerIdAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "동일 시각 첫 번째 호텔",
                    "서울특별시 종로구",
                    "동일 생성 시각의 첫 번째 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Accommodation sameTimeHigherIdAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "동일 시각 두 번째 호텔",
                    "서울특별시 송파구",
                    "동일 생성 시각의 두 번째 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        /*
         * 가장 최근에 등록된 숙소지만 INACTIVE 상태로 저장합니다.
         *
         * 생성 시각이 가장 최신이어도 fallback 결과에서는
         * 제외되어야 합니다.
         */
        Accommodation newestInactiveAccommodation =
            Accommodation.create(
                "최신 비공개 호텔",
                "서울특별시 강남구",
                "가장 최근이지만 운영 중단된 숙소입니다.",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        newestInactiveAccommodation.inactivate();

        newestInactiveAccommodation =
            accommodationRepository.save(
                newestInactiveAccommodation
            );

        entityManager.flush();

        /*
         * JPA Auditing이 자동 생성한 시각 대신
         * 테스트에서 의도한 생성 시각을 DB에 직접 설정합니다.
         *
         * 이렇게 하면 테스트 실행 속도에 의존하지 않고
         * 정렬 조건을 안정적으로 검증할 수 있습니다.
         */
        updateCreatedAt(
            oldestActiveAccommodation.getId(),
            LocalDateTime.of(
                2026,
                7,
                28,
                10,
                0
            )
        );

        LocalDateTime sameCreatedAt =
            LocalDateTime.of(
                2026,
                7,
                29,
                10,
                0
            );

        updateCreatedAt(
            sameTimeLowerIdAccommodation.getId(),
            sameCreatedAt
        );

        updateCreatedAt(
            sameTimeHigherIdAccommodation.getId(),
            sameCreatedAt
        );

        updateCreatedAt(
            newestInactiveAccommodation.getId(),
            LocalDateTime.of(
                2026,
                7,
                30,
                10,
                0
            )
        );

        /*
         * Native Query로 변경한 생성 시각을 기준으로
         * 다시 조회하도록 영속성 컨텍스트를 초기화합니다.
         */
        entityManager.clear();

        int limit = 2;

        // when: Redis 장애 시 사용할 최신 ACTIVE 숙소를 조회합니다.
        List<AccommodationListResponseDto> result =
            accommodationRepository.findLatestActive(
                PageRequest.of(
                    0,
                    limit
                )
            );

        // then: 요청한 limit만큼만 반환됩니다.
        assertThat(result)
            .hasSize(limit);

        /*
         * 가장 최신인 INACTIVE 숙소는 제외됩니다.
         *
         * 남은 ACTIVE 숙소 중 생성 시각이 같은 경우에는
         * ID가 큰 숙소가 먼저 반환됩니다.
         */
        assertThat(result)
            .extracting(
                AccommodationListResponseDto::accommodationId
            )
            .containsExactly(
                sameTimeHigherIdAccommodation.getId(),
                sameTimeLowerIdAccommodation.getId()
            );

        assertThat(result)
            .extracting(
                AccommodationListResponseDto::name
            )
            .containsExactly(
                "동일 시각 두 번째 호텔",
                "동일 시각 첫 번째 호텔"
            );

        /*
         * 오래된 ACTIVE 숙소는 limit 때문에 제외되고,
         * 최신 INACTIVE 숙소는 상태 조건 때문에 제외됩니다.
         */
        assertThat(result)
            .extracting(
                AccommodationListResponseDto::accommodationId
            )
            .doesNotContain(
                oldestActiveAccommodation.getId(),
                newestInactiveAccommodation.getId()
            );
    }

    @Test
    @DisplayName(
        "숙소 ID 목록 조회는 대표(0번) 이미지를 LEFT JOIN으로 "
            + "함께 조회하고, 이미지가 없으면 imageUrl은 null이다"
    )
    void findAllActiveSummaryByIdIn_대표_이미지를_함께_조회한다() {
        // given
        Accommodation withImage =
            accommodationRepository.save(
                Accommodation.create(
                    "이미지 있는 호텔",
                    "서울특별시 중구",
                    "설명",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );
        withImage.addImages(
            List.of(
                "https://example.com/a.jpg",
                "https://example.com/b.jpg"
            )
        );
        accommodationRepository.save(withImage);

        Accommodation withoutImage =
            accommodationRepository.save(
                Accommodation.create(
                    "이미지 없는 호텔",
                    "서울특별시 강남구",
                    "설명",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        entityManager.flush();
        entityManager.clear();

        // when
        List<AccommodationListResponseDto> result =
            accommodationRepository.findAllActiveSummaryByIdIn(
                List.of(withImage.getId(), withoutImage.getId())
            );

        // then: 대표(0번) 이미지 URL만 반환되고, LEFT JOIN으로 행이 늘어나지 않는다.
        assertThat(result).hasSize(2);

        assertThat(result)
            .filteredOn(dto -> dto.accommodationId().equals(withImage.getId()))
            .extracting(AccommodationListResponseDto::imageUrl)
            .containsExactly("https://example.com/a.jpg");

        assertThat(result)
            .filteredOn(dto -> dto.accommodationId().equals(withoutImage.getId()))
            .extracting(AccommodationListResponseDto::imageUrl)
            .containsExactly((String) null);
    }

    @Test
    @DisplayName("숙소 상세 조회는 이미지 컬렉션을 fetch join으로 함께 초기화한다")
    void findByIdWithImages_이미지를_함께_로딩한다() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "룸픽 호텔",
                    "서울특별시 중구",
                    "설명",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );
        accommodation.addImages(List.of("https://example.com/a.jpg"));
        accommodationRepository.save(accommodation);

        entityManager.flush();
        entityManager.clear();

        // when
        Accommodation found = accommodationRepository
            .findByIdWithImages(accommodation.getId())
            .orElseThrow();

        // then
        boolean imagesLoaded = jakarta.persistence.Persistence.getPersistenceUtil()
            .isLoaded(found, "images");
        assertThat(imagesLoaded).isTrue();
        assertThat(found.getImages()).hasSize(1);
    }

    @Test
    @DisplayName("숙소 ID 조회는 이미지 컬렉션을 초기화하지 않는다")
    void findById_이미지를_로딩하지_않는다() {
        // given
        Accommodation accommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "룸픽 호텔",
                    "서울특별시 중구",
                    "설명",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        entityManager.flush();
        entityManager.clear();

        // when
        Accommodation found = accommodationRepository
            .findById(accommodation.getId())
            .orElseThrow();

        // then: 상태 확인 등 이미지가 필요 없는 조회 경로까지
        // 이미지 로딩을 강제하지 않는다.
        boolean imagesLoaded = jakarta.persistence.Persistence.getPersistenceUtil()
            .isLoaded(found, "images");
        assertThat(imagesLoaded).isFalse();
    }

    /**
     * fallback 정렬 테스트에서 사용할 생성 시각을
     * DB에 직접 설정합니다.
     */
    private void updateCreatedAt(
        Long accommodationId,
        LocalDateTime createdAt
    ) {
        entityManager.createNativeQuery(
                """
                UPDATE accommodations
                SET created_at = :createdAt
                WHERE accommodation_id = :accommodationId
                """
            )
            .setParameter(
                "createdAt",
                createdAt
            )
            .setParameter(
                "accommodationId",
                accommodationId
            )
            .executeUpdate();
    }
}
