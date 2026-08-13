package com.roompick.domain.admin.timesale.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.entity.Accommodation;
import com.roompick.domain.accommodation.service.AccommodationService;
import com.roompick.domain.admin.timesale.dto.request.TimeSaleCreateRequestDto;
import com.roompick.domain.admin.timesale.dto.response.TimeSaleCreateResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;
import com.roompick.domain.timesale.entity.TimeSale;
import com.roompick.domain.timesale.service.TimeSaleService;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 타임세일 등록 흐름을 조율합니다.
 *
 * 숙소 전체 타임세일과 특정 객실 타임세일을
 * 하나의 등록 API에서 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class AdminTimeSaleFacade {

    private final AccommodationService
        accommodationService;

    private final RoomService roomService;

    private final TimeSaleService timeSaleService;

    /**
     * 숙소 전체 또는 특정 객실에 적용할
     * 타임세일을 등록합니다.
     *
     * roomId가 null이면 숙소 전체 타임세일로
     * 등록합니다.
     */
    public TimeSaleCreateResponseDto create(
        Long accommodationId,
        TimeSaleCreateRequestDto request
    ) {
        Accommodation accommodation =
            accommodationService.findById(
                accommodationId
            );

        Room room = findTargetRoom(
            accommodationId,
            request.roomId()
        );

        TimeSale timeSale =
            timeSaleService.create(
                accommodation,
                room,
                request.discountRate(),
                request.startAt(),
                request.endAt()
            );

        return TimeSaleCreateResponseDto.from(
            timeSale
        );
    }

    /**
     * roomId가 존재할 때만 지정한 숙소에 소속된
     * 객실을 조회합니다.
     */
    private Room findTargetRoom(
        Long accommodationId,
        Long roomId
    ) {
        if (roomId == null) {
            return null;
        }

        return roomService
            .findByIdAndAccommodationIdForAdmin(
                accommodationId,
                roomId
            );
    }
}
