package com.roompick.domain.accommodation.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * 전체 숙소 목록의 페이지 정보를 반환하는 DTO입니다.
 *
 * 숙소 목록과 현재 페이지 번호, 페이지 크기,
 * 전체 숙소 수와 전체 페이지 수를 함께 반환합니다.
 */
public record AccommodationPageResponseDto(

    List<AccommodationListResponseDto> content,

    int pageNumber,

    int pageSize,

    long totalElements,

    int totalPages,

    boolean last

) {

    /**
     * Repository에서 조회한 숙소 Page를
     * API 응답용 페이지 DTO로 변환합니다.
     */
    public static AccommodationPageResponseDto from(
        Page<AccommodationListResponseDto> accommodationPage
    ) {
        return new AccommodationPageResponseDto(
            accommodationPage.getContent(),
            accommodationPage.getNumber(),
            accommodationPage.getSize(),
            accommodationPage.getTotalElements(),
            accommodationPage.getTotalPages(),
            accommodationPage.isLast()
        );
    }
}
