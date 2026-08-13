package com.roompick.domain.accommodation.config;

import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ReactiveElasticsearchClientAutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * MYSQL 위치 검색 환경에서 Elasticsearch 자동 구성을 제외합니다.
 *
 * ELASTICSEARCH가 선택된 경우에는 전용 설정의
 * ImportAutoConfiguration이 Spring Boot 조건과 순서에 따라 동작하도록
 * 자동 구성 후보를 유지합니다.
 */
public class AccommodationElasticsearchAutoConfigurationImportFilter
    implements AutoConfigurationImportFilter, EnvironmentAware {

    private static final String LOCATION_ENGINE_PROPERTY =
        "roompick.search.location-engine";

    private static final Set<String>
        ALWAYS_EXCLUDED_ELASTICSEARCH_AUTO_CONFIGURATIONS = Set.of(
        ElasticsearchRepositoriesAutoConfiguration.class.getName(),
        ReactiveElasticsearchRepositoriesAutoConfiguration.class.getName(),
        ReactiveElasticsearchClientAutoConfiguration.class.getName()
    );

    private static final Set<String>
        ELASTICSEARCH_INFRASTRUCTURE_AUTO_CONFIGURATIONS = Set.of(
        ElasticsearchRestClientAutoConfiguration.class.getName(),
        ElasticsearchClientAutoConfiguration.class.getName(),
        ElasticsearchDataAutoConfiguration.class.getName()
    );

    private Environment environment;

    /**
     * 현재 위치 검색 엔진에 따라 Elasticsearch 자동 구성 후보를 결정합니다.
     */
    @Override
    public boolean[] match(
        String[] autoConfigurationClasses,
        AutoConfigurationMetadata autoConfigurationMetadata
    ) {
        boolean elasticsearchEnabled = "ELASTICSEARCH".equalsIgnoreCase(
            environment.getProperty(
                LOCATION_ENGINE_PROPERTY,
                "MYSQL"
            )
        );

        boolean[] matches = new boolean[autoConfigurationClasses.length];

        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfigurationClass =
                autoConfigurationClasses[index];

            matches[index] = autoConfigurationClass == null ||
                !ALWAYS_EXCLUDED_ELASTICSEARCH_AUTO_CONFIGURATIONS.contains(
                    autoConfigurationClass
                ) && (
                    elasticsearchEnabled ||
                    !ELASTICSEARCH_INFRASTRUCTURE_AUTO_CONFIGURATIONS.contains(
                        autoConfigurationClass
                    )
                );
        }

        return matches;
    }

    /**
     * 위치 검색 엔진 설정을 조회할 Environment를 전달받습니다.
     */
    @Override
    public void setEnvironment(
        Environment environment
    ) {
        this.environment = environment;
    }
}
