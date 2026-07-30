package com.roompick.domain.payment.client.portone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentResponseDto;
import com.roompick.global.common.BusinessException;

class PortOneClientTest {

    private static final String BASE_URL =
        "http://localhost";

    private static final String API_SECRET =
        "test-portone-api-secret";

    private static final String PORTONE_PAYMENT_ID =
        "roompick-payment-test-001";

    private MockRestServiceServer mockServer;

    private PortOneClient portOneClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
            RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "PortOne " + API_SECRET
                )
                .defaultHeader(
                    HttpHeaders.ACCEPT,
                    MediaType.APPLICATION_JSON_VALUE
                );

        mockServer =
            MockRestServiceServer
                .bindTo(builder)
                .build();

        RestClient restClient =
            builder.build();

        portOneClient =
            new PortOneClient(restClient);
    }

    @Test
    @DisplayName("PortOne 결제 단건 조회에 성공하면 응답을 반환한다")
    void getPaymentSuccessfully() {

        // given
        mockServer.expect(
                requestTo(
                    BASE_URL
                        + "/payments/"
                        + PORTONE_PAYMENT_ID
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andExpect(
                header(
                    HttpHeaders.AUTHORIZATION,
                    "PortOne " + API_SECRET
                )
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "PAID",
                      "id": "roompick-payment-test-001",
                      "transactionId": "transaction-test-001",
                      "amount": {
                        "total": 200000
                      },
                      "paidAt": "2026-07-29T08:00:00Z"
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        // when
        PortOnePaymentResponseDto result =
            portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            );

        // then
        assertEquals(
            "PAID",
            result.status()
        );

        assertEquals(
            PORTONE_PAYMENT_ID,
            result.id()
        );

        assertEquals(
            "transaction-test-001",
            result.transactionId()
        );

        assertEquals(
            200_000L,
            result.amount().total()
        );

        assertEquals(
            OffsetDateTime.parse(
                "2026-07-29T08:00:00Z"
            ),
            result.paidAt()
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("PortOne 결제가 존재하지 않으면 공통 예외로 변환한다")
    void rejectWhenPortOnePaymentDoesNotExist() {

        // given
        mockServer.expect(
                requestTo(
                    BASE_URL
                        + "/payments/"
                        + PORTONE_PAYMENT_ID
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .body(
                        """
                        {
                          "type": "PAYMENT_NOT_FOUND",
                          "message": "결제를 찾을 수 없습니다."
                        }
                        """
                    )
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("PortOne 인증에 실패하면 공통 예외로 변환한다")
    void rejectWhenPortOneAuthenticationFails() {

        // given
        mockServer.expect(
                requestTo(
                    BASE_URL
                        + "/payments/"
                        + PORTONE_PAYMENT_ID
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withStatus(HttpStatus.UNAUTHORIZED)
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("PortOne 서버에서 오류가 발생하면 공통 예외로 변환한다")
    void rejectWhenPortOneServerReturnsError() {

        // given
        mockServer.expect(
                requestTo(
                    BASE_URL
                        + "/payments/"
                        + PORTONE_PAYMENT_ID
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withServerError()
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("PortOne 응답 본문이 비어 있으면 공통 예외로 변환한다")
    void rejectWhenPortOneResponseBodyIsEmpty() {

        // given
        mockServer.expect(
                requestTo(
                    BASE_URL
                        + "/payments/"
                        + PORTONE_PAYMENT_ID
                )
            )
            .andExpect(
                method(HttpMethod.GET)
            )
            .andRespond(
                withSuccess(
                    "",
                    MediaType.APPLICATION_JSON
                )
            );

        // when & then
        assertThrows(
            BusinessException.class,
            () -> portOneClient.getPayment(
                PORTONE_PAYMENT_ID
            )
        );

        mockServer.verify();
    }

    @Test
    @DisplayName("PortOne 결제 식별값이 비어 있으면 API를 호출하지 않는다")
    void rejectWhenPortOnePaymentIdIsBlank() {

        // when & then
        assertThrows(
            BusinessException.class,
            () -> portOneClient.getPayment(" ")
        );
    }
}
