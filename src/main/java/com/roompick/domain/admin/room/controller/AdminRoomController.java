package com.roompick.domain.admin.room.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.admin.room.dto.request.RoomCreateRequestDto;
import com.roompick.domain.admin.room.dto.response.RoomCreateResponseDto;
import com.roompick.domain.admin.room.facade.AdminRoomFacade;
import com.roompick.global.common.ApiResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/accommodations/{accommodationId}/rooms")
public class AdminRoomController {

    private final AdminRoomFacade adminRoomFacade;

    @PostMapping
    public ResponseEntity<ApiResponseDto<RoomCreateResponseDto>> createRoom(
        @PathVariable Long accommodationId,
        @Valid @RequestBody RoomCreateRequestDto request
    ) {
        RoomCreateResponseDto response =
            adminRoomFacade.createRoom(
                accommodationId,
                request
            );

        ResponseEntity<ApiResponseDto<RoomCreateResponseDto>> result =
            ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                    ApiResponseDto.success(
                        "객실이 등록되었습니다.",
                        response
                    )
                );

        return result;
    }
}
