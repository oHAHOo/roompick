package com.roompick.domain.accommodation.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

/**
 * 인기 숙소 조회 캐시의 삭제를 담당하는 Service입니다.
 *
 * 숙소의 공개 정보가 수정되거나 운영 상태가 변경된 경우
 * 기존 인기 숙소 응답이 노출되지 않도록 전체 캐시를 삭제합니다.
 */
@Service
public class PopularAccommodationCacheEvictionService {

    /**
     * 날짜와 조회 개수별로 생성된 인기 숙소 캐시를 모두 삭제합니다.
     *
     * CacheManager가 transactionAware로 설정되어 있으므로
     * 트랜잭션 안에서 호출하면 실제 삭제는 커밋 이후 수행됩니다.
     * 트랜잭션이 롤백되면 캐시도 삭제되지 않습니다.
     */
    @CacheEvict(
        cacheNames = "popularAccommodations",
        allEntries = false
    )
    public void evictAll() {
        // 캐시 삭제는 @CacheEvict가 처리합니다.
    }
}
