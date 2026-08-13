package com.roompick.domain.accommodation.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.ActiveProfiles;

import com.roompick.domain.accommodation.repository.AccommodationElasticsearchLocationSearchRepository;
import com.roompick.domain.accommodation.repository.AccommodationSearchDocumentRepository;
import com.roompick.domain.accommodation.service.AccommodationElasticsearchLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationSearchReindexService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * ELASTICSEARCH 위치 검색 설정의 조건부 Bean 구성을 검증합니다.
 *
 * 실제 Elasticsearch 서버를 호출하지 않고 Client, Repository,
 * 검색 및 재색인 Service가 함께 활성화되는지 확인합니다.
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = "roompick.search.location-engine=ELASTICSEARCH"
)
class AccommodationElasticsearchSearchContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("ELASTICSEARCH 검색 설정은 전용 인프라 Bean을 활성화한다")
    void loadContextWithElasticsearchBeans() {
        assertThat(
            applicationContext.getBean(ElasticsearchClient.class)
        ).isNotNull();

        assertThat(
            applicationContext.getBean(ElasticsearchOperations.class)
        ).isNotNull();

        assertThat(
            applicationContext.getBean(
                AccommodationSearchDocumentRepository.class
            )
        ).isNotNull();

        assertThat(
            applicationContext.getBean(
                AccommodationElasticsearchLocationSearchRepository.class
            )
        ).isNotNull();

        assertThat(
            applicationContext.getBean(
                AccommodationElasticsearchLocationSearchService.class
            )
        ).isNotNull();

        assertThat(
            applicationContext.getBean(
                AccommodationSearchReindexService.class
            )
        ).isNotNull();

        assertThat(
            applicationContext.getBeansOfType(
                ReactiveElasticsearchClient.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                ElasticsearchRepositoriesAutoConfiguration.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                ReactiveElasticsearchRepositoriesAutoConfiguration.class
            )
        ).isEmpty();
    }
}
