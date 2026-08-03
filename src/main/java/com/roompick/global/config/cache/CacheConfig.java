package com.roompick.global.config.cache;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring Cache 기능을 활성화하고
 * Redis 기반 캐시 정책을 설정하는 클래스입니다.
 */
@Slf4j
@EnableCaching
@Configuration
public class CacheConfig implements CachingConfigurer {

    private static final String POPULAR_ACCOMMODATIONS_CACHE =
        "popularAccommodations";

    /**
     * 인기 숙소 조회 결과를 Redis에 JSON 형식으로 저장합니다.
     *
     * 인기 숙소 캐시 TTL은 설정 파일에서 주입받습니다.
     * 별도의 설정이 없으면 기본값으로 60초를 사용합니다.
     *
     * 테스트에서는 해당 설정값을 1초처럼 짧게 재정의하여
     * 실제로 60초를 기다리지 않고 캐시 만료를 검증할 수 있습니다.
     */
    @Bean
    public RedisCacheManager cacheManager(
        RedisConnectionFactory redisConnectionFactory,

        /*
         * 인기 숙소 캐시의 TTL을 설정값으로 주입받습니다.
         *
         * roompick.cache.popular-accommodations-ttl 설정이 없으면
         * 기본값인 60초가 적용됩니다.
         *
         * Spring은 "60s", "1s" 등의 문자열을
         * Duration 객체로 자동 변환합니다.
         */
        @Value(
            "${roompick.cache.popular-accommodations-ttl:60s}"
        )
        Duration popularAccommodationsTtl
    ) {
        /*
         * 모든 Redis 캐시에서 공통으로 사용할
         * 기본 직렬화 정책을 설정합니다.
         */
        RedisCacheConfiguration defaultConfiguration =
            RedisCacheConfiguration.defaultCacheConfig()

                /*
                 * null 결과는 캐시에 저장하지 않습니다.
                 *
                 * 일시적으로 조회 결과가 없었던 상황이
                 * 캐시에 남아 정상 데이터 조회를 막는 것을 방지합니다.
                 */
                .disableCachingNullValues()

                /*
                 * Redis 캐시 Key는 사람이 확인하기 쉬운
                 * 문자열 형태로 저장합니다.
                 */
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new StringRedisSerializer()
                        )
                )

                /*
                 * 캐시 Value는 DTO 목록을 저장할 수 있도록
                 * JSON 형태로 직렬화합니다.
                 *
                 * Entity가 아닌 조회 응답 DTO만 캐싱합니다.
                 */
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair
                        .fromSerializer(
                            new GenericJackson2JsonRedisSerializer()
                        )
                );

        /*
         * 인기 숙소 캐시에만 적용할 별도 설정입니다.
         *
         * 기본 직렬화 정책은 그대로 사용하고,
         * 외부 설정에서 주입받은 TTL을 추가합니다.
         */
        RedisCacheConfiguration
            popularAccommodationConfiguration =
            defaultConfiguration.entryTtl(
                popularAccommodationsTtl
            );

        /*
         * Redis 기반 CacheManager를 생성합니다.
         */
        return RedisCacheManager.builder(
                redisConnectionFactory
            )

            /*
             * 별도 설정이 지정되지 않은 캐시에서 사용할
             * 기본 설정을 등록합니다.
             */
            .cacheDefaults(
                defaultConfiguration
            )

            /*
             * popularAccommodations 캐시에만
             * TTL이 포함된 별도 설정을 적용합니다.
             */
            .withInitialCacheConfigurations(
                Map.of(
                    POPULAR_ACCOMMODATIONS_CACHE,
                    popularAccommodationConfiguration
                )
            )

            /*
             * 트랜잭션 안에서 캐시 삭제 또는 저장이 요청되면
             * 실제 캐시 작업은 트랜잭션 완료 시점에 수행합니다.
             *
             * 트랜잭션이 롤백된 경우 캐시 변경도 수행되지 않아
             * DB 데이터와 캐시의 불일치를 방지합니다.
             */
            .transactionAware()

            /*
             * 미리 등록하지 않은 이름의 캐시가
             * 실수로 자동 생성되는 것을 방지합니다.
             */
            .disableCreateOnMissingCache()

            /*
             * 위 설정을 기준으로 CacheManager를 생성합니다.
             */
            .build();
    }

    /**
     * Redis 캐시 작업 중 오류가 발생해도
     * 실제 인기 숙소 조회 로직을 계속 실행할 수 있도록 합니다.
     *
     * 캐시 조회·저장·삭제 실패는 로그로 남기고
     * 예외를 다시 던지지 않습니다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(
                RuntimeException exception,
                Cache cache,
                Object key
            ) {
                log.warn(
                    "캐시 조회에 실패했습니다. cacheName={}, key={}",
                    cache.getName(),
                    key,
                    exception
                );
            }

            @Override
            public void handleCachePutError(
                RuntimeException exception,
                Cache cache,
                Object key,
                Object value
            ) {
                log.warn(
                    "캐시 저장에 실패했습니다. cacheName={}, key={}",
                    cache.getName(),
                    key,
                    exception
                );
            }

            @Override
            public void handleCacheEvictError(
                RuntimeException exception,
                Cache cache,
                Object key
            ) {
                log.warn(
                    "캐시 삭제에 실패했습니다. cacheName={}, key={}",
                    cache.getName(),
                    key,
                    exception
                );
            }

            @Override
            public void handleCacheClearError(
                RuntimeException exception,
                Cache cache
            ) {
                log.warn(
                    "캐시 전체 삭제에 실패했습니다. cacheName={}",
                    cache.getName(),
                    exception
                );
            }
        };
    }
}
