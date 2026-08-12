package com.roompick.domain.accommodation.type;

/**
 * 위치 기반 숙소 검색에 사용할 검색 엔진을 정의합니다.
 *
 * 동일한 API에서 MySQL과 Elasticsearch 검색 경로를
 * 환경설정으로 선택할 수 있도록 사용합니다.
 */
public enum AccommodationLocationSearchEngine {

    MYSQL,
    ELASTICSEARCH
}
