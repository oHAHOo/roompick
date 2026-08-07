package com.roompick.domain.reservation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.roompick.domain.reservation.dto.ReservationCreateRequestDto;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

/**
 * 예약 생성 요청을 비교하기 위한 SHA-256 해시를 생성합니다.
 *
 * JSON 문자열 자체를 해시하지 않고 DTO의 필드 값을
 * 고정된 순서와 형식으로 조합해 해시합니다.
 * 따라서 JSON 필드 순서나 공백 차이의 영향을 받지 않습니다.
 */
@Component
public class ReservationRequestHasher {

    private static final String HASH_ALGORITHM =
        "SHA-256";

    private static final String HASH_VERSION =
        "reservation-create:v1";

    /**
     * 객실 ID, 체크인·체크아웃 날짜, 예약 인원을
     * 기준으로 예약 생성 요청 해시를 생성합니다.
     */
    public String hash(
        ReservationCreateRequestDto request
    ) {
        validateRequest(request);

        String canonicalRequest =
            createCanonicalRequest(request);

        try {
            MessageDigest messageDigest =
                MessageDigest.getInstance(
                    HASH_ALGORITHM
                );

            byte[] hashBytes =
                messageDigest.digest(
                    canonicalRequest.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            return HexFormat.of()
                .formatHex(hashBytes);
        } catch (
            NoSuchAlgorithmException exception
        ) {
            /*
             * SHA-256은 Java 표준 알고리즘이므로
             * 정상적인 실행 환경에서는 발생하지 않습니다.
             */
            throw new IllegalStateException(
                "예약 요청 해시를 생성할 수 없습니다.",
                exception
            );
        }
    }

    /**
     * 요청 필드를 고정된 순서와 형식으로 변환합니다.
     *
     * 해시 생성 규칙이 변경될 가능성에 대비해
     * 첫 줄에 해시 버전을 포함합니다.
     */
    private String createCanonicalRequest(
        ReservationCreateRequestDto request
    ) {
        return String.join(
            "\n",
            HASH_VERSION,
            "roomId=" + request.roomId(),
            "checkInDate="
                + request.checkInDate(),
            "checkOutDate="
                + request.checkOutDate(),
            "guestCount="
                + request.guestCount()
        );
    }

    private void validateRequest(
        ReservationCreateRequestDto request
    ) {
        if (
            request == null
                || request.roomId() == null
                || request.checkInDate() == null
                || request.checkOutDate() == null
                || request.guestCount() == null
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}
