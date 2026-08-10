package com.roompick.domain.accommodation.entity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RoomPick에 등록된 숙소 정보를 나타내는 Entity입니다.
 *
 * 생성·수정 시각은 BaseTimeEntity에서 상속받습니다.
 */
@Getter
@Entity
@Table(name = "accommodations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accommodation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accommodation_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccommodationStatus status;

    // 등록 순서(sortOrder) 오름차순이며, 첫 번째가 대표(썸네일) 이미지입니다.
    @OneToMany(
        mappedBy = "accommodation",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    @OrderBy("sortOrder ASC")
    private List<AccommodationImage> images = new ArrayList<>();

    /**
     * 새로운 숙소를 생성합니다.
     *
     * 숙소는 처음 등록될 때 항상 운영 중인 ACTIVE 상태로 시작합니다.
     */
    public static Accommodation create(
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime
    ) {
        validateName(name);
        validateAddress(address);
        validateTimes(checkInTime, checkOutTime);

        return new Accommodation(
            name,
            address,
            description,
            checkInTime,
            checkOutTime,
            AccommodationStatus.ACTIVE
        );
    }

    // 외부에서 생성자를 직접 사용하지 못하게 하고 create()만 사용하도록 제한합니다.
    private Accommodation(
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        AccommodationStatus status
    ) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.status = status;
    }

    /**
     * 숙소의 공개 정보를 수정합니다.
     *
     * 수정 전 필수값을 검증하고,
     * 검증에 성공한 경우에만 Entity 상태를 변경합니다.
     */
    public void updatePublicInformation(
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime
    ) {
        validateName(name);
        validateAddress(address);
        validateTimes(
            checkInTime,
            checkOutTime
        );

        this.name = name;
        this.address = address;
        this.description = description;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
    }

    /**
     * 숙소를 운영 중단 상태로 변경합니다.
     *
     * 비공개 전환된 숙소는 공개 목록과 인기 숙소 목록에서
     * 더 이상 노출되지 않아야 합니다.
     */
    public void inactivate() {
        this.status = AccommodationStatus.INACTIVE;
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
                AccommodationImage.create(
                    this,
                    imageUrl,
                    images.size()
                )
            );
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_NAME_REQUIRED);
        }
    }

    private static void validateAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_ADDRESS_REQUIRED);
        }
    }

    private static void validateTimes(LocalTime checkInTime, LocalTime checkOutTime) {
        if (checkInTime == null || checkOutTime == null) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_TIME_REQUIRED);
        }
    }
}
