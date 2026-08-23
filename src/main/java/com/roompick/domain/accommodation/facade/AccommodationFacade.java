package com.roompick.domain.accommodation.facade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationPageResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.exception.PopularAccommodationRankingUnavailableException;
import com.roompick.domain.accommodation.service.AccommodationElasticsearchLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationQueryService;
import com.roompick.domain.accommodation.service.PopularAccommodationRankingService;
import com.roompick.domain.accommodation.service.PopularAccommodationSingleFlightService;
import com.roompick.domain.accommodation.type.AccommodationLocationSearchEngine;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.service.RoomService;
import com.roompick.domain.timesale.service.TimeSalePriceService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 숙소와 객실 관련 API 흐름을 조율하는 Facade입니다.
 *
 * 여러 Service의 실행 순서를 조정하고,
 * 도메인 조회 결과를 API 응답 DTO로 변환합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccommodationFacade {

    private final AccommodationService accommodationService;

    /**
     * MySQL Bounding Box 기반 위치 검색을 담당합니다.
     */
    private final AccommodationLocationSearchService
        accommodationLocationSearchService;

    /**
     * Elasticsearch 기반 위치 검색을 담당합니다.
     */
    private final ObjectProvider<AccommodationElasticsearchLocationSearchService>
        accommodationElasticsearchLocationSearchServiceProvider;

    private AccommodationElasticsearchLocationSearchService
        accommodationElasticsearchLocationSearchService;

    private final RoomService roomService;

    private final PopularAccommodationRankingService
        popularAccommodationRankingService;

    private final PopularAccommodationSingleFlightService
        popularAccommodationSingleFlightService;

    private final PopularAccommodationQueryService
        popularAccommodationQueryService;

    private final TimeSalePriceService
        timeSalePriceService;
    /**
     * 위치 기반 숙소 검색에서 사용할 검색 엔진입니다.
     *
     * application 설정에 따라
     * MySQL 또는 Elasticsearch 검색 경로를 선택합니다.
     */
    @Value("${roompick.search.location-engine:MYSQL}")
    private AccommodationLocationSearchEngine locationSearchEngine;

    /**
     * 위치 검색 엔진 설정과 조건부 Elasticsearch Bean 구성을 검증합니다.
     *
     * ELASTICSEARCH가 선택된 경우 검색 Service를 애플리케이션 초기화 시
     * 한 번만 확인하고 보관하여 잘못된 배포 설정을 즉시 발견합니다.
     */
    @PostConstruct
    void initializeLocationSearchEngine() {
        if (locationSearchEngine !=
            AccommodationLocationSearchEngine.ELASTICSEARCH) {
            return;
        }

        accommodationElasticsearchLocationSearchService =
            accommodationElasticsearchLocationSearchServiceProvider
                .getIfAvailable();

        if (accommodationElasticsearchLocationSearchService == null) {
            throw new IllegalStateException(
                "위치 검색 엔진은 ELASTICSEARCH이지만 Elasticsearch 검색 Bean이 활성화되지 않았습니다."
            );
        }
    }

    /**
     * 운영 중인 숙소 목록 조회 흐름을 조율합니다.
     *
     * 페이지 요청값을 Service에 전달하고,
     * 조회 결과를 API 응답용 페이지 DTO로 변환합니다.
     */
    public AccommodationPageResponseDto getAccommodationList(
        int page,
        int size
    ) {
        Page<AccommodationListResponseDto> accommodationPage =
            accommodationService.findAllActive(
                page,
                size
            );

        return AccommodationPageResponseDto.from(
            accommodationPage
        );
    }

    /**
     * 사용자 위치를 기준으로 주변 숙소를 검색합니다.
     *
     * roompick.search.location-engine 설정에 따라
     * MySQL Bounding Box 검색 또는 Elasticsearch 검색을 선택합니다.
     *
     * Controller는 실제 검색 엔진 구현을 알 필요 없이
     * 항상 동일한 Facade 메서드만 호출합니다.
     */
    public List<AccommodationLocationSearchResponseDto>
    searchNearbyAccommodations(
        String keyword,
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        return switch (locationSearchEngine) {
            case MYSQL ->
                accommodationLocationSearchService.searchNearby(
                    keyword,
                    latitude,
                    longitude,
                    radiusKm,
                    limit
                );

            case ELASTICSEARCH ->
                accommodationElasticsearchLocationSearchService.searchNearby(
                    keyword,
                    latitude,
                    longitude,
                    radiusKm,
                    limit
                );
        };
    }

    /**
     * 인기 숙소 목록을 조회합니다.
     *
     * 동일한 캐시 키의 동시 요청은 Single Flight로 하나의 조회를 공유합니다.
     * Redis 인기 랭킹을 정상적으로 조회하면 실제 인기 순위를 반환하고,
     * Redis 장애가 발생하면 최신 ACTIVE 숙소를 임시 fallback으로 반환합니다.
     */
    public List<PopularAccommodationResponseDto>
    getPopularAccommodations(
        PopularAccommodationPeriod period,
        int limit
    ) {
        return popularAccommodationSingleFlightService.execute(
            period,
            limit,
            () -> getPopularAccommodationsWithFallback(
                period,
                limit
            )
        );
    }

    /**
     * 정상 인기 숙소 조회와 Redis 랭킹 장애 fallback을
     * 하나의 최종 작업으로 조율합니다.
     */
    private List<PopularAccommodationResponseDto>
    getPopularAccommodationsWithFallback(
        PopularAccommodationPeriod period,
        int limit
    ) {
        try {
            return popularAccommodationQueryService
                .getPopularAccommodations(
                    period,
                    limit
                );
        } catch (
            PopularAccommodationRankingUnavailableException exception
        ) {
            log.warn(
                "Redis 인기 숙소 랭킹 조회 실패로 최신 숙소 fallback을 반환합니다. period={}, limit={}",
                period,
                limit,
                exception
            );

            List<AccommodationListResponseDto>
                latestActiveAccommodations =
                accommodationService.findLatestActive(
                    limit
                );

            return createFallbackPopularAccommodations(
                latestActiveAccommodations
            );
        }
    }

    /**
     * 운영 중인 숙소의 기본 정보를 조회하고 조회 점수를 기록합니다.
     *
     * 숙소 조회와 응답 DTO 변환이 성공한 경우에만 인기 점수를 기록합니다.
     * Redis 기록에 실패하더라도 상세 조회 응답에는 영향을 주지 않습니다.
     *
     * 객실 목록은 별도의 숙소별 객실 목록 조회 API가 담당하므로
     * 숙소 상세 조회에서는 불필요한 객실 조회를 수행하지 않습니다.
     */
    public AccommodationDetailResponseDto getAccommodationDetail(
        Long accommodationId,
        boolean admin
    ) {
        Accommodation accommodation =
            admin
                ? accommodationService.findAnyByIdWithImages(accommodationId)
                : accommodationService.findActiveByIdWithImages(accommodationId);

        AccommodationDetailResponseDto response =
            admin
                ? AccommodationDetailResponseDto.forAdmin(accommodation)
                : AccommodationDetailResponseDto.from(accommodation);

        popularAccommodationRankingService.recordView(
            accommodationId
        );

        return response;
    }

    /**
     * 운영 중인 숙소에 소속된 운영 중인 객실 목록을 조회합니다.
     *
     * 먼저 숙소의 존재 여부와 운영 상태를 확인하고,
     * 현재 적용되는 타임세일 가격을 계산해 응답합니다.
     */
    public List<RoomListResponseDto> getRoomList(
        Long accommodationId,
        boolean admin
    ) {
        if (admin) {
            accommodationService.findById(accommodationId);
        } else {
            accommodationService.findActiveById(accommodationId);
        }

        List<RoomListResponseDto> rooms =
            admin
                ? roomService.findAllSummaryByAccommodationIdForAdmin(accommodationId)
                : roomService.findAllActiveSummaryByAccommodationId(accommodationId);

        Map<Long, Long> appliedPrices =
            timeSalePriceService
                .calculateRoomListPrices(
                    accommodationId,
                    rooms
                );

        return rooms.stream()
            .map(room -> room.withAppliedPrice(
                appliedPrices.get(room.roomId())
            ))
            .toList();
    }

    /**
     * 최신 ACTIVE 숙소 목록을 인기 숙소 응답 형태로 변환합니다.
     *
     * 여기서 부여하는 순위는 실제 인기 순위가 아니라
     * fallback 응답 형식을 유지하기 위한 임시 순번입니다.
     */
    private List<PopularAccommodationResponseDto>
    createFallbackPopularAccommodations(
        List<AccommodationListResponseDto> accommodations
    ) {
        List<PopularAccommodationResponseDto> result =
            new ArrayList<>();

        for (
            AccommodationListResponseDto accommodation
            : accommodations
        ) {
            int temporaryRank = result.size() + 1;

            result.add(
                PopularAccommodationResponseDto.from(
                    temporaryRank,
                    accommodation
                )
            );
        }

        return result;
    }
}
