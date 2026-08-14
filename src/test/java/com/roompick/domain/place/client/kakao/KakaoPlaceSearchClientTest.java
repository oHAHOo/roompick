package com.roompick.domain.place.client.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.roompick.domain.place.model.PlaceSearchCandidate;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * Kakao Local 장소 검색 요청 계약과 응답 변환을 검증합니다.
 */
class KakaoPlaceSearchClientTest {

    private static final String BASE_URL =
        "http://localhost";

    private static final String API_KEY =
        "test-kakao-rest-api-key";

    private static final String SEARCH_PATH =
        "/v2/local/search/keyword.json";

    private MockRestServiceServer mockServer;

    private KakaoPlaceSearchClient kakaoPlaceSearchClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
            RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "KakaoAK " + API_KEY
                )
                .defaultHeader(
                    HttpHeaders.ACCEPT,
                    MediaType.APPLICATION_JSON_VALUE
                );

        mockServer =
            MockRestServiceServer
                .bindTo(builder)
                .build();

        kakaoPlaceSearchClient =
            new KakaoPlaceSearchClient(
                builder.build()
            );
    }

    @Test
    @DisplayName("Kakao 장소 검색 요청과 후보 장소 변환에 성공한다")
    void searchPlacesSuccessfully() {
        // given
        String encodedQuery =
            URLEncoder.encode(
                "강남역",
                StandardCharsets.UTF_8
            );

        mockServer.expect(
                requestTo(
                    startsWith(
                        BASE_URL + SEARCH_PATH
                    )
                )
            )
            .andExpect(method(HttpMethod.GET))
            .andExpect(request ->
                assertThat(
                    request
                        .getURI()
                        .getRawQuery()
                ).contains(
                    "query=" + encodedQuery
                )
            )
            .andExpect(
                header(
                    HttpHeaders.AUTHORIZATION,
                    "KakaoAK " + API_KEY
                )
            )
            .andExpect(
                queryParam(
                    "size",
                    "5"
                )
            )
            .andRespond(
                withSuccess(
                    validResponseBody(),
                    MediaType.APPLICATION_JSON
                )
            );

        // when
        List<PlaceSearchCandidate> candidates =
            kakaoPlaceSearchClient.search(
                "강남역",
                5
            );

        // then
        assertThat(candidates).hasSize(1);

        PlaceSearchCandidate candidate =
            candidates.get(0);

        assertThat(candidate.placeId()).isEqualTo("123456");
        assertThat(candidate.name()).isEqualTo("강남역 2호선");
        assertThat(candidate.address()).isEqualTo("서울 강남구 역삼동");
        assertThat(candidate.roadAddress())
            .isEqualTo("서울 강남구 강남대로 396");
        assertThat(candidate.latitude()).isEqualTo(37.4979);
        assertThat(candidate.longitude()).isEqualTo(127.0276);
        assertThat(candidate.category()).isEqualTo("교통,수송 > 지하철");

        mockServer.verify();
    }

    @Test
    @DisplayName("Kakao documents가 비어 있으면 빈 목록을 반환한다")
    void returnEmptyListWhenDocumentsAreEmpty() {
        // given
        expectSuccessResponse(
            """
            {
              "meta": {
                "total_count": 0,
                "pageable_count": 0,
                "is_end": true
              },
              "documents": []
            }
            """
        );

        // when
        List<PlaceSearchCandidate> candidates =
            kakaoPlaceSearchClient.search(
                "존재하지않는장소",
                5
            );

        // then
        assertThat(candidates).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Kakao 응답 본문이 없으면 잘못된 응답으로 처리한다")
    void rejectEmptyResponseBody() {
        // given
        expectSuccessResponse("");

        // when & then
        assertThatThrownBy(
            () -> kakaoPlaceSearchClient.search(
                "강남역",
                5
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.PLACE_API_INVALID_RESPONSE);

        mockServer.verify();
    }

    @Test
    @DisplayName("Kakao documents가 null이면 잘못된 응답으로 처리한다")
    void rejectNullDocuments() {
        // given
        expectSuccessResponse(
            """
            {
              "meta": {
                "total_count": 1
              },
              "documents": null
            }
            """
        );

        // when & then
        assertThatThrownBy(
            () -> kakaoPlaceSearchClient.search(
                "강남역",
                5
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.PLACE_API_INVALID_RESPONSE);

        mockServer.verify();
    }

    @Test
    @DisplayName("Kakao 경도 x가 숫자가 아니면 잘못된 응답으로 처리한다")
    void rejectNonNumericLongitude() {
        assertInvalidCoordinate(
            "not-a-number",
            "37.4979"
        );
    }

    @Test
    @DisplayName("Kakao 위도 y가 숫자가 아니면 잘못된 응답으로 처리한다")
    void rejectNonNumericLatitude() {
        assertInvalidCoordinate(
            "127.0276",
            "not-a-number"
        );
    }

    @Test
    @DisplayName("Kakao 경도 x가 허용 범위를 벗어나면 잘못된 응답으로 처리한다")
    void rejectLongitudeOutOfRange() {
        assertInvalidCoordinate(
            "180.000001",
            "37.4979"
        );
    }

    @Test
    @DisplayName("Kakao 위도 y가 허용 범위를 벗어나면 잘못된 응답으로 처리한다")
    void rejectLatitudeOutOfRange() {
        assertInvalidCoordinate(
            "127.0276",
            "90.000001"
        );
    }

    @Test
    @DisplayName("Kakao 401 응답을 장소 API 인증 실패로 변환한다")
    void convertUnauthorizedToAuthenticationFailed() {
        assertHttpError(
            HttpStatus.UNAUTHORIZED,
            ErrorCode.PLACE_API_AUTHENTICATION_FAILED
        );
    }

    @Test
    @DisplayName("Kakao 403 응답을 장소 API 인증 실패로 변환한다")
    void convertForbiddenToAuthenticationFailed() {
        assertHttpError(
            HttpStatus.FORBIDDEN,
            ErrorCode.PLACE_API_AUTHENTICATION_FAILED
        );
    }

    @Test
    @DisplayName("Kakao 429 응답을 장소 검색 요청 제한으로 변환한다")
    void convertTooManyRequestsToRateLimited() {
        assertHttpError(
            HttpStatus.TOO_MANY_REQUESTS,
            ErrorCode.PLACE_API_RATE_LIMITED
        );
    }

    @Test
    @DisplayName("Kakao 기타 4xx 응답을 장소 API 요청 실패로 변환한다")
    void convertOtherClientErrorToRequestFailed() {
        assertHttpError(
            HttpStatus.BAD_REQUEST,
            ErrorCode.PLACE_API_REQUEST_FAILED
        );
    }

    @Test
    @DisplayName("Kakao 500 응답을 장소 검색 서비스 장애로 변환한다")
    void convertInternalServerErrorToUnavailable() {
        // given
        mockServer.expect(
                requestTo(
                    startsWith(
                        BASE_URL + SEARCH_PATH
                    )
                )
            )
            .andRespond(withServerError());

        // when & then
        assertBusinessError(
            ErrorCode.PLACE_API_UNAVAILABLE
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("Kakao 503 응답을 장소 검색 서비스 장애로 변환한다")
    void convertServiceUnavailableToUnavailable() {
        assertHttpError(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCode.PLACE_API_UNAVAILABLE
        );
    }

    /**
     * 성공 상태와 전달받은 본문을 반환하는 Kakao 요청을 준비합니다.
     */
    private void expectSuccessResponse(
        String responseBody
    ) {
        mockServer.expect(
                requestTo(
                    startsWith(
                        BASE_URL + SEARCH_PATH
                    )
                )
            )
            .andRespond(
                withSuccess(
                    responseBody,
                    MediaType.APPLICATION_JSON
                )
            );
    }

    /**
     * 잘못된 좌표가 정상 장소 후보로 변환되지 않는지 검증합니다.
     */
    private void assertInvalidCoordinate(
        String longitude,
        String latitude
    ) {
        // given
        expectSuccessResponse(
            responseBodyWithCoordinates(
                longitude,
                latitude
            )
        );

        // when & then
        assertThatThrownBy(
            () -> kakaoPlaceSearchClient.search(
                "강남역",
                5
            )
        )
            .isInstanceOf(BusinessException.class)
            .extracting(exception ->
                ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.PLACE_API_INVALID_RESPONSE);

        mockServer.verify();
    }

    /**
     * Kakao HTTP 오류가 예상한 RoomPick 오류로 변환되는지 검증합니다.
     */
    private void assertHttpError(
        HttpStatus status,
        ErrorCode expectedErrorCode
    ) {
        // given
        mockServer.expect(
                requestTo(
                    startsWith(
                        BASE_URL + SEARCH_PATH
                    )
                )
            )
            .andRespond(
                withStatus(status)
            );

        // when & then
        assertBusinessError(expectedErrorCode);
        mockServer.verify();
    }

    /**
     * Kakao Client 호출 결과의 BusinessException ErrorCode를 검증합니다.
     */
    private void assertBusinessError(
        ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(
            () -> kakaoPlaceSearchClient.search(
                "강남역",
                5
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(expectedErrorCode);

                assertThat(businessException.getCause())
                    .isNotNull();
            });
    }

    /**
     * 정상 좌표가 포함된 Kakao 장소 검색 응답을 생성합니다.
     */
    private String validResponseBody() {
        return responseBodyWithCoordinates(
            "127.0276",
            "37.4979"
        );
    }

    /**
     * 좌표 검증 테스트용 Kakao 장소 검색 응답을 생성합니다.
     */
    private String responseBodyWithCoordinates(
        String longitude,
        String latitude
    ) {
        return """
            {
              "meta": {
                "total_count": 1,
                "pageable_count": 1,
                "is_end": true
              },
              "documents": [
                {
                  "id": "123456",
                  "place_name": "강남역 2호선",
                  "category_name": "교통,수송 > 지하철",
                  "address_name": "서울 강남구 역삼동",
                  "road_address_name": "서울 강남구 강남대로 396",
                  "x": "%s",
                  "y": "%s"
                }
              ]
            }
            """.formatted(
                longitude,
                latitude
            );
    }
}
