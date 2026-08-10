package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.global.config.JpaConfig;

import jakarta.persistence.EntityManager;

/**
 * 실제 MySQL의 공간 함수를 사용해
 * 위치 기반 숙소 검색 쿼리를 검증하는 통합 테스트입니다.
 *
 * H2가 아닌 MySQL 8.4에서 ST_Distance_Sphere()와 POINT()를 실행하여
 * 실제 운영 DB와 같은 거리 계산 동작을 확인합니다.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
    }
)
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaConfig.class)
class AccommodationLocationSearchRepositoryIntegrationTest {

    private static final double SEOUL_CITY_HALL_LATITUDE = 37.566500;
    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.978000;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL_CONTAINER =
        new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4")
        )
            .withDatabaseName("roompick_location_search_test")
            .withUsername("roompick")
            .withPassword("roompick-password")
            .withStartupTimeout(Duration.ofMinutes(2));

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private AccommodationLocationSearchRepository
        accommodationLocationSearchRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName(
        "지정 반경 안의 ACTIVE 숙소만 거리순으로 조회한다"
    )
    void searchNearbyByRadiusAndDistance() {
        // given: 서울시청과 가까운 숙소를 저장합니다.
        Accommodation nearbyAccommodation =
            saveAccommodation(
                "시청 룸픽 호텔",
                "서울특별시 중구 세종대로",
                "37.565800",
                "126.978500"
            );

        /*
         * 첫 번째 숙소보다 조금 더 떨어진 위치의
         * 운영 중인 숙소를 저장합니다.
         */
        Accommodation fartherAccommodation =
            saveAccommodation(
                "명동 룸픽 호텔",
                "서울특별시 중구 명동",
                "37.560900",
                "126.986000"
            );

        /*
         * 검색 반경 밖에 위치한 숙소를 저장합니다.
         *
         * 위치 좌표는 존재하지만 반경 조건 때문에
         * 검색 결과에서 제외되어야 합니다.
         */
        Accommodation outsideAccommodation =
            saveAccommodation(
                "강남 룸픽 호텔",
                "서울특별시 강남구",
                "37.497900",
                "127.027600"
            );

        /*
         * 검색 중심과 가까운 위치지만 INACTIVE 상태인 숙소를
         * 저장하여 상태 조건도 함께 검증합니다.
         */
        Accommodation inactiveAccommodation =
            saveAccommodation(
                "비공개 시청 호텔",
                "서울특별시 중구",
                "37.566000",
                "126.977500"
            );

        inactiveAccommodation.inactivate();

        /*
         * 위치 정보가 없는 기존 숙소도 저장합니다.
         *
         * latitude와 longitude가 null인 숙소는
         * 위치 검색 대상에서 제외되어야 합니다.
         */
        Accommodation noLocationAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "좌표 없는 호텔",
                    "서울특별시 중구",
                    "위치 좌표가 없는 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        entityManager.flush();
        entityManager.clear();

        // when: 서울시청 기준 반경 2km 안의 숙소를 검색합니다.
        List<AccommodationLocationSearchProjection> result =
            accommodationLocationSearchRepository.searchNearby(
                null,
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        // then: 반경 안의 ACTIVE 숙소 두 건만 반환됩니다.
        assertThat(result)
            .hasSize(2);

        /*
         * 검색 중심과 가까운 숙소부터
         * 거리 오름차순으로 반환되어야 합니다.
         */
        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(
                nearbyAccommodation.getId(),
                fartherAccommodation.getId()
            );

        /*
         * 각 결과에는 MySQL이 계산한 실제 거리 값이
         * 함께 반환되어야 합니다.
         */
        assertThat(result.get(0).getDistanceMeters())
            .isPositive();

        assertThat(result.get(1).getDistanceMeters())
            .isGreaterThan(
                result.get(0).getDistanceMeters()
            );

        /*
         * 반경 밖 숙소, 비공개 숙소,
         * 좌표가 없는 숙소는 모두 제외됩니다.
         */
        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .doesNotContain(
                outsideAccommodation.getId(),
                inactiveAccommodation.getId(),
                noLocationAccommodation.getId()
            );
    }

    @Test
    @DisplayName(
        "위치 조건과 숙소명 또는 주소 keyword 조건을 함께 적용한다"
    )
    void searchNearbyWithKeyword() {
        // given: 검색어가 숙소명에 포함되는 숙소를 저장합니다.
        Accommodation nameMatchedAccommodation =
            saveAccommodation(
                "룸픽 시청 호텔",
                "서울특별시 중구 세종대로",
                "37.565800",
                "126.978500"
            );

        /*
         * 숙소명에는 검색어가 없지만
         * 주소에 검색어가 포함되는 숙소를 저장합니다.
         */
        Accommodation addressMatchedAccommodation =
            saveAccommodation(
                "서울 중앙 숙소",
                "서울특별시 중구 룸픽거리",
                "37.561500",
                "126.982000"
            );

        /*
         * 같은 검색 반경 안에 있지만
         * 숙소명과 주소 어디에도 keyword가 없는 숙소입니다.
         */
        Accommodation unmatchedAccommodation =
            saveAccommodation(
                "서울 일반 호텔",
                "서울특별시 중구 을지로",
                "37.563000",
                "126.980000"
            );

        entityManager.flush();
        entityManager.clear();

        // when: 위치 조건과 "룸픽" keyword를 함께 검색합니다.
        List<AccommodationLocationSearchProjection> result =
            accommodationLocationSearchRepository.searchNearby(
                "룸픽",
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        // then: 이름 또는 주소에 keyword가 포함된 숙소만 반환됩니다.
        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(
                nameMatchedAccommodation.getId(),
                addressMatchedAccommodation.getId()
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .doesNotContain(
                unmatchedAccommodation.getId()
            );
    }

    @Test
    @DisplayName(
        "위치 검색 결과는 요청한 limit만큼만 반환한다"
    )
    void searchNearbyWithLimit() {
        // given: 모두 검색 반경 안에 있는 숙소 세 건을 저장합니다.
        Accommodation firstAccommodation =
            saveAccommodation(
                "첫 번째 호텔",
                "서울특별시 중구",
                "37.566000",
                "126.978000"
            );

        Accommodation secondAccommodation =
            saveAccommodation(
                "두 번째 호텔",
                "서울특별시 중구",
                "37.565000",
                "126.978000"
            );

        Accommodation thirdAccommodation =
            saveAccommodation(
                "세 번째 호텔",
                "서울특별시 중구",
                "37.564000",
                "126.978000"
            );

        entityManager.flush();
        entityManager.clear();

        int limit = 2;

        // when: 결과를 두 건으로 제한하여 검색합니다.
        List<AccommodationLocationSearchProjection> result =
            accommodationLocationSearchRepository.searchNearby(
                null,
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                limit
            );

        // then: 가까운 숙소부터 두 건만 반환됩니다.
        assertThat(result)
            .hasSize(limit);

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(
                firstAccommodation.getId(),
                secondAccommodation.getId()
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .doesNotContain(
                thirdAccommodation.getId()
            );
    }

    /**
     * 위치 검색 테스트에 사용할 ACTIVE 숙소를 생성하고 저장합니다.
     */
    private Accommodation saveAccommodation(
        String name,
        String address,
        String latitude,
        String longitude
    ) {
        Accommodation accommodation =
            Accommodation.create(
                name,
                address,
                "위치 검색 테스트 숙소입니다.",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        accommodation.updateLocation(
            new BigDecimal(latitude),
            new BigDecimal(longitude)
        );

        return accommodationRepository.save(
            accommodation
        );
    }
}
