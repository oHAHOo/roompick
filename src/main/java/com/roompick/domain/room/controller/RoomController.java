package com.roompick.domain.room.controller;

import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.room.dto.RoomDetailResponseDto;
import com.roompick.domain.room.facade.RoomFacade;
import com.roompick.global.common.ApiResponseDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomFacade roomFacade;

    /**
     * 객실과 소속 숙소의 상세 정보를 조회합니다.
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponseDto<RoomDetailResponseDto>> getRoomDetail(@PathVariable Long roomId) {
        RoomDetailResponseDto result = roomFacade.getRoomDetail(roomId);

        ResponseEntity<ApiResponseDto<RoomDetailResponseDto>> response =
            ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponseDto.success(
                    "객실 상세 조회에 성공했습니다.",
                    result
                ));

        return response;
    }
}
