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

import com.roompick.domain.accommodation.facade.AccommodationFacade;
import com.roompick.domain.accommodation.repository.AccommodationElasticsearchLocationSearchRepository;
import com.roompick.domain.accommodation.repository.AccommodationSearchDocumentRepository;
import com.roompick.domain.accommodation.service.AccommodationElasticsearchLocationSearchService;
import com.roompick.domain.accommodation.service.AccommodationSearchReindexService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * Elasticsearch 서버가 없는 MYSQL 환경의 조건부 Bean 구성을 검증합니다.
 *
 * test 프로필은 MYSQL 위치 검색을 사용하며 외부 Elasticsearch 주소를
 * 제공하지 않습니다. 이 상태에서 전체 ApplicationContext가 생성되고
 * Elasticsearch 전용 Bean이 등록되지 않아야 합니다.
 */
@ActiveProfiles("test")
@SpringBootTest
class AccommodationMysqlSearchContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("MYSQL 검색 설정은 Elasticsearch 전용 Bean 없이 기동된다")
    void loadContextWithoutElasticsearchBeans() {
        assertThat(
            applicationContext.getBean(AccommodationFacade.class)
        ).isNotNull();

        assertThat(
            applicationContext.getBeansOfType(
                AccommodationElasticsearchLocationSearchService.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                AccommodationElasticsearchLocationSearchRepository.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                AccommodationSearchReindexService.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                AccommodationSearchDocumentRepository.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                ElasticsearchOperations.class
            )
        ).isEmpty();

        assertThat(
            applicationContext.getBeansOfType(
                ElasticsearchClient.class
            )
        ).isEmpty();

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
