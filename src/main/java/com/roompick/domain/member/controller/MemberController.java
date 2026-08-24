package com.roompick.domain.member.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roompick.domain.member.dto.MemberProfileResponseDto;
import com.roompick.domain.member.facade.MemberFacade;
import com.roompick.global.common.ApiResponseDto;
import com.roompick.global.security.AuthMember;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberFacade memberFacade;

    /**
     * 인증된 회원 자신의 프로필을 조회합니다.
     *
     * 액세스 토큰에는 회원 ID와 역할만 담겨 있어 이메일 등은 알 수 없으므로,
     * 결제창 호출처럼 구매자 이메일이 필요한 화면에서 이 API로 조회한다.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<MemberProfileResponseDto>> getMyProfile(
        @AuthenticationPrincipal AuthMember authMember
    ) {
        MemberProfileResponseDto result = memberFacade.getMyProfile(authMember.memberId());

        ResponseEntity<ApiResponseDto<MemberProfileResponseDto>> response = ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponseDto.success("내 프로필 조회에 성공했습니다.", result));

        return response;
    }
}
