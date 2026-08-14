package com.roompick.domain.accommodation.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;

/**
 * Elasticsearch 숙소 검색 Document를 관리하는 Repository입니다.
 *
 * 숙소 검색 인덱스의 저장, 조회, 삭제 등
 * 기본 Document 관리에 사용합니다.
 *
 * geo_distance와 keyword를 조합한 실제 위치 검색은
 * 별도의 검색 Repository에서 ElasticsearchOperations를 사용하여 처리합니다.
 */
public interface AccommodationSearchDocumentRepository
    extends ElasticsearchRepository<AccommodationSearchDocument, Long> {
}
