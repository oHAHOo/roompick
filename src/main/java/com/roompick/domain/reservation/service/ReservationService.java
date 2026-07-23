package com.roompick.domain.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.reservation.repository.ReservationRepository;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 예약 날짜 검증과 활성 예약 조회를 담당하는 Service입니다.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final ZoneId SERVICE_ZONE_ID =
        ZoneId.of("Asia/Seoul");

    private final ReservationRepository reservationRepository;

    /**
     * 요청한 숙박 기간에 객실을 예약할 수 있는지 확인합니다.
     */
    @Transactional(readOnly = true)
    public boolean isRoomAvailable(
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        LocalDateTime now =
            LocalDateTime.now(SERVICE_ZONE_ID);

        validateStayPeriod(
            checkInDate,
            checkOutDate,
            now.toLocalDate()
        );

        boolean overlappingReservationExists =
            reservationRepository.existsActiveOverlappingReservation(
                roomId,
                checkInDate,
                checkOutDate,
                now
            );

        return !overlappingReservationExists;
    }

    /**
     * 체크인·체크아웃 날짜가 숙박 정책에 맞는지 검증합니다.
     */
    private void validateStayPeriod(
        LocalDate checkInDate,
        LocalDate checkOutDate,
        LocalDate today
    ) {
        if (
            checkInDate == null
                || checkOutDate == null
                || checkInDate.isBefore(today)
                || !checkInDate.isBefore(checkOutDate)
        ) {
            throw new BusinessException(
                ErrorCode.INVALID_STAY_PERIOD
            );
        }
    }
}
