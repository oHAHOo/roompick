package com.roompick.domain.payment.client.portone;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.roompick.domain.payment.client.portone.dto.response.PortOnePaymentResponseDto;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * PortOne V2 REST API를 호출하는 Client입니다.
 */
@Component
public class PortOneClient {

    private final RestClient portOneRestClient;

    public PortOneClient(
        @Qualifier("portOneRestClient")
        RestClient portOneRestClient
    ) {
        this.portOneRestClient =
            portOneRestClient;
    }

    /**
     * PortOne 결제 ID를 기준으로
     * 결제 정보를 조회합니다.
     */
    public PortOnePaymentResponseDto getPayment(
        String portOnePaymentId
    ) {
        validatePortOnePaymentId(
            portOnePaymentId
        );

        try {
            PortOnePaymentResponseDto response =
                portOneRestClient
                    .get()
                    .uri(
                        "/payments/{paymentId}",
                        portOnePaymentId
                    )
                    .retrieve()
                    .body(
                        PortOnePaymentResponseDto.class
                    );

            validateResponse(response);

            return response;

        } catch (HttpClientErrorException exception) {
            throw convertClientError(exception);

        } catch (ResourceAccessException exception) {
            throw new BusinessException(
                ErrorCode.PORTONE_CONNECTION_FAILED
            );

        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                ErrorCode.PORTONE_API_ERROR
            );

        } catch (RestClientException exception) {
            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }
    }

    /**
     * PortOne에서 반환한 HTTP 4xx 오류를
     * RoomPick의 공통 예외로 변환합니다.
     */
    private BusinessException convertClientError(
        HttpClientErrorException exception
    ) {
        HttpStatus status =
            HttpStatus.resolve(
                exception.getStatusCode().value()
            );

        if (status == HttpStatus.UNAUTHORIZED
            || status == HttpStatus.FORBIDDEN) {

            return new BusinessException(
                ErrorCode.PORTONE_AUTHENTICATION_FAILED
            );
        }

        if (status == HttpStatus.NOT_FOUND) {
            return new BusinessException(
                ErrorCode.PORTONE_PAYMENT_NOT_FOUND
            );
        }

        return new BusinessException(
            ErrorCode.PORTONE_API_ERROR
        );
    }

    /**
     * PortOne 결제 조회 응답이 비어 있는지 검증합니다.
     */
    private void validateResponse(
        PortOnePaymentResponseDto response
    ) {
        if (response == null) {
            throw new BusinessException(
                ErrorCode.PORTONE_INVALID_RESPONSE
            );
        }
    }

    /**
     * PortOne 결제 식별값이 올바른지 검증합니다.
     */
    private void validatePortOnePaymentId(
        String portOnePaymentId
    ) {
        if (portOnePaymentId == null
            || portOnePaymentId.isBlank()) {

            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}
