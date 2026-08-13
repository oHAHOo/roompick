package com.roompick.domain.accommodation.entity;

import java.math.BigDecimal;
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

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

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

    /**
     * 숙소 위도입니다.
     *
     * 기존 숙소에는 좌표가 없을 수 있으므로 nullable로 유지합니다.
     * 위치 검색에서는 위도와 경도가 모두 존재하는 숙소만 대상으로 합니다.
     */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    /**
     * 숙소 경도입니다.
     *
     * 기존 숙소에는 좌표가 없을 수 있으므로 nullable로 유지합니다.
     */
    @Column(precision = 10, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccommodationStatus status;

    /*
     * 등록 순서(sortOrder) 오름차순이며, 첫 번째가 대표(썸네일) 이미지입니다.
     * LAZY이므로 전체 이미지가 필요한 상세 조회는
     * AccommodationRepository.findByIdWithImages()의 fetch join으로 초기화한다.
     */
    @OneToMany(
        mappedBy = "accommodation",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @OrderBy("sortOrder ASC")
    private List<AccommodationImage> images = new ArrayList<>();

    /**
     * 새로운 숙소를 생성합니다.
     *
     * 숙소는 처음 등록될 때 항상 운영 중인 ACTIVE 상태로 시작합니다.
     *
     * 현재 위치 정보 입력은 별도 단계로 분리되어 있으므로
     * 최초 생성 시 좌표는 null일 수 있습니다.
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
            null,
            null,
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
        BigDecimal latitude,
        BigDecimal longitude,
        AccommodationStatus status
    ) {
        this.name = name;
        this.address = address;
        this.description = description;
        this.checkInTime = checkInTime;
        this.checkOutTime = checkOutTime;
        this.latitude = latitude;
        this.longitude = longitude;
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
     * 숙소 위치 좌표를 변경합니다.
     *
     * 위도와 경도는 항상 한 쌍으로 관리하며,
     * 지구 좌표 범위를 벗어나는 값은 허용하지 않습니다.
     */
    public void updateLocation(
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        validateLocation(latitude, longitude);

        this.latitude = latitude;
        this.longitude = longitude;
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

    private static void validateTimes(
        LocalTime checkInTime,
        LocalTime checkOutTime
    ) {
        if (checkInTime == null || checkOutTime == null) {
            throw new BusinessException(ErrorCode.ACCOMMODATION_TIME_REQUIRED);
        }
    }

    /**
     * 위치 좌표를 검증합니다.
     *
     * 위치 검색의 정확성을 위해 위도와 경도 중 하나만 존재하는 상태는
     * 허용하지 않습니다.
     */
    private static void validateLocation(
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        if (latitude == null || longitude == null) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_LOCATION_REQUIRED
            );
        }

        if (
            latitude.compareTo(MIN_LATITUDE) < 0
                || latitude.compareTo(MAX_LATITUDE) > 0
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_LATITUDE_OUT_OF_RANGE
            );
        }

        if (
            longitude.compareTo(MIN_LONGITUDE) < 0
                || longitude.compareTo(MAX_LONGITUDE) > 0
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_LONGITUDE_OUT_OF_RANGE
            );
        }
    }
}
