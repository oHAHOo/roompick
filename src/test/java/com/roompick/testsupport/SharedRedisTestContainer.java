package com.roompick.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 전체가 공유하는 단일 Redis Testcontainers 인스턴스입니다.
 *
 * 테스트는 순차 실행되고 각 클래스가 @BeforeEach/@AfterEach에서 자신이
 * 사용한 키 또는 캐시를 직접 정리하므로, 컨테이너를 공유해도 클래스 간
 * 데이터 오염 없이 기동 오버헤드만 줄어듭니다.
 */
public final class SharedRedisTestContainer {

    private static final int REDIS_PORT = 6379;

    public static final GenericContainer<?> INSTANCE;

    static {
        INSTANCE = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine")
        ).withExposedPorts(REDIS_PORT);

        INSTANCE.start();
    }

    private SharedRedisTestContainer() {
    }

    public static String host() {
        return INSTANCE.getHost();
    }

    public static int port() {
        return INSTANCE.getMappedPort(REDIS_PORT);
    }
}
