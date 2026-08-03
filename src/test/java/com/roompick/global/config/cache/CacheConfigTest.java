package com.roompick.global.config.cache;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

class CacheConfigTest {

    @Test
    void 캐시_전체_삭제_실패를_비즈니스_예외로_전달하지_않는다() {
        // given
        CacheConfig cacheConfig = new CacheConfig();
        CacheErrorHandler cacheErrorHandler =
            cacheConfig.errorHandler();
        Cache cache = mock(Cache.class);
        RuntimeException redisException =
            new RuntimeException("Redis connection failed");

        when(cache.getName())
            .thenReturn("popularAccommodations");

        // when & then
        assertThatCode(() ->
            cacheErrorHandler.handleCacheClearError(
                redisException,
                cache
            )
        ).doesNotThrowAnyException();
    }
}
