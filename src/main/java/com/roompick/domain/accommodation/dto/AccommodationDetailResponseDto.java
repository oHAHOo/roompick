package com.roompick.domain.accommodation.dto;

import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationImage;
import com.roompick.domain.accommodation.entity.AccommodationStatus;

/**
 * 숙소 상세 화면에 필요한 숙소 기본 정보를 반환하는 응답 DTO입니다.
 *
 * 객실 목록은 숙소별 객실 목록 조회 API에서 별도로 반환합니다.
 *
 * status는 관리자 조회에서만 채워집니다. 일반 사용자 응답에서는 null이며
 * NON_NULL 설정에 따라 필드 자체가 직렬화되지 않습니다.
 *
 * description 등 다른 필드는 원래도 null을 그대로 응답하던 값이라
 * NON_NULL은 status에만 걸어 기존 응답 형태를 그대로 유지합니다.
 */
public record AccommodationDetailResponseDto(

    Long accommodationId,

    String name,

    String address,

    String description,

    LocalTime checkInTime,

    LocalTime checkOutTime,

    List<String> imageUrls,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    AccommodationStatus status

) {

    /**
     * Accommodation 엔티티를 공개용 숙소 상세 응답으로 변환합니다.
     */
    public static AccommodationDetailResponseDto from(
        Accommodation accommodation
    ) {
        return of(accommodation, false);
    }

    /**
     * 관리자 조회용 응답으로 변환합니다.
     * 공개 응답과 달리 숙소 운영 상태를 포함합니다.
     */
    public static AccommodationDetailResponseDto forAdmin(
        Accommodation accommodation
    ) {
        return of(accommodation, true);
    }

    private static AccommodationDetailResponseDto of(
        Accommodation accommodation,
        boolean includeStatus
    ) {
        return new AccommodationDetailResponseDto(
            accommodation.getId(),
            accommodation.getName(),
            accommodation.getAddress(),
            accommodation.getDescription(),
            accommodation.getCheckInTime(),
            accommodation.getCheckOutTime(),
            accommodation.getImages().stream()
                .map(AccommodationImage::getImageUrl)
                .toList(),
            includeStatus ? accommodation.getStatus() : null
        );
    }
}
