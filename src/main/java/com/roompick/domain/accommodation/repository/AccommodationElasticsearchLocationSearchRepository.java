package com.roompick.domain.accommodation.repository;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.SortOrder;
import lombok.RequiredArgsConstructor;

/**
 * Elasticsearch를 이용한 위치 기반 숙소 검색 Repository입니다.
 *
 * ACTIVE 숙소만 대상으로 검색하며,
 * 검색 중심 좌표로부터 지정된 반경 안의 숙소를 조회합니다.
 *
 * keyword가 존재하면 숙소명과 주소를 함께 검색하고,
 * 최종 결과는 검색 중심 위치와 가까운 순서로 반환합니다.
 */
@Repository
@RequiredArgsConstructor
public class AccommodationElasticsearchLocationSearchRepository {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_LOCATION = "location";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_ADDRESS = "address";

    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 검색 중심 좌표를 기준으로 주변 숙소를 조회합니다.
     *
     * MySQL 기준 검색과 동일하게 다음 조건을 적용합니다.
     *
     * 1. ACTIVE 숙소만 조회합니다.
     * 2. 지정된 반경 안의 숙소만 조회합니다.
     * 3. keyword가 존재하면 숙소명 또는 주소를 검색합니다.
     * 4. 검색 중심 좌표와 가까운 순으로 정렬합니다.
     * 5. 요청한 limit만큼만 반환합니다.
     */
    public List<AccommodationElasticsearchLocationSearchResult>
    searchNearby(
        String keyword,
        double latitude,
        double longitude,
        double radiusKm,
        int limit
    ) {
        NativeQuery query =
            NativeQuery.builder()
                .withQuery(
                    queryBuilder ->
                        queryBuilder.bool(
                            boolQuery -> {
                                /*
                                 * 운영 중인 숙소만 검색합니다.
                                 *
                                 * status는 Keyword 필드이므로
                                 * 정확한 값인 ACTIVE를 term query로 조회합니다.
                                 */
                                boolQuery.filter(
                                    filter ->
                                        filter.term(
                                            term ->
                                                term
                                                    .field(FIELD_STATUS)
                                                    .value(ACTIVE_STATUS)
                                        )
                                );

                                /*
                                 * 검색 중심 좌표에서 radiusKm 안에 존재하는
                                 * 숙소만 검색합니다.
                                 */
                                boolQuery.filter(
                                    filter ->
                                        filter.geoDistance(
                                            geoDistance ->
                                                geoDistance
                                                    .field(FIELD_LOCATION)
                                                    .distance(
                                                        radiusKm + "km"
                                                    )
                                                    .location(
                                                        location ->
                                                            location.latlon(
                                                                latLon ->
                                                                    latLon
                                                                        .lat(latitude)
                                                                        .lon(longitude)
                                                            )
                                                    )
                                        )
                                );

                                /*
                                 * keyword가 존재하는 경우에만
                                 * 숙소명과 주소를 대상으로 검색 조건을 추가합니다.
                                 */
                                if (
                                    keyword != null
                                        && !keyword.isBlank()
                                ) {
                                    boolQuery.must(
                                        must ->
                                            must.multiMatch(
                                                multiMatch ->
                                                    multiMatch
                                                        .query(keyword)
                                                        .fields(
                                                            FIELD_NAME,
                                                            FIELD_ADDRESS
                                                        )
                                            )
                                    );
                                }

                                return boolQuery;
                            }
                        )
                )

                /*
                 * 검색 결과 개수만 필요하므로
                 * 전체 검색 결과 건수 계산은 수행하지 않습니다.
                 */
                .withTrackTotalHits(false)

                /*
                 * 요청한 limit만큼만 Elasticsearch에서 조회합니다.
                 *
                 * 모든 Document를 애플리케이션으로 가져온 뒤
                 * 잘라내는 방식은 사용하지 않습니다.
                 */
                .withPageable(
                    PageRequest.of(
                        0,
                        limit
                    )
                )

                /*
                 * 검색 중심 좌표와 가까운 숙소부터 정렬합니다.
                 *
                 * 정렬값의 단위를 Kilometers로 지정하여
                 * SearchHit의 첫 번째 sort value를 그대로
                 * distanceKm으로 사용할 수 있게 합니다.
                 */
                .withSort(
                    sort ->
                        sort.geoDistance(
                            geoDistance ->
                                geoDistance
                                    .field(FIELD_LOCATION)
                                    .location(
                                        location ->
                                            location.latlon(
                                                latLon ->
                                                    latLon
                                                        .lat(latitude)
                                                        .lon(longitude)
                                            )
                                    )
                                    .unit(DistanceUnit.Kilometers)
                                    .order(SortOrder.Asc)
                        )
                )
                .build();

        SearchHits<AccommodationSearchDocument> searchHits =
            elasticsearchOperations.search(
                query,
                AccommodationSearchDocument.class
            );

        return searchHits
            .getSearchHits()
            .stream()
            .map(this::toSearchResult)
            .toList();
    }

    /**
     * Elasticsearch SearchHit을 애플리케이션 검색 결과로 변환합니다.
     */
    private AccommodationElasticsearchLocationSearchResult
    toSearchResult(
        SearchHit<AccommodationSearchDocument> searchHit
    ) {
        double distanceKm =
            extractDistanceKm(
                searchHit
            );

        return new AccommodationElasticsearchLocationSearchResult(
            searchHit.getContent(),
            distanceKm
        );
    }

    /**
     * geo distance 정렬이 반환한 거리값을 추출합니다.
     *
     * 첫 번째 정렬 조건이 geo distance이고 단위를 km로 지정했으므로
     * 첫 번째 sort value가 숙소와 검색 중심 사이의 거리입니다.
     */
    private double extractDistanceKm(
        SearchHit<AccommodationSearchDocument> searchHit
    ) {
        List<Object> sortValues =
            searchHit.getSortValues();

        if (sortValues.isEmpty()) {
            throw new IllegalStateException(
                "Elasticsearch 위치 검색 결과에 거리 정렬값이 없습니다."
            );
        }

        Object distanceValue =
            sortValues.get(0);

        if (!(distanceValue instanceof Number number)) {
            throw new IllegalStateException(
                "Elasticsearch 위치 검색 거리값의 형식이 올바르지 않습니다."
            );
        }

        return number.doubleValue();
    }
}
