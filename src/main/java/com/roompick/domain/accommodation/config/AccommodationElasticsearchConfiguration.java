package com.roompick.domain.accommodation.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import com.roompick.domain.accommodation.repository.AccommodationSearchDocumentRepository;

/**
 * Elasticsearch가 위치 검색 엔진으로 선택된 경우에만
 * Elasticsearch Client와 검색용 Repository를 활성화합니다.
 *
 * Spring Boot의 Elasticsearch Repository 자동 스캔은 애플리케이션에서
 * 제외하고 이 설정이 필요한 Repository만 명시적으로 등록합니다.
 * MYSQL 환경에서는 Repository Proxy를 만들지 않으므로
 * Elasticsearch 서버 없이 애플리케이션을 기동할 수 있습니다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "roompick.search",
    name = "location-engine",
    havingValue = "ELASTICSEARCH"
)
@ImportAutoConfiguration({
    ElasticsearchRestClientAutoConfiguration.class,
    ElasticsearchClientAutoConfiguration.class,
    ElasticsearchDataAutoConfiguration.class
})
@EnableElasticsearchRepositories(
    basePackageClasses = AccommodationSearchDocumentRepository.class,
    includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = AccommodationSearchDocumentRepository.class
    )
)
public class AccommodationElasticsearchConfiguration {
}
