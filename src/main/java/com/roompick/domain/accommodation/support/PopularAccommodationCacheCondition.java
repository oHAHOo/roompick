package com.roompick.domain.accommodation.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인기 숙소 응답 캐시의 활성화 여부를 제공합니다.
 *
 * 운영 기본값은 true이며,
 * 성능 테스트에서는 설정값을 false로 변경해
 * 캐시 미적용 기준 성능을 동일한 코드로 측정할 수 있습니다.
 */
@Component
public class PopularAccommodationCacheCondition {

    private final boolean enabled;

    public PopularAccommodationCacheCondition(
        @Value(
            "${roompick.cache.popular-accommodations-enabled:true}"
        )
        boolean enabled
    ) {
        this.enabled = enabled;
    }

    /**
     * 인기 숙소 응답 캐시 사용 여부를 반환합니다.
     */
    public boolean isEnabled() {
        return enabled;
    }
}
