package com.roompick.domain.accommodation.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.roompick.domain.accommodation.service.AccommodationSearchReindexService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 환경에서 Elasticsearch 숙소 검색 인덱스를
 * 필요할 때만 전체 재색인하는 Runner입니다.
 *
 * 기본 실행에서는 동작하지 않으며,
 * roompick.search.reindex-enabled=true 설정이 전달된 경우에만
 * 애플리케이션 시작 후 재색인을 수행합니다.
 *
 * 공개 API로 전체 재색인 기능을 노출하지 않으면서
 * 로컬 개발과 성능 측정 시 재현 가능한 실행 경로를 제공합니다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "roompick.search",
    name = "reindex-enabled",
    havingValue = "true"
)
public class AccommodationSearchReindexRunner
    implements ApplicationRunner {

    private final AccommodationSearchReindexService
        accommodationSearchReindexService;

    /**
     * 애플리케이션 시작 후 Elasticsearch 전체 재색인을 수행합니다.
     */
    @Override
    public void run(
        ApplicationArguments args
    ) {
        log.info(
            "로컬 Elasticsearch 숙소 검색 인덱스 재색인을 시작합니다."
        );

        long indexedCount =
            accommodationSearchReindexService.reindexAll();

        log.info(
            "로컬 Elasticsearch 숙소 검색 인덱스 재색인을 완료했습니다. indexedCount={}",
            indexedCount
        );
    }
}
