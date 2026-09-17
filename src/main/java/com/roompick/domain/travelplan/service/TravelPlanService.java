package com.roompick.domain.travelplan.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roompick.domain.accommodation.dto.AccommodationLocationSearchResponseDto;
import com.roompick.domain.accommodation.service.AccommodationLocationSearchService;
import com.roompick.domain.travelplan.client.TravelPlanLlmClient;
import com.roompick.domain.travelplan.dto.ItineraryDayDto;
import com.roompick.domain.travelplan.dto.RecommendedAccommodationDto;
import com.roompick.domain.travelplan.dto.TravelPlanRequestDto;
import com.roompick.domain.travelplan.dto.TravelPlanResponseDto;
import com.roompick.domain.travelplan.model.TravelPlanCandidate;
import com.roompick.domain.travelplan.model.TravelPlanLlmRequest;
import com.roompick.domain.travelplan.model.TravelPlanLlmResult;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.BusinessException.BusinessFieldError;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 여행 계획 생성 조건을 검증하고 LLM 여행 계획을 만드는 Service입니다.
 *
 * 추천 숙소는 LLM이 만들어낸 텍스트가 아니라
 * 좌표 기준으로 조회한 실제 ACTIVE 숙소에서만 선택합니다.
 *
 * LLM 호출 중에는 DB 트랜잭션을 시작하지 않으며,
 * 이력 저장은 호출이 끝난 뒤 TravelPlanHistoryService에서 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanService {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    /**
     * 여행 계획의 중심 좌표에서 숙소 후보를 찾는 반경입니다.
     */
    private static final double SEARCH_RADIUS_KM = 10.0;

    /**
     * LLM에 전달할 숙소 후보의 최대 개수입니다.
     */
    private static final int MAX_CANDIDATES = 5;

    /**
     * 한 번에 계획할 수 있는 최대 숙박일 수입니다.
     */
    private static final long MAX_NIGHTS = 14;

    private final AccommodationLocationSearchService
        accommodationLocationSearchService;
    private final TravelPlanLlmClient travelPlanLlmClient;
    private final TravelPlanHistoryService travelPlanHistoryService;
    private final ObjectMapper objectMapper;

    /**
     * 좌표와 숙박 기간을 기준으로 여행 일정과 추천 숙소를 생성합니다.
     *
     * 1. 요청값 검증
     * 2. 반경 내 ACTIVE 숙소 후보 조회
     * 3. LLM으로 일정·추천 생성
     * 4. LLM이 선택한 후보를 실제 숙소 데이터와 매핑
     * 5. 이력 저장 후 응답 반환
     */
    public TravelPlanResponseDto generatePlan(
        TravelPlanRequestDto request
    ) {
        validateRequest(request);

        List<AccommodationLocationSearchResponseDto> nearbyAccommodations =
            accommodationLocationSearchService.searchNearby(
                null,
                request.latitude(),
                request.longitude(),
                SEARCH_RADIUS_KM,
                MAX_CANDIDATES
            );

        TravelPlanLlmResult result =
            travelPlanLlmClient.generate(
                new TravelPlanLlmRequest(
                    request.latitude(),
                    request.longitude(),
                    request.checkInDate(),
                    request.checkOutDate(),
                    request.guestCount(),
                    toCandidates(nearbyAccommodations)
                )
            );

        List<ItineraryDayDto> itinerary =
            result.itinerary()
                .stream()
                .map(ItineraryDayDto::from)
                .toList();

        List<RecommendedAccommodationDto> recommendations =
            toRecommendations(
                result,
                nearbyAccommodations
            );

        Long travelPlanId =
            travelPlanHistoryService.save(
                request.latitude(),
                request.longitude(),
                request.checkInDate(),
                request.checkOutDate(),
                request.guestCount(),
                travelPlanLlmClient.modelName(),
                writeItineraryJson(itinerary),
                recommendations
            );

        return new TravelPlanResponseDto(
            travelPlanId,
            request.checkInDate(),
            request.checkOutDate(),
            request.guestCount(),
            itinerary,
            recommendations
        );
    }

    /**
     * 조회된 숙소를 LLM에 전달할 후보 모델로 변환합니다.
     *
     * 가격이나 편의시설처럼 조회 결과에 없는 정보는 전달하지 않습니다.
     */
    private List<TravelPlanCandidate> toCandidates(
        List<AccommodationLocationSearchResponseDto> accommodations
    ) {
        return accommodations.stream()
            .map(accommodation -> new TravelPlanCandidate(
                accommodation.accommodationId(),
                accommodation.name(),
                accommodation.address(),
                accommodation.distanceKm()
            ))
            .toList();
    }

    /**
     * LLM이 선택한 후보 인덱스를 실제 숙소 조회 결과와 매핑합니다.
     *
     * 숙소 이름·주소·거리는 DB 조회 결과를 사용하고
     * 추천 이유만 LLM 응답에서 가져옵니다.
     */
    private List<RecommendedAccommodationDto> toRecommendations(
        TravelPlanLlmResult result,
        List<AccommodationLocationSearchResponseDto> accommodations
    ) {
        /*
         * 후보 목록은 이 Service가 만들었으므로 범위 검증도 여기서 한 번 더 한다.
         * LLM 응답에서 비롯된 인덱스로 조회 결과를 벗어나 접근하면
         * 사용자에게 500 오류가 노출된다.
         */
        return result.selections()
            .stream()
            .filter(selection ->
                selection.candidateIndex() >= 0
                    && selection.candidateIndex() < accommodations.size()
            )
            .map(selection -> RecommendedAccommodationDto.from(
                accommodations.get(selection.candidateIndex()),
                selection.reason()
            ))
            .toList();
    }

    /**
     * 이력 저장용으로 일정을 JSON 문자열로 변환합니다.
     */
    private String writeItineraryJson(
        List<ItineraryDayDto> itinerary
    ) {
        try {
            return objectMapper.writeValueAsString(itinerary);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                exception
            );
        }
    }

    /**
     * 좌표 범위와 숙박 기간이 유효한지 검증합니다.
     *
     * 잘못된 요청은 LLM을 호출하기 전에 차단합니다.
     */
    private void validateRequest(
        TravelPlanRequestDto request
    ) {
        double latitude = request.latitude();
        double longitude = request.longitude();

        if (
            !Double.isFinite(latitude)
                || latitude < MIN_LATITUDE
                || latitude > MAX_LATITUDE
        ) {
            throw invalidInput(
                "latitude",
                "위도는 -90 이상 90 이하여야 합니다."
            );
        }

        if (
            !Double.isFinite(longitude)
                || longitude < MIN_LONGITUDE
                || longitude > MAX_LONGITUDE
        ) {
            throw invalidInput(
                "longitude",
                "경도는 -180 이상 180 이하여야 합니다."
            );
        }

        LocalDate checkInDate = request.checkInDate();
        LocalDate checkOutDate = request.checkOutDate();

        if (!checkInDate.isBefore(checkOutDate)) {
            throw invalidInput(
                "checkOutDate",
                "체크아웃 날짜는 체크인 날짜보다 이후여야 합니다."
            );
        }

        if (checkInDate.isBefore(LocalDate.now())) {
            throw invalidInput(
                "checkInDate",
                "체크인 날짜는 과거일 수 없습니다."
            );
        }

        if (
            checkInDate.plusDays(MAX_NIGHTS).isBefore(checkOutDate)
        ) {
            throw invalidInput(
                "checkOutDate",
                "여행 계획은 최대 14박까지 생성할 수 있습니다."
            );
        }
    }

    /**
     * 요청 필드의 구체적인 검증 사유를 포함한 공통 입력 예외를 생성합니다.
     */
    private BusinessException invalidInput(
        String field,
        String message
    ) {
        return new BusinessException(
            ErrorCode.INVALID_INPUT_VALUE,
            List.of(
                new BusinessFieldError(
                    field,
                    message
                )
            )
        );
    }
}
