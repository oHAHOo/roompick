package com.roompick.domain.place.client.kakao;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.roompick.domain.place.client.PlaceSearchClient;
import com.roompick.domain.place.client.kakao.dto.KakaoPlaceDocumentDto;
import com.roompick.domain.place.client.kakao.dto.KakaoPlaceSearchResponseDto;
import com.roompick.domain.place.model.PlaceSearchCandidate;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * Kakao Local 장소 키워드 검색 API를 호출하는 Client입니다.
 *
 * Kakao 전용 응답을 제공자 중립 PlaceSearchCandidate로 변환하여
 * 외부 API 타입이 Service 계층에 노출되지 않게 합니다.
 */
@Component
public class KakaoPlaceSearchClient implements PlaceSearchClient {

    private static final String SEARCH_PATH =
        "/v2/local/search/keyword.json";

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;

    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    private final RestClient kakaoPlaceRestClient;

    public KakaoPlaceSearchClient(
        @Qualifier("kakaoPlaceRestClient")
        RestClient kakaoPlaceRestClient
    ) {
        this.kakaoPlaceRestClient =
            kakaoPlaceRestClient;
    }

    /**
     * 검색어와 최대 결과 수를 Kakao Local API에 전달하고
     * 내부 장소 후보 목록으로 변환합니다.
     */
    @Override
    public List<PlaceSearchCandidate> search(
        String query,
        int limit
    ) {
        try {
            KakaoPlaceSearchResponseDto response =
                kakaoPlaceRestClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path(SEARCH_PATH)
                                .queryParam(
                                    "query",
                                    query
                                )
                                .queryParam(
                                    "size",
                                    limit
                                )
                                .build()
                    )
                    .retrieve()
                    .body(
                        KakaoPlaceSearchResponseDto.class
                    );

            validateResponse(response);

            return response.documents()
                .stream()
                .map(this::toCandidate)
                .toList();
        } catch (KakaoPlaceSearchException exception) {
            throw new BusinessException(
                ErrorCode.PLACE_API_INVALID_RESPONSE,
                exception
            );
        } catch (ResourceAccessException exception) {
            throw convertResourceAccessException(
                exception
            );
        } catch (RestClientResponseException exception) {
            throw convertResponseException(
                exception
            );
        } catch (RestClientException exception) {
            throw new BusinessException(
                ErrorCode.PLACE_API_INVALID_RESPONSE,
                exception
            );
        }
    }

    /**
     * Kakao API HTTP 오류를 응답 상태에 맞는 RoomPick 오류로 변환합니다.
     */
    private BusinessException convertResponseException(
        RestClientResponseException exception
    ) {
        HttpStatus status =
            HttpStatus.resolve(
                exception.getStatusCode().value()
            );

        if (
            status == HttpStatus.UNAUTHORIZED
                || status == HttpStatus.FORBIDDEN
        ) {
            return new BusinessException(
                ErrorCode.PLACE_API_AUTHENTICATION_FAILED,
                exception
            );
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new BusinessException(
                ErrorCode.PLACE_API_RATE_LIMITED,
                exception
            );
        }

        if (exception.getStatusCode().is5xxServerError()) {
            return new BusinessException(
                ErrorCode.PLACE_API_UNAVAILABLE,
                exception
            );
        }

        return new BusinessException(
            ErrorCode.PLACE_API_REQUEST_FAILED,
            exception
        );
    }

    /**
     * 연결 계층 오류의 원인 체인을 확인하여 timeout과 일반 장애를 구분합니다.
     */
    private BusinessException convertResourceAccessException(
        ResourceAccessException exception
    ) {
        ErrorCode errorCode =
            hasTimeoutCause(exception)
                ? ErrorCode.PLACE_API_TIMEOUT
                : ErrorCode.PLACE_API_UNAVAILABLE;

        return new BusinessException(
            errorCode,
            exception
        );
    }

    /**
     * 예외 원인 체인에 HTTP 또는 소켓 timeout이 포함되어 있는지 확인합니다.
     */
    private boolean hasTimeoutCause(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (
                current instanceof HttpTimeoutException
                    || current instanceof HttpConnectTimeoutException
                    || current instanceof SocketTimeoutException
            ) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    /**
     * 응답 본문과 documents 필드가 존재하는지 검증합니다.
     *
     * 빈 documents 목록은 정상 검색 결과로 허용합니다.
     */
    private void validateResponse(
        KakaoPlaceSearchResponseDto response
    ) {
        if (response == null) {
            throw new KakaoPlaceSearchException(
                "Kakao 장소 검색 응답 본문이 없습니다."
            );
        }

        if (response.documents() == null) {
            throw new KakaoPlaceSearchException(
                "Kakao 장소 검색 응답에 documents가 없습니다."
            );
        }
    }

    /**
     * Kakao 장소 응답의 좌표를 숫자로 변환하고
     * 제공자 중립 장소 후보를 생성합니다.
     */
    private PlaceSearchCandidate toCandidate(
        KakaoPlaceDocumentDto document
    ) {
        if (document == null) {
            throw new KakaoPlaceSearchException(
                "Kakao 장소 검색 결과에 비어 있는 document가 있습니다."
            );
        }

        double longitude = parseCoordinate(
            document.x(),
            "x"
        );

        double latitude = parseCoordinate(
            document.y(),
            "y"
        );

        validateLongitude(longitude);
        validateLatitude(latitude);

        return new PlaceSearchCandidate(
            document.id(),
            document.placeName(),
            document.addressName(),
            document.roadAddressName(),
            latitude,
            longitude,
            document.categoryName()
        );
    }

    /**
     * Kakao가 문자열로 반환한 좌표를 유한한 double 값으로 변환합니다.
     */
    private double parseCoordinate(
        String coordinate,
        String field
    ) {
        try {
            double value =
                Double.parseDouble(coordinate);

            if (!Double.isFinite(value)) {
                throw invalidCoordinate(field);
            }

            return value;
        } catch (
            NullPointerException
                | NumberFormatException exception
        ) {
            throw new KakaoPlaceSearchException(
                "Kakao 장소 검색 좌표 "
                    + field
                    + "의 형식이 올바르지 않습니다.",
                exception
            );
        }
    }

    /**
     * 위도가 -90 이상 90 이하인지 검증합니다.
     */
    private void validateLatitude(
        double latitude
    ) {
        if (
            latitude < MIN_LATITUDE
                || latitude > MAX_LATITUDE
        ) {
            throw invalidCoordinate("y");
        }
    }

    /**
     * 경도가 -180 이상 180 이하인지 검증합니다.
     */
    private void validateLongitude(
        double longitude
    ) {
        if (
            longitude < MIN_LONGITUDE
                || longitude > MAX_LONGITUDE
        ) {
            throw invalidCoordinate("x");
        }
    }

    /**
     * 허용 범위를 벗어난 Kakao 좌표 응답 예외를 생성합니다.
     */
    private KakaoPlaceSearchException invalidCoordinate(
        String field
    ) {
        return new KakaoPlaceSearchException(
            "Kakao 장소 검색 좌표 "
                + field
                + "가 허용 범위를 벗어났습니다."
        );
    }
}
