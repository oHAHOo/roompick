package com.roompick.domain.accommodation.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.stereotype.Service;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;
import com.roompick.domain.accommodation.repository.AccommodationSearchIndexProjection;
import com.roompick.domain.accommodation.repository.AccommodationSearchIndexRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MySQL 숙소 데이터를 Elasticsearch 검색 인덱스로
 * 전체 재색인하는 Service입니다.
 *
 * Accommodation Entity 전체를 한 번에 메모리에 올리지 않고,
 * ID 기반 Keyset Pagination으로 일정 개수씩 조회한 뒤
 * Elasticsearch Bulk 작업으로 저장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationSearchReindexService {

    /**
     * 한 번에 MySQL에서 조회하고 Elasticsearch에 저장할 숙소 개수입니다.
     *
     * 너무 많은 데이터를 한 번에 메모리에 올리거나
     * Elasticsearch Bulk 요청 크기가 지나치게 커지는 것을 방지합니다.
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * Keyset Pagination의 최초 시작 ID입니다.
     */
    private static final long INITIAL_ACCOMMODATION_ID = 0L;

    private final AccommodationSearchIndexRepository
        accommodationSearchIndexRepository;

    private final ElasticsearchOperations
        elasticsearchOperations;

    /**
     * MySQL을 Source of Truth로 사용하여
     * Elasticsearch 숙소 검색 인덱스를 처음부터 다시 생성합니다.
     *
     * 기존 인덱스를 제거한 뒤 현재 Document 매핑으로 다시 생성하므로,
     * MySQL에서 삭제되었거나 좌표가 제거된 숙소가
     * Elasticsearch에 오래 남는 문제도 함께 정리합니다.
     *
     * @return Elasticsearch에 재색인한 전체 숙소 수
     */
    public long reindexAll() {
        log.info(
            "숙소 검색 Elasticsearch 전체 재색인을 시작합니다."
        );

        recreateIndex();

        long lastAccommodationId =
            INITIAL_ACCOMMODATION_ID;

        long indexedCount = 0L;

        while (true) {
            /*
             * OFFSET을 사용하지 않고 마지막 처리 ID 이후부터
             * 다음 배치를 조회합니다.
             */
            List<AccommodationSearchIndexProjection> batch =
                accommodationSearchIndexRepository
                    .findSearchIndexBatchAfterId(
                        lastAccommodationId,
                        PageRequest.of(
                            0,
                            BATCH_SIZE
                        )
                    );

            if (batch.isEmpty()) {
                break;
            }

            /*
             * MySQL Projection을 Elasticsearch Bulk 요청으로 변환합니다.
             */
            List<IndexQuery> indexQueries =
                batch
                    .stream()
                    .map(this::toIndexQuery)
                    .toList();

            /*
             * 숙소를 한 건씩 Elasticsearch에 저장하지 않고
             * 현재 배치를 한 번의 Bulk 작업으로 저장합니다.
             */
            elasticsearchOperations.bulkIndex(
                indexQueries,
                AccommodationSearchDocument.class
            );

            /*
             * 현재 배치의 마지막 숙소 ID를 저장하여
             * 다음 Keyset 조회 시작점으로 사용합니다.
             */
            AccommodationSearchIndexProjection
                lastAccommodation =
                batch.get(
                    batch.size() - 1
                );

            lastAccommodationId =
                lastAccommodation.getAccommodationId();

            indexedCount +=
                batch.size();

            log.info(
                "숙소 검색 Elasticsearch 재색인 진행 중. indexedCount={}, lastAccommodationId={}",
                indexedCount,
                lastAccommodationId
            );
        }

        /*
         * 전체 Bulk 저장이 끝난 후 한 번만 refresh합니다.
         *
         * 배치마다 refresh하면 Elasticsearch 비용이 증가하므로
         * 전체 재색인이 끝난 뒤 한 번만 수행합니다.
         */
        elasticsearchOperations
            .indexOps(
                AccommodationSearchDocument.class
            )
            .refresh();

        log.info(
            "숙소 검색 Elasticsearch 전체 재색인을 완료했습니다. indexedCount={}",
            indexedCount
        );

        return indexedCount;
    }

    /**
     * 기존 숙소 검색 인덱스를 제거하고
     * 현재 AccommodationSearchDocument 매핑으로 다시 생성합니다.
     */
    private void recreateIndex() {
        IndexOperations indexOperations =
            elasticsearchOperations.indexOps(
                AccommodationSearchDocument.class
            );

        if (indexOperations.exists()) {
            boolean deleted =
                indexOperations.delete();

            if (!deleted) {
                throw new IllegalStateException(
                    "기존 Elasticsearch 숙소 검색 인덱스를 삭제하지 못했습니다."
                );
            }
        }

        boolean created =
            indexOperations.createWithMapping();

        if (!created) {
            throw new IllegalStateException(
                "Elasticsearch 숙소 검색 인덱스를 생성하지 못했습니다."
            );
        }
    }

    /**
     * MySQL 재색인 Projection을
     * Elasticsearch Bulk 저장 요청으로 변환합니다.
     */
    private IndexQuery toIndexQuery(
        AccommodationSearchIndexProjection projection
    ) {
        AccommodationSearchDocument document =
            AccommodationSearchDocument.create(
                projection.getAccommodationId(),
                projection.getName(),
                projection.getAddress(),
                projection.getStatus().name(),
                projection
                    .getLatitude()
                    .doubleValue(),
                projection
                    .getLongitude()
                    .doubleValue()
            );

        return IndexQuery
            .builder()
            .withId(
                projection
                    .getAccommodationId()
                    .toString()
            )
            .withObject(document)
            .build();
    }
}
