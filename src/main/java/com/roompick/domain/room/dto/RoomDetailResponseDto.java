package com.roompick.domain.room.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomImage;
import com.roompick.domain.room.entity.RoomStatus;

/**
 * 객실 상세·예약 가능 여부 화면에 필요한
 * 객실 기본 정보를 반환하는 응답 DTO입니다.
 *
 * pricePerNight는 현재 적용 가격이고,
 * normalPricePerNight는 객실에 등록된 정상 가격입니다.
 *
 * status는 관리자 조회에서만 채워집니다. 일반 사용자 응답에서는 null이며
 * NON_NULL 설정에 따라 필드 자체가 직렬화되지 않습니다.
 *
 * description 등 다른 필드는 원래도 null을 그대로 응답하던 값이라
 * NON_NULL은 status에만 걸어 기존 응답 형태를 그대로 유지합니다.
 */
public record RoomDetailResponseDto(

    Long roomId,

    String roomNumber,

    String name,

    String description,

    long pricePerNight,

    long normalPricePerNight,

    boolean discountApplied,

    int standardCapacity,

    int maxCapacity,

    List<String> imageUrls,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    RoomStatus status

) {

    /**
     * Room 엔티티와 현재 적용 가격을
     * 공개용 객실 상세 응답으로 변환합니다.
     */
    public static RoomDetailResponseDto from(
        Room room,
        long appliedPricePerNight
    ) {
        return of(room, appliedPricePerNight, false);
    }

    /**
     * 관리자 조회용 응답으로 변환합니다.
     * 공개 응답과 달리 객실 운영 상태를 포함합니다.
     */
    public static RoomDetailResponseDto forAdmin(
        Room room,
        long appliedPricePerNight
    ) {
        return of(room, appliedPricePerNight, true);
    }

    private static RoomDetailResponseDto of(
        Room room,
        long appliedPricePerNight,
        boolean includeStatus
    ) {
        long normalPricePerNight =
            room.getPricePerNight();

        return new RoomDetailResponseDto(
            room.getId(),
            room.getRoomNumber(),
            room.getName(),
            room.getDescription(),
            appliedPricePerNight,
            normalPricePerNight,
            appliedPricePerNight
                < normalPricePerNight,
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            room.getImages().stream()
                .map(RoomImage::getImageUrl)
                .toList(),
            includeStatus ? room.getStatus() : null
        );
    }
}
