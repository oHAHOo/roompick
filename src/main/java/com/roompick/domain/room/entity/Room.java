package com.roompick.domain.room.entity;

import java.util.ArrayList;
import java.util.List;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.global.common.BaseTimeEntity;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 실제로 예약되는 물리적 객실 하나를 나타내는 Entity입니다.
 *
 * 생성·수정 시각은 BaseTimeEntity에서 상속받습니다.
 */
@Getter
@Entity
@Table(
    name = "rooms",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_rooms_accommodation_room_number",
            columnNames = {"accommodation_id", "room_number"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    // 객실이 소속된 숙소이며, 실제로 필요할 때만 조회합니다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accommodation_id", nullable = false)
    private Accommodation accommodation;

    @Column(name = "room_number", nullable = false, length = 30)
    private String roomNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_per_night", nullable = false)
    private long pricePerNight;

    // 객실의 기본 이용 인원
    @Column(name = "standard_capacity", nullable = false)
    private int standardCapacity;

    // 객실에서 허용하는 최대 이용 인원
    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    /*
     * 등록 순서(sortOrder) 오름차순이며, 첫 번째가 대표(썸네일) 이미지입니다.
     * LAZY이므로 전체 이미지가 필요한 상세 조회는
     * RoomRepository.findPublicById()의 fetch join으로 초기화한다.
     */
    @OneToMany(
        mappedBy = "room",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("sortOrder ASC")
    private List<RoomImage> images = new ArrayList<>();

    /**
     * 새로운 객실을 생성합니다.
     *
     * 관리자가 공개 여부를 결정할 수 있도록 INACTIVE 상태로 시작합니다.
     */
    public static Room create(
        Accommodation accommodation,
        String roomNumber,
        String name,
        String description,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity
    ) {
        validateAccommodation(accommodation);
        validateRoomNumber(roomNumber);
        validateName(name);
        validatePrice(pricePerNight);
        validateCapacity(standardCapacity, maxCapacity);

        return new Room(
            accommodation,
            roomNumber,
            name,
            description,
            pricePerNight,
            standardCapacity,
            maxCapacity,
            RoomStatus.INACTIVE
        );
    }

    /**
     * 사용자에게 객실을 공개합니다.
     */
    public void activate() {
        this.status = RoomStatus.ACTIVE;
    }

    /**
     * 사용자에게 객실을 노출하지 않도록 변경합니다.
     */
    public void deactivate() {
        this.status = RoomStatus.INACTIVE;
    }

    /**
     * 등록 시점에 업로드된 이미지 URL을 순서대로 추가합니다.
     */
    public void addImages(List<String> imageUrls) {
        if (imageUrls == null) {
            return;
        }

        for (String imageUrl : imageUrls) {
            images.add(
                RoomImage.create(
                    this,
                    imageUrl,
                    images.size()
                )
            );
        }
    }

    private Room(
        Accommodation accommodation,
        String roomNumber,
        String name,
        String description,
        long pricePerNight,
        int standardCapacity,
        int maxCapacity,
        RoomStatus status
    ) {
        this.accommodation = accommodation;
        this.roomNumber = roomNumber;
        this.name = name;
        this.description = description;
        this.pricePerNight = pricePerNight;
        this.standardCapacity = standardCapacity;
        this.maxCapacity = maxCapacity;
        this.status = status;
    }

    private static void validateAccommodation(Accommodation accommodation) {
        if (accommodation == null) {
            throw new BusinessException(ErrorCode.ROOM_ACCOMMODATION_REQUIRED);
        }
    }

    private static void validateRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new BusinessException(ErrorCode.ROOM_NUMBER_REQUIRED);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.ROOM_NAME_REQUIRED);
        }
    }

    private static void validatePrice(long pricePerNight) {
        if (pricePerNight < 0) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_PRICE);
        }
    }

    private static void validateCapacity(int standardCapacity, int maxCapacity) {
        // 기준 인원은 1명 이상이고, 최대 인원은 기준 인원보다 작을 수 없습니다.
        if (standardCapacity < 1 || maxCapacity < standardCapacity) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_CAPACITY);
        }
    }
}
