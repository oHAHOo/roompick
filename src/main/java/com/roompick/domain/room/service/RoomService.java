package com.roompick.domain.room.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    /**
     * 특정 숙소에 소속된 객실 목록을 조회합니다.
     *
     * 숙소의 존재 여부는 AccommodationService에서 먼저 확인하므로
     * 객실이 없으면 빈 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<Room> findAllByAccommodationId(Long accommodationId) {
        return roomRepository.findAllByAccommodationId(accommodationId);
    }
}
