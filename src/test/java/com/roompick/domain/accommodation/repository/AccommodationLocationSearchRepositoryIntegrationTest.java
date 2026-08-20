package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationLocationBoundingBox;
import com.roompick.global.config.JpaConfig;
import com.roompick.testsupport.SharedMySqlTestContainer;

import jakarta.persistence.EntityManager;

/**
 * 실제 MySQL의 공간 함수를 사용해
 * Bounding Box 기반 위치 검색 쿼리를 검증하는 통합 테스트입니다.
 *
 * H2가 아닌 MySQL 8.4에서
 * 위도/경도 Bounding Box 선필터링과
 * ST_Distance_Sphere() 정확 거리 계산을 함께 검증합니다.
 */
@Tag("integration")
@DataJpaTest(
    properties = {
        /*
         * 실제 운영 스키마와 동일하게 Flyway V1~V11을 적용합니다.
         *
         * 위치 검색 Repository는 V11에서 생성한
         * latitude / longitude 복합 인덱스를 사용하므로
         * Hibernate가 테스트 스키마를 임의 생성하지 않도록 합니다.
         */
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@AutoConfigureTestDatabase(
    replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(JpaConfig.class)
class AccommodationLocationSearchRepositoryIntegrationTest {

    private static final double SEOUL_CITY_HALL_LATITUDE = 37.566500;
    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.978000;

    private static final String DATABASE_NAME =
        "roompick_location_search_test";

    @DynamicPropertySource
    static void registerMySqlProperties(
        DynamicPropertyRegistry registry
    ) {
        SharedMySqlTestContainer.createDatabaseIfAbsent(DATABASE_NAME);
        registry.add(
            "spring.datasource.url",
            () -> SharedMySqlTestContainer.jdbcUrl(DATABASE_NAME)
        );
        registry.add(
            "spring.datasource.username",
            () -> SharedMySqlTestContainer.USERNAME
        );
        registry.add(
            "spring.datasource.password",
            () -> SharedMySqlTestContainer.PASSWORD
        );
        registry.add(
            "spring.datasource.driver-class-name",
            () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private AccommodationLocationSearchRepository
        accommodationLocationSearchRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName(
        "Bounding Box 후보 중 실제 반경 안의 ACTIVE 숙소만 거리순으로 조회한다"
    )
    void searchNearbyByRadiusAndDistance() {
        // given
        Accommodation nearbyAccommodation =
            saveAccommodation(
                "시청 룸픽 호텔",
                "서울특별시 중구 세종대로",
                "37.565800",
                "126.978500"
            );

        Accommodation fartherAccommodation =
            saveAccommodation(
                "명동 룸픽 호텔",
                "서울특별시 중구 명동",
                "37.560900",
                "126.986000"
            );

        /*
         * Bounding Box의 사각형 안에는 포함되지만
         * 실제 2km 원형 검색 반경 밖에 위치하도록 생성합니다.
         *
         * 따라서 Bounding Box만으로 검색을 끝내면 잘못 포함되지만,
         * ST_Distance_Sphere() 최종 검증에서는 제외되어야 합니다.
         */
        Accommodation boundingBoxCornerAccommodation =
            saveAccommodation(
                "바운딩 박스 모서리 호텔",
                "서울특별시 중구",
                "37.581500",
                "126.997000"
            );

        /*
         * Bounding Box 자체에서도 벗어나는 먼 숙소입니다.
         */
        Accommodation outsideAccommodation =
            saveAccommodation(
                "강남 룸픽 호텔",
                "서울특별시 강남구",
                "37.497900",
                "127.027600"
            );

        Accommodation inactiveAccommodation =
            saveAccommodation(
                "비공개 시청 호텔",
                "서울특별시 중구",
                "37.566000",
                "126.977500"
            );

        inactiveAccommodation.inactivate();

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

        // when
        List<AccommodationLocationSearchProjection> result =
            searchNearby(
                null,
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        // then
        assertThat(result)
            .hasSize(2);

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(
                nearbyAccommodation.getId(),
                fartherAccommodation.getId()
            );

        assertThat(result.get(0).getDistanceMeters())
            .isPositive();

        assertThat(result.get(1).getDistanceMeters())
            .isGreaterThan(
                result.get(0).getDistanceMeters()
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .doesNotContain(
                boundingBoxCornerAccommodation.getId(),
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
        // given
        Accommodation nameMatchedAccommodation =
            saveAccommodation(
                "룸픽 시청 호텔",
                "서울특별시 중구 세종대로",
                "37.565800",
                "126.978500"
            );

        Accommodation addressMatchedAccommodation =
            saveAccommodation(
                "서울 중앙 숙소",
                "서울특별시 중구 룸픽거리",
                "37.561500",
                "126.982000"
            );

        Accommodation unmatchedAccommodation =
            saveAccommodation(
                "서울 일반 호텔",
                "서울특별시 중구 을지로",
                "37.563000",
                "126.980000"
            );

        entityManager.flush();
        entityManager.clear();

        // when
        List<AccommodationLocationSearchProjection> result =
            searchNearby(
                "룸픽",
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        // then
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
    @DisplayName("keyword의 %는 LIKE wildcard가 아니라 일반 문자로 검색한다")
    void searchNearbyWithPercentKeyword() {
        Accommodation literalMatched =
            saveAccommodation(
                "할인 20% 호텔",
                "서울특별시 중구",
                "37.565800",
                "126.978500"
            );

        Accommodation wildcardOnlyMatched =
            saveAccommodation(
                "일반 호텔",
                "서울특별시 중구",
                "37.560900",
                "126.986000"
            );

        entityManager.flush();
        entityManager.clear();

        List<AccommodationLocationSearchProjection> result =
            searchNearby(
                "%",
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(literalMatched.getId())
            .doesNotContain(wildcardOnlyMatched.getId());
    }

    @Test
    @DisplayName("keyword의 _는 LIKE wildcard가 아니라 일반 문자로 검색한다")
    void searchNearbyWithUnderscoreKeyword() {
        Accommodation literalMatched =
            saveAccommodation(
                "ROOM_PICK 호텔",
                "서울특별시 중구",
                "37.565800",
                "126.978500"
            );

        Accommodation wildcardOnlyMatched =
            saveAccommodation(
                "ROOMXPICK 호텔",
                "서울특별시 중구",
                "37.560900",
                "126.986000"
            );

        entityManager.flush();
        entityManager.clear();

        List<AccommodationLocationSearchProjection> result =
            searchNearby(
                "_",
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(literalMatched.getId())
            .doesNotContain(wildcardOnlyMatched.getId());
    }

    @Test
    @DisplayName("LIKE escape 문자 자체도 일반 문자로 검색한다")
    void searchNearbyWithEscapeCharacterKeyword() {
        Accommodation literalMatched =
            saveAccommodation(
                "느낌! 호텔",
                "서울특별시 중구",
                "37.565800",
                "126.978500"
            );

        Accommodation unmatched =
            saveAccommodation(
                "느낌 좋은 호텔",
                "서울특별시 중구",
                "37.560900",
                "126.986000"
            );

        entityManager.flush();
        entityManager.clear();

        List<AccommodationLocationSearchProjection> result =
            searchNearby(
                "!",
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                10
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchProjection::getAccommodationId
            )
            .containsExactly(literalMatched.getId())
            .doesNotContain(unmatched.getId());
    }

    @Test
    @DisplayName(
        "위치 검색 결과는 요청한 limit만큼만 반환한다"
    )
    void searchNearbyWithLimit() {
        // given
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

        // when
        List<AccommodationLocationSearchProjection> result =
            searchNearby(
                null,
                SEOUL_CITY_HALL_LATITUDE,
                SEOUL_CITY_HALL_LONGITUDE,
                2.0,
                limit
            );

        // then
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
     * 실제 Service와 동일한 방식으로 Bounding Box를 계산한 뒤
     * Repository 위치 검색 쿼리를 실행합니다.
     */
    private List<AccommodationLocationSearchProjection> searchNearby(
        String keyword,
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        AccommodationLocationBoundingBox boundingBox =
            AccommodationLocationBoundingBox.calculate(
                latitude,
                longitude,
                radiusKm
            );

        return accommodationLocationSearchRepository.searchNearby(
            keyword,
            latitude,
            longitude,
            radiusKm,
            boundingBox.minLatitude(),
            boundingBox.maxLatitude(),
            boundingBox.minLongitude(),
            boundingBox.maxLongitude(),
            limit
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
