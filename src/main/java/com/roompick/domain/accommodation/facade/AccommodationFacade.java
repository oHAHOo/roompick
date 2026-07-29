package com.roompick.domain.accommodation.facade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.dto.AccommodationDetailResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.dto.AccommodationPageResponseDto;
import com.roompick.domain.accommodation.dto.PopularAccommodationResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.accommodation.service.PopularAccommodationService;
import com.roompick.domain.room.dto.RoomListResponseDto;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccommodationFacade {

    private final AccommodationService accommodationService;
    private final RoomService roomService;
    private final PopularAccommodationService popularAccommodationService;

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
     * 오늘 날짜의 인기 숙소 목록을 조회합니다.
     *
     * Redis에서 인기 숙소 ID를 순위대로 조회하고,
     * 해당 숙소의 공개 정보는 IN 조건으로 한 번만 DB에서 조회합니다.
     *
     * DB 조회 결과는 순서가 보장되지 않으므로 Redis 랭킹 순서로 다시 정렬합니다.
     * 존재하지 않거나 비공개 상태인 숙소는 제외한 뒤 순위를 다시 계산합니다.
     */
    public List<PopularAccommodationResponseDto>
    getPopularAccommodations(
        int limit
    ) {
        List<Long> rankedAccommodationIds =
            popularAccommodationService.findTopAccommodationIds(
                limit
            );

        List<AccommodationListResponseDto> activeAccommodations =
            accommodationService.findAllActiveSummaryByIds(
                rankedAccommodationIds
            );

        Map<Long, AccommodationListResponseDto> accommodationById =
            new HashMap<>();

        for (
            AccommodationListResponseDto accommodation
            : activeAccommodations
        ) {
            accommodationById.put(
                accommodation.accommodationId(),
                accommodation
            );
        }

        List<PopularAccommodationResponseDto> result =
            new java.util.ArrayList<>();

        for (Long accommodationId : rankedAccommodationIds) {
            AccommodationListResponseDto accommodation =
                accommodationById.get(
                    accommodationId
                );

            if (accommodation == null) {
                continue;
            }

            int rank = result.size() + 1;

            result.add(
                PopularAccommodationResponseDto.from(
                    rank,
                    accommodation
                )
            );
        }

        return result;
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

        popularAccommodationService.recordView(
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
}
