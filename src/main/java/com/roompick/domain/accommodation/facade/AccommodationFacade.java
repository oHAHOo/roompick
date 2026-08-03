package com.roompick.domain.accommodation.facade;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationPageResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.exception.PopularAccommodationRankingUnavailableException;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationQueryService;
import com.roompick.domain.accommodation.service.PopularAccommodationRankingService;
import com.roompick.domain.accommodation.type.PopularAccommodationPeriod;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccommodationFacade {

    private final AccommodationService accommodationService;
    private final RoomService roomService;
    private final PopularAccommodationRankingService
        popularAccommodationRankingService;
    private final PopularAccommodationQueryService popularAccommodationQueryService;

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
     * 인기 숙소 목록을 조회합니다.
     *
     * Redis 인기 랭킹을 정상적으로 조회하면 실제 인기 순위를 반환하고,
     * Redis 장애가 발생하면 최신 ACTIVE 숙소를 임시 fallback으로 반환합니다.
     */
    public List<PopularAccommodationResponseDto>
    getPopularAccommodations(
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
            PopularAccommodationRankingUnavailableException
                exception
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
        Long accommodationId
    ) {
        Accommodation accommodation =
            accommodationService.findActiveById(
                accommodationId
            );

        AccommodationDetailResponseDto response =
            AccommodationDetailResponseDto.from(
                accommodation
            );

        popularAccommodationRankingService.recordView(
            accommodationId
        );

        return response;
    }

    /**
     * 운영 중인 숙소에 소속된 운영 중인 객실 목록을 조회합니다.
     *
     * 먼저 숙소의 존재 여부와 운영 상태를 확인한 뒤,
     * 객실 목록 화면에 필요한 정보만 조회합니다.
     */
    public List<RoomListResponseDto> getRoomList(
        Long accommodationId
    ) {
        accommodationService.findActiveById(
            accommodationId
        );

        return roomService
            .findAllActiveSummaryByAccommodationId(
                accommodationId
            );
    }

    /**
     * 최신 ACTIVE 숙소 목록을 인기 숙소 응답 형태로 변환합니다.
     *
     * 여기서 부여하는 순위는 실제 인기 순위가 아니라
     * fallback 응답 형식을 유지하기 위한 임시 순번입니다.
     */
    private List<PopularAccommodationResponseDto>
    createFallbackPopularAccommodations(
        List<AccommodationListResponseDto>
            accommodations
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
