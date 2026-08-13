package com.roompick.domain.place.client.kakao;

/**
 * Kakao Local API 응답을 신뢰할 수 없을 때 발생하는 예외입니다.
 *
 * 장소 검색 전용 ErrorCode가 추가되기 전까지 잘못된 외부 응답을
 * 정상 결과로 숨기지 않고 상위 계층에 전달하는 용도로 사용합니다.
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
