package com.roompick.domain.accommodation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.accommodation.service.AccommodationElasticsearchLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationSearchReindexService;

/**
 * MySQL 숙소 데이터를 Elasticsearch에 재색인한 뒤
 * 실제 geo_distance 위치 검색을 검증하는 통합 테스트입니다.
 *
 * MySQL과 Elasticsearch를 모두 Testcontainers로 실행하여
 * 로컬 환경에 설치된 DB나 검색 엔진에 의존하지 않습니다.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "roompick.search.location-engine=ELASTICSEARCH"
    }
)
class AccommodationElasticsearchLocationSearchIntegrationTest {

    private static final double SEOUL_CITY_HALL_LATITUDE =
        37.566500;

    private static final double SEOUL_CITY_HALL_LONGITUDE =
        126.978000;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
        new MySQLContainer<>(
            "mysql:8.4"
        )
            .withDatabaseName(
                "roompick_elasticsearch_search_test"
            )
            .withUsername(
                "roompick"
            )
            .withPassword(
                "roompick-password"
            )
            .withStartupTimeout(
                Duration.ofMinutes(2)
            );

    @Container
    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH =
        new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.18.1"
        )
            /*
             * 로컬 compose 환경과 동일하게
             * 테스트에서도 보안 기능을 비활성화합니다.
             */
            .withEnv(
                "xpack.security.enabled",
                "false"
            )
            .withStartupTimeout(
                Duration.ofMinutes(2)
            );

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private AccommodationSearchReindexService
        accommodationSearchReindexService;

    @Autowired
    private AccommodationElasticsearchLocationSearchService
        accommodationElasticsearchLocationSearchService;

    @BeforeEach
    void setUp() {
        /*
         * 각 테스트가 독립적인 MySQL 데이터 상태에서
         * 실행되도록 기존 숙소 데이터를 제거합니다.
         *
         * Elasticsearch 인덱스는 reindexAll()이
         * 매 테스트마다 새로 생성합니다.
         */
        accommodationRepository.deleteAll();
    }

    @Test
    void MySQL_숙소를_재색인한_뒤_반경과_거리순으로_검색한다() {
        // given: 서울시청 근처 숙소를 생성합니다.
        Accommodation nearest =
            createAccommodationWithLocation(
                "시청 룸픽 호텔",
                "서울특별시 중구",
                37.565800,
                126.978500
            );

        Accommodation farther =
            createAccommodationWithLocation(
                "명동 룸픽 호텔",
                "서울특별시 중구 명동",
                37.560900,
                126.986000
            );

        /*
         * 좌표는 있지만 2km 반경 밖에 있는 숙소입니다.
         */
        createAccommodationWithLocation(
            "강남 룸픽 호텔",
            "서울특별시 강남구",
            37.497900,
            127.027600
        );

        /*
         * 좌표가 없는 기존 숙소는 Elasticsearch 위치 검색
         * 인덱스 대상에서 제외되어야 합니다.
         */
        accommodationRepository.save(
            Accommodation.create(
                "좌표 없는 숙소",
                "서울특별시 중구",
                "좌표가 없는 테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            )
        );

        // when: MySQL 데이터를 Elasticsearch에 전체 재색인합니다.
        long indexedCount =
            accommodationSearchReindexService.reindexAll();

        List<AccommodationLocationSearchResponseDto> result =
            accommodationElasticsearchLocationSearchService
                .searchNearby(
                    null,
                    SEOUL_CITY_HALL_LATITUDE,
                    SEOUL_CITY_HALL_LONGITUDE,
                    2.0,
                    20
                );

        // then
        /*
         * 좌표가 존재하는 숙소 세 개만
         * Elasticsearch에 재색인되어야 합니다.
         */
        assertThat(indexedCount)
            .isEqualTo(3L);

        /*
         * 2km 안의 숙소 두 개만 반환되고,
         * 서울시청에서 가까운 순서대로 정렬되어야 합니다.
         */
        assertThat(result)
            .extracting(
                AccommodationLocationSearchResponseDto::accommodationId
            )
            .containsExactly(
                nearest.getId(),
                farther.getId()
            );

        assertThat(result)
            .extracting(
                AccommodationLocationSearchResponseDto::name
            )
            .containsExactly(
                "시청 룸픽 호텔",
                "명동 룸픽 호텔"
            );

        assertThat(result.get(0).distanceKm())
            .isPositive();

        assertThat(result.get(1).distanceKm())
            .isGreaterThan(
                result.get(0).distanceKm()
            );
    }

    @Test
    void keyword가_있으면_숙소명과_주소를_함께_검색한다() {
        // given: 숙소명에 keyword가 포함된 숙소입니다.
        Accommodation nameMatched =
            createAccommodationWithLocation(
                "룸픽 시청 호텔",
                "서울특별시 중구 세종대로",
                37.565800,
                126.978500
            );

        /*
         * 숙소명에는 keyword가 없지만
         * 주소에 별도 단어로 "룸픽"이 포함된 숙소입니다.
         */
        Accommodation addressMatched =
            createAccommodationWithLocation(
                "서울 중앙 숙소",
                "서울특별시 중구 룸픽 거리",
                37.560900,
                126.986000
            );

        /*
         * 반경 안에 있지만 keyword가 없는 숙소입니다.
         */
        createAccommodationWithLocation(
            "서울 테스트 호텔",
            "서울특별시 중구 테스트 거리",
            37.564000,
            126.980000
        );

        accommodationSearchReindexService.reindexAll();

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationElasticsearchLocationSearchService
                .searchNearby(
                    "룸픽",
                    SEOUL_CITY_HALL_LATITUDE,
                    SEOUL_CITY_HALL_LONGITUDE,
                    2.0,
                    20
                );

        // then
        /*
         * name 또는 address 중 하나에 keyword가 매칭되는
         * 숙소만 반환되어야 합니다.
         */
        assertThat(result)
            .extracting(
                AccommodationLocationSearchResponseDto::accommodationId
            )
            .containsExactly(
                nameMatched.getId(),
                addressMatched.getId()
            );
    }

    @Test
    void limit만큼만_가까운_숙소를_반환한다() {
        // given
        Accommodation first =
            createAccommodationWithLocation(
                "첫 번째 숙소",
                "서울특별시 중구",
                37.566000,
                126.978000
            );

        Accommodation second =
            createAccommodationWithLocation(
                "두 번째 숙소",
                "서울특별시 중구",
                37.565000,
                126.978000
            );

        createAccommodationWithLocation(
            "세 번째 숙소",
            "서울특별시 중구",
            37.564000,
            126.978000
        );

        accommodationSearchReindexService.reindexAll();

        // when
        List<AccommodationLocationSearchResponseDto> result =
            accommodationElasticsearchLocationSearchService
                .searchNearby(
                    null,
                    SEOUL_CITY_HALL_LATITUDE,
                    SEOUL_CITY_HALL_LONGITUDE,
                    5.0,
                    2
                );

        // then
        assertThat(result)
            .hasSize(2)
            .extracting(
                AccommodationLocationSearchResponseDto::accommodationId
            )
            .containsExactly(
                first.getId(),
                second.getId()
            );
    }

    /**
     * 좌표가 존재하는 ACTIVE 숙소를 생성합니다.
     */
    private Accommodation createAccommodationWithLocation(
        String name,
        String address,
        double latitude,
        double longitude
    ) {
        Accommodation accommodation =
            Accommodation.create(
                name,
                address,
                "Elasticsearch 위치 검색 통합 테스트 숙소",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        accommodation.updateLocation(
            BigDecimal.valueOf(latitude),
            BigDecimal.valueOf(longitude)
        );

        return accommodationRepository.save(
            accommodation
        );
    }
}
