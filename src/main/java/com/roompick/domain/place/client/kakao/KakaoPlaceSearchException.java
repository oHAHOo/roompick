package com.roompick.domain.place.client.kakao;

/**
 * Kakao Local API 응답 구조나 좌표 값이 유효하지 않을 때
 * Client 내부에서 사용하는 예외입니다.
 *
 * KakaoPlaceSearchClient에서 최종적으로
 * PLACE_API_INVALID_RESPONSE BusinessException으로 변환합니다.
 */
public class KakaoPlaceSearchException extends RuntimeException {

    public KakaoPlaceSearchException(
        String message
    ) {
        super(message);
    }

    public KakaoPlaceSearchException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}
