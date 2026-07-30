package com.roompick.domain.payment.client.portone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

class PortOneClientTimeoutTest {

    private static final String PORTONE_PAYMENT_ID =
        "roompick-payment-test-001";

    private static final Duration TEST_CONNECT_TIMEOUT =
        Duration.ofMillis(200);

    private static final Duration TEST_READ_TIMEOUT =
        Duration.ofMillis(200);

    /**
     * 실제 PortOne 서버 대신 로컬 소켓 서버를 실행합니다.
     *
     * 연결은 수락하지만 응답은 보내지 않아서
     * RestClient의 read timeout을 발생시킵니다.
     */
    @Test
    @DisplayName(
        "PortOne 응답 시간이 read timeout을 초과하면 연결 실패 오류로 변환한다"
    )
    void convertReadTimeoutToConnectionFailed()
        throws Exception {

        // given
        try (
            ServerSocket serverSocket =
                new ServerSocket(0)
        ) {
            Thread delayedServerThread =
                startDelayedServer(
                    serverSocket
                );

            PortOneClient portOneClient =
                createPortOneClient(
                    serverSocket.getLocalPort()
                );

            // when
            BusinessException exception =
                catchThrowableOfType(
                    () ->
                        portOneClient.getPayment(
                            PORTONE_PAYMENT_ID
                        ),
                    BusinessException.class
                );

            // then
            assertThat(exception)
                .isNotNull();

            assertThat(exception.getErrorCode())
                .isEqualTo(
                    ErrorCode
                        .PORTONE_CONNECTION_FAILED
                );

            /*
             * 테스트 종료 시 대기 중인 서버 스레드를
             * 즉시 종료합니다.
             */
            delayedServerThread.interrupt();
        }
    }

    /**
     * 클라이언트 연결은 수락하지만
     * HTTP 응답을 전송하지 않는 테스트 서버입니다.
     */
    private Thread startDelayedServer(
        ServerSocket serverSocket
    ) {
        Thread serverThread =
            new Thread(
                () ->
                    holdConnectionWithoutResponse(
                        serverSocket
                    )
            );

        /*
         * 테스트 종료를 방해하지 않도록
         * daemon 스레드로 실행합니다.
         */
        serverThread.setDaemon(true);
        serverThread.start();

        return serverThread;
    }

    /**
     * 연결 후 일정 시간 동안 응답하지 않아
     * 클라이언트에서 read timeout이 발생하게 합니다.
     */
    private void holdConnectionWithoutResponse(
        ServerSocket serverSocket
    ) {
        try (
            Socket ignored =
                serverSocket.accept()
        ) {
            Thread.sleep(2_000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            /*
             * 테스트 종료 과정에서 ServerSocket이 닫히면
             * accept()에서 IOException이 발생할 수 있습니다.
             */
        }
    }

    /**
     * 운영 설정과 같은 방식으로
     * connect/read timeout이 적용된
     * PortOneClient를 생성합니다.
     */
    private PortOneClient createPortOneClient(
        int serverPort
    ) {
        HttpClient httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    TEST_CONNECT_TIMEOUT
                )
                .build();

        JdkClientHttpRequestFactory
            requestFactory =
            new JdkClientHttpRequestFactory(
                httpClient
            );

        requestFactory.setReadTimeout(
            TEST_READ_TIMEOUT
        );

        RestClient restClient =
            RestClient.builder()
                .requestFactory(
                    requestFactory
                )
                .baseUrl(
                    "http://127.0.0.1:"
                        + serverPort
                )
                .defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "PortOne test-api-secret"
                )
                .defaultHeader(
                    HttpHeaders.ACCEPT,
                    MediaType
                        .APPLICATION_JSON_VALUE
                )
                .build();

        return new PortOneClient(
            restClient
        );
    }
}
