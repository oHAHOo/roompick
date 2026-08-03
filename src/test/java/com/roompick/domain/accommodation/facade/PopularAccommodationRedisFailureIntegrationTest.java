package com.roompick.domain.accommodation.facade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;

import jakarta.persistence.EntityManager;

/**
 * Redis에 연결할 수 없는 실제 장애 상황에서
 * 숙소 조회 API 흐름이 유지되는지 검증합니다.
 *
 * 인기 숙소 조회는 최신 ACTIVE 숙소를 임시 fallback으로 반환하고,
 * 상세 조회는 인기 점수 기록 실패와 관계없이 정상 응답해야 합니다.
 */
@Tag("integration")
@SpringBootTest(
    properties = {
        /*
         * 사용 중인 Redis와 우연히 연결되지 않도록
         * 테스트에서 접근할 수 없는 포트를 지정합니다.
         */
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",

        /*
         * Redis 장애 테스트가 오래 대기하지 않도록
         * 연결 및 명령 제한 시간을 짧게 설정합니다.
         */
        "spring.data.redis.connect-timeout=200ms",
        "spring.data.redis.timeout=200ms"
    }
)
@ActiveProfiles("test")
class PopularAccommodationRedisFailureIntegrationTest {

    @Autowired
    private AccommodationFacade accommodationFacade;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 테스트에서 저장한 숙소가
     * 다른 테스트에 영향을 주지 않도록 삭제합니다.
     */
    @AfterEach
    void tearDown() {
        accommodationRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName(
        "Redis 인기 랭킹 조회에 실패하면 "
            + "최신 ACTIVE 숙소를 DB fallback으로 반환한다"
    )
    void returnDatabaseFallbackWhenRedisIsUnavailable() {
        // given: 오래된 ACTIVE 숙소를 먼저 저장합니다.
        Accommodation olderActiveAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "먼저 등록된 룸픽 호텔",
                    "서울특별시 중구",
                    "먼저 등록된 운영 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        /*
         * 나중에 저장된 ACTIVE 숙소입니다.
         *
         * 생성 시각이 같더라도 ID 내림차순 조건으로
         * 이 숙소가 먼저 반환되어야 합니다.
         */
        Accommodation newerActiveAccommodation =
            accommodationRepository.save(
                Accommodation.create(
                    "최근 등록된 룸픽 호텔",
                    "서울특별시 강남구",
                    "최근 등록된 운영 숙소입니다.",
                    LocalTime.of(16, 0),
                    LocalTime.of(10, 0)
                )
            );

        /*
         * 가장 마지막에 저장되지만 INACTIVE 상태인 숙소입니다.
         *
         * 최신 숙소여도 fallback 결과에서는
         * 제외되어야 합니다.
         */
        Accommodation inactiveAccommodation =
            Accommodation.create(
                "최근 비공개 룸픽 호텔",
                "서울특별시 송파구",
                "운영 중단된 숙소입니다.",
                LocalTime.of(15, 0),
                LocalTime.of(11, 0)
            );

        inactiveAccommodation.inactivate();

        /*
         * 마지막 숙소까지 즉시 DB에 반영합니다.
         *
         * saveAndFlush()가 Repository 트랜잭션 안에서
         * 저장과 flush를 함께 처리합니다.
         */
        accommodationRepository.saveAndFlush(
            inactiveAccommodation
        );

        int limit = 2;

        // when: Redis가 연결되지 않는 상태에서 인기 숙소를 조회합니다.
        List<PopularAccommodationResponseDto> result =
            accommodationFacade.getPopularAccommodations(
                PopularAccommodationPeriod.DAILY,
                limit
            );

        // then: API 흐름이 실패하지 않고 두 건을 반환합니다.
        assertThat(result)
            .hasSize(limit);

        /*
         * fallback의 rank는 실제 인기 순위가 아니라
         * 응답 형식을 유지하기 위한 임시 순번입니다.
         */
        assertThat(result.get(0).rank())
            .isEqualTo(1);

        assertThat(result.get(0).accommodationId())
            .isEqualTo(
                newerActiveAccommodation.getId()
            );

        assertThat(result.get(0).name())
            .isEqualTo(
                "최근 등록된 룸픽 호텔"
            );

        assertThat(result.get(1).rank())
            .isEqualTo(2);

        assertThat(result.get(1).accommodationId())
            .isEqualTo(
                olderActiveAccommodation.getId()
            );

        /*
         * 운영 중단된 숙소는 최신 데이터여도
         * fallback 응답에 포함되지 않아야 합니다.
         */
        assertThat(result)
            .extracting(
                PopularAccommodationResponseDto::accommodationId
            )
            .doesNotContain(
                inactiveAccommodation.getId()
            );
    }

    @Test
    @DisplayName(
        "Redis 인기 점수 기록에 실패해도 "
            + "숙소 상세 조회는 정상적으로 성공한다"
    )
    void returnAccommodationDetailWhenRedisIsUnavailable() {
        // given: 상세 조회할 ACTIVE 숙소를 저장합니다.
        Accommodation accommodation =
            accommodationRepository.saveAndFlush(
                Accommodation.create(
                    "룸픽 상세 호텔",
                    "서울특별시 종로구",
                    "Redis 장애 상세 조회 테스트 숙소입니다.",
                    LocalTime.of(15, 0),
                    LocalTime.of(11, 0)
                )
            );

        Long accommodationId =
            accommodation.getId();

        entityManager.clear();

        /*
         * when:
         * 숙소 DB 조회와 응답 DTO 생성은 성공하지만,
         * 이후 Redis 인기 점수 기록은 연결 장애로 실패합니다.
         */
        AccommodationDetailResponseDto response =
            accommodationFacade.getAccommodationDetail(
                accommodationId
            );

        /*
         * then:
         * Redis 점수 기록 실패가 외부로 전달되지 않고
         * 숙소 상세 정보가 정상적으로 반환됩니다.
         */
        assertThat(response.accommodationId())
            .isEqualTo(accommodationId);

        assertThat(response.name())
            .isEqualTo(
                "룸픽 상세 호텔"
            );

        assertThat(response.address())
            .isEqualTo(
                "서울특별시 종로구"
            );

        assertThat(response.description())
            .isEqualTo(
                "Redis 장애 상세 조회 테스트 숙소입니다."
            );
    }
}
