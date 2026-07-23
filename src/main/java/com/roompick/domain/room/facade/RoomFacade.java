package com.roompick.domain.room.facade;

import org.springframework.stereotype.Component;

import com.roompick.domain.room.dto.RoomDetailResponseDto;
import com.roompick.domain.room.entity.Room;
import com.roompick.domain.room.service.RoomService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoomFacade {

    private final RoomService roomService;

    /**
     * 객실과 소속 숙소를 조회하며 객실 상세 응답으로 변환합니다.
     */
    public RoomDetailResponseDto getRoomDetail(Long roomId) {
        Room room =  roomService.findById(roomId);

        return RoomDetailResponseDto.from(room);
    }
}
