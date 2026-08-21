package com.roompick.domain.accommodation.service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.accommodation.dto.AccommodationListResponseDto;
import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationRepository;
import com.roompick.domain.room.repository.RoomRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final RoomRepository roomRepository;
    private final PopularAccommodationCacheEvictionService
        popularAccommodationCacheEvictionService;

    @Transactional(readOnly = true)
    public Accommodation findById(Long accommodationId) {
        return accommodationRepository.findById(accommodationId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.ACCOMMODATION_NOT_FOUND
                )
            );
    }

    /**
     * 숙소 전체 타임세일 등록 트랜잭션에서
     * 대상 숙소 행에 비관적 쓰기 락을 획득합니다.
     *
     * 락 획득부터 기간 중복 검사와 저장까지 같은
     * Facade 트랜잭션에서 처리하기 위해 기존 트랜잭션을 필수로 합니다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Accommodation findByIdForTimeSaleUpdate(
        Long accommodationId
    ) {
        return accommodationRepository
            .findByIdForUpdate(accommodationId)
            .orElseThrow(() ->
                new BusinessException(
                    ErrorCode.ACCOMMODATION_NOT_FOUND
                )
            );
    }

    /**
     * 운영 중인 숙소를 ID로 조회합니다.
     *
     * 존재하지 않는 숙소는 숙소 없음 예외를 발생시키고,
     * 운영이 중단된 숙소는 객실 목록을 공개하지 않습니다.
     */
    @Transactional(readOnly = true)
    public Accommodation findActiveById(
        Long accommodationId
    ) {
        Accommodation accommodation =
            findById(accommodationId);

        if (
            accommodation.getStatus()
                != AccommodationStatus.ACTIVE
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_INACTIVE
            );
        }

        return accommodation;
    }

    /**
     * 운영 중인 숙소를 이미지 전체 목록과 함께 조회합니다.
     *
     * 숙소 상세 조회에서만 이미지가 필요하므로
     * fetch join 전용 쿼리로 조회해 다른 조회 경로의
     * 이미지 로딩을 강제하지 않습니다.
     */
    @Transactional(readOnly = true)
    public Accommodation findActiveByIdWithImages(
        Long accommodationId
    ) {
        Accommodation accommodation =
            accommodationRepository
                .findByIdWithImages(accommodationId)
                .orElseThrow(() ->
                    new BusinessException(
                        ErrorCode.ACCOMMODATION_NOT_FOUND
                    )
                );

        if (
            accommodation.getStatus()
                != AccommodationStatus.ACTIVE
        ) {
            throw new BusinessException(
                ErrorCode.ACCOMMODATION_INACTIVE
            );
        }

        return accommodation;
    }

    /**
     * 운영 중인 숙소 목록을 페이지 단위로 조회합니다.
     *
     * 페이지 요청값을 검증한 뒤 목록 화면에 필요한 필드만
     * DTO로 직접 조회합니다.
     */
    @Transactional(readOnly = true)
    public Page<AccommodationListResponseDto> findAllActive(
        int page,
        int size
    ) {
        validatePageRequest(page, size);

        PageRequest pageable = PageRequest.of(
            page,
            size
        );

        return accommodationRepository.findAllActive(pageable);
    }

    /**
     * 전달받은 숙소 ID 중 운영 중인 숙소의 공개 요약 정보를 조회합니다.
     *
     * ID 목록 전체를 IN 조건으로 한 번에 조회하여
     * 숙소 개수만큼 반복 SELECT가 발생하지 않도록 합니다.
     *
     * Redis 랭킹 데이터가 비어 있으면 DB 조회를 생략하고
     * 즉시 빈 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<AccommodationListResponseDto>
    findAllActiveSummaryByIds(
        List<Long> accommodationIds
    ) {
        if (
            accommodationIds == null
                || accommodationIds.isEmpty()
        ) {
            return List.of();
        }

        return accommodationRepository
            .findAllActiveSummaryByIdIn(
                accommodationIds
            );
    }

    /**
     * Redis 인기 랭킹 장애 시 제공할 최신 운영 숙소를 조회합니다.
     *
     * 실제 인기 순위가 아니라 API 응답을 유지하기 위한 임시 fallback이며,
     * 등록일이 최신인 ACTIVE 숙소를 요청한 개수만큼만 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<AccommodationListResponseDto> findLatestActive(
        int limit
    ) {
        PageRequest pageable = PageRequest.of(
            0,
            limit
        );

        return accommodationRepository.findLatestActive(
            pageable
        );
    }

    /**
     * 숙소의 공개 정보를 수정합니다.
     *
     * Entity 변경이 정상적으로 완료된 뒤 인기 숙소 캐시 삭제를 요청합니다.
     * CacheManager의 transactionAware 설정에 따라 실제 삭제는
     * 트랜잭션 커밋 이후 수행되며, 롤백되면 삭제되지 않습니다.
     */
    @Transactional
    public Accommodation updatePublicInformation(
        Long accommodationId,
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime
    ) {
        Accommodation accommodation =
            findById(accommodationId);

        accommodation.updatePublicInformation(
            name,
            address,
            description,
            checkInTime,
            checkOutTime
        );

        popularAccommodationCacheEvictionService.evictAll();

        return accommodation;
    }

    /**
     * 숙소와 소속 객실을 하나의 트랜잭션에서 비활성화합니다.
     *
     * 하위 객실은 Entity 전체를 조회하지 않고 벌크 UPDATE하며,
     * 커밋 이후 기존 인기 숙소 캐시를 삭제합니다.
     */
    @Transactional
    public void inactivateAccommodation(
        Long accommodationId
    ) {
        Accommodation accommodation =
            findById(accommodationId);

        accommodation.inactivate();

        roomRepository.deactivateAllByAccommodationId(
            accommodationId
        );

        popularAccommodationCacheEvictionService.evictAll();
    }

    @Transactional
    public Accommodation createAccommodation(
        String name,
        String address,
        String description,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        BigDecimal latitude,
        BigDecimal longitude,
        List<String> imageUrls
    ) {
        Accommodation accommodation =
            Accommodation.create(
                name,
                address,
                description,
                checkInTime,
                checkOutTime,
                latitude,
                longitude
            );

        accommodation.addImages(imageUrls);

        return accommodationRepository.save(accommodation);
    }

    /**
     * 숙소 목록의 페이지 번호와 크기가 허용 범위인지 확인합니다.
     */
    private void validatePageRequest(
        int page,
        int size
    ) {
        if (
            page < 0
                || size < 1
                || size > 100
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }
}
