package com.roompick.domain.place.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.roompick.domain.place.client.PlaceSearchClient;
import com.roompick.domain.place.dto.PlaceSearchResponseDto;
import com.roompick.domain.place.model.PlaceSearchCandidate;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.BusinessException.BusinessFieldError;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 장소 검색 조건을 검증하고 외부 장소 검색을 수행하는 Service입니다.
 *
 * 데이터베이스를 사용하지 않으며 외부 API 호출 중 트랜잭션을 시작하지 않습니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 15;
    private static final int MAX_QUERY_LENGTH = 100;

    private final PlaceSearchClient placeSearchClient;

    /**
     * 검색어를 정규화하고 후보 장소를 공개 응답 DTO로 변환합니다.
     *
     * 잘못된 요청은 외부 API를 호출하기 전에 차단합니다.
     * 동일한 정규화 검색어와 limit의 정상 결과는 단기 캐시에 저장합니다.
     */
    @Cacheable(
        cacheNames = "placeSearches",
        key = "#query.trim() + ':' + #limit",
        condition = "#query != null && !#query.isBlank() " +
            "&& #query.trim().length() <= 100 " +
            "&& #limit >= 1 && #limit <= 15"
    )
    public List<PlaceSearchResponseDto> searchPlaces(
        String query,
        int limit
    ) {
        String normalizedQuery = normalizeQuery(query);
        validateLimit(limit);

        List<PlaceSearchCandidate> candidates =
            placeSearchClient.search(
                normalizedQuery,
                limit
            );

        return candidates
            .stream()
            .map(PlaceSearchResponseDto::from)
            .toList();
    }

    /**
     * 검색어의 앞뒤 공백을 제거하고 필수 입력 여부를 검증합니다.
     */
    private String normalizeQuery(
        String query
    ) {
        if (
            query == null
                || query.isBlank()
        ) {
            throw invalidInput(
                "query",
                "장소 검색어는 필수입니다."
            );
        }

        String normalizedQuery = query.trim();

        if (normalizedQuery.length() > MAX_QUERY_LENGTH) {
            throw invalidInput(
                "query",
                "장소 검색어는 100자 이하여야 합니다."
            );
        }

        return normalizedQuery;
    }

    /**
     * 반환할 장소 수가 1개 이상 15개 이하인지 검증합니다.
     */
    private void validateLimit(
        int limit
    ) {
        if (
            limit < MIN_LIMIT
                || limit > MAX_LIMIT
        ) {
            throw invalidInput(
                "limit",
                "장소 검색 결과 수는 1 이상 15 이하여야 합니다."
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
