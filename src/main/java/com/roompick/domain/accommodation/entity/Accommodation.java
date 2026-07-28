package com.roompick.domain.accommodation.entity;

import java.time.LocalTime;

import com.roompick.global.common.BaseTimeEntity;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
