package com.roompick.domain.accommodation.dto;

import java.time.LocalTime;
import java.util.List;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationImage;

/**
 * 숙소 상세 화면에 필요한 숙소 기본 정보를 반환하는 응답 DTO입니다.
 *
 * 객실 목록은 숙소별 객실 목록 조회 API에서 별도로 반환합니다.
 */
public record AccommodationDetailResponseDto(

    Long accommodationId,

    String name,

    String address,

    String description,

    LocalTime checkInTime,

    LocalTime checkOutTime,

    List<String> imageUrls

) {

    /**
     * Accommodation 엔티티를 숙소 상세 응답 DTO로 변환합니다.
     */
    public static AccommodationDetailResponseDto from(
        Accommodation accommodation
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
                .toList()
        );
    }
}
