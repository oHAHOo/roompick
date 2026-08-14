package com.roompick.domain.place.client.kakao;

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

/**
 * Kakao Local 장소 검색 Client의 read timeout 설정을 검증합니다.
 */
class KakaoPlaceSearchClientTimeoutTest {

    private static final Duration TEST_CONNECT_TIMEOUT =
        Duration.ofMillis(200);

    private static final Duration TEST_READ_TIMEOUT =
        Duration.ofMillis(200);

    /**
     * 실제 Kakao 서버 대신 응답하지 않는 로컬 소켓 서버를 사용해
     * read timeout이 발생하는지 검증합니다.
     */
    @Test
    @DisplayName("Kakao 응답 시간이 read timeout을 초과하면 timeout 오류로 변환한다")
    void convertReadTimeoutToPlaceApiTimeout()
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

            KakaoPlaceSearchClient client =
                createKakaoPlaceSearchClient(
                    serverSocket.getLocalPort()
                );

            // when
            BusinessException exception =
                catchThrowableOfType(
                    () -> client.search(
                        "강남역",
                        5
                    ),
                    BusinessException.class
                );

            // then
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.PLACE_API_TIMEOUT);
            assertThat(exception.getCause()).isNotNull();

            /*
             * 테스트 종료 시 대기 중인 서버 스레드를 즉시 종료합니다.
             */
            delayedServerThread.interrupt();
        }
    }

    @Test
    @DisplayName("Kakao 서버 연결 실패를 장소 검색 서비스 장애로 변환한다")
    void convertConnectionFailureToPlaceApiUnavailable()
        throws Exception {

        // given
        int unavailablePort;

        try (
            ServerSocket serverSocket =
                new ServerSocket(0)
        ) {
            unavailablePort =
                serverSocket.getLocalPort();
        }

        KakaoPlaceSearchClient client =
            createKakaoPlaceSearchClient(
                unavailablePort
            );

        // when
        BusinessException exception =
            catchThrowableOfType(
                () -> client.search(
                    "강남역",
                    5
                ),
                BusinessException.class
            );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode())
            .isEqualTo(ErrorCode.PLACE_API_UNAVAILABLE);
        assertThat(exception.getCause()).isNotNull();
    }

    /**
     * 연결은 수락하지만 HTTP 응답을 전송하지 않는 서버를 시작합니다.
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

        serverThread.setDaemon(true);
        serverThread.start();

        return serverThread;
    }

    /**
     * 연결 후 응답을 보내지 않아 Client read timeout을 발생시킵니다.
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
             * 테스트 종료 과정에서 ServerSocket이 닫힐 수 있습니다.
             */
        }
    }

    /**
     * 운영 설정과 같은 방식으로 짧은 timeout이 적용된 Client를 생성합니다.
     */
    private KakaoPlaceSearchClient createKakaoPlaceSearchClient(
        int serverPort
    ) {
        HttpClient httpClient =
            HttpClient.newBuilder()
                .connectTimeout(
                    TEST_CONNECT_TIMEOUT
                )
                .build();

        JdkClientHttpRequestFactory requestFactory =
            new JdkClientHttpRequestFactory(
                httpClient
            );

        requestFactory.setReadTimeout(
            TEST_READ_TIMEOUT
        );

        RestClient restClient =
            RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(
                    "http://127.0.0.1:"
                        + serverPort
                )
                .defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "KakaoAK test-kakao-rest-api-key"
                )
                .defaultHeader(
                    HttpHeaders.ACCEPT,
                    MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        return new KakaoPlaceSearchClient(
            restClient
        );
    }
}
