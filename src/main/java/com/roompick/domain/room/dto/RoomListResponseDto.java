package com.roompick.domain.room.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.entity.RoomImage;
import com.roompick.domain.room.entity.RoomStatus;

/**
 * 숙소별 객실 목록의 객실 요약 정보를 반환하는 DTO입니다.
 *
 * pricePerNight는 현재 적용 가격이고,
 * normalPricePerNight는 객실에 등록된 정상 가격입니다.
 *
 * imageUrl은 등록된 이미지 중 첫 번째 이미지입니다.
 *
 * status는 관리자 조회에서만 채워집니다. 일반 사용자 응답은 항상 ACTIVE 객실만
 * 반환하므로 null이며 NON_NULL 설정에 따라 필드 자체가 직렬화되지 않습니다.
 *
 * imageUrl은 이미지가 없으면 원래도 null을 그대로 응답하던 필드라
 * NON_NULL은 status에만 걸어 기존 응답 형태를 그대로 유지합니다.
 */
public record RoomListResponseDto(

    Long roomId,

    String name,

    long pricePerNight,

    long normalPricePerNight,

    boolean discountApplied,

    int standardCapacity,

    int maxCapacity,

    String imageUrl,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    RoomStatus status

) {

    /**
     * 기존 Repository DTO 직접 조회(공개 목록)에서 사용합니다.
     */
    public RoomListResponseDto(
        Long roomId,
        String name,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity,
        String imageUrl
    ) {
        this(
            roomId,
            name,
            pricePerNight,
            pricePerNight,
            false,
            standardCapacity,
            maxCapacity,
            imageUrl,
            null
        );
    }

    /**
     * 관리자 목록 조회에서 객실 상태를 함께 담아야 할 때 사용합니다.
     */
    public RoomListResponseDto(
        Long roomId,
        String name,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity,
        String imageUrl,
        RoomStatus status
    ) {
        this(
            roomId,
            name,
            pricePerNight,
            pricePerNight,
            false,
            standardCapacity,
            maxCapacity,
            imageUrl,
            status
        );
    }

    /**
     * 대표 이미지 조회가 필요 없는 테스트 등에서 사용합니다.
     */
    public RoomListResponseDto(
        Long roomId,
        String name,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity
    ) {
        this(
            roomId,
            name,
            pricePerNight,
            pricePerNight,
            false,
            standardCapacity,
            maxCapacity,
            null,
            null
        );
    }

    /**
     * 객실과 현재 적용 가격을 객실 목록 응답으로 변환합니다.
     */
    public static RoomListResponseDto from(
        Room room,
        long appliedPricePerNight
    ) {
        long normalPricePerNight =
            room.getPricePerNight();

        String imageUrl =
            findRepresentativeImageUrl(room);

        return new RoomListResponseDto(
            room.getId(),
            room.getName(),
            appliedPricePerNight,
            normalPricePerNight,
            appliedPricePerNight
                < normalPricePerNight,
            room.getStandardCapacity(),
            room.getMaxCapacity(),
            imageUrl,
            null
        );
    }

    /**
     * Repository에서 한 번에 조회한 객실 요약 정보에
     * 배치 계산된 현재 적용 가격을 반영합니다.
     */
    public RoomListResponseDto withAppliedPrice(
        long appliedPricePerNight
    ) {
        return new RoomListResponseDto(
            roomId,
            name,
            appliedPricePerNight,
            normalPricePerNight,
            appliedPricePerNight < normalPricePerNight,
            standardCapacity,
            maxCapacity,
            imageUrl,
            status
        );
    }

    /**
     * 정렬된 객실 이미지 중 첫 번째 이미지를
     * 대표 이미지로 반환합니다.
     */
    private static String findRepresentativeImageUrl(
        Room room
    ) {
        return room.getImages().stream()
            .findFirst()
            .map(RoomImage::getImageUrl)
            .orElse(null);
    }
}
