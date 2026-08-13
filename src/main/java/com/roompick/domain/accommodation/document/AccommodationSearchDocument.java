package com.roompick.domain.accommodation.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Elasticsearch 숙소 검색 전용 Document입니다.
 *
 * MySQL Accommodation Entity를 검색 인덱스에 그대로 의존시키지 않고,
 * 위치 기반 숙소 검색에 필요한 필드만 별도의 Read Model로 관리합니다.
 *
 * location은 Elasticsearch의 geo_point로 저장하며,
 * 반경 검색과 거리순 정렬에 사용합니다.
 * 인덱스 생성과 매핑은 전체 재색인 Service에서 명시적으로 수행합니다.
 */
@Getter
@Document(
    indexName = "roompick-accommodations-v1",
    createIndex = false
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccommodationSearchDocument {

    /**
     * MySQL accommodations 테이블의 숙소 ID입니다.
     *
     * MySQL과 Elasticsearch 문서를 연결하는 식별자로 사용합니다.
     */
    @Id
    private Long accommodationId;

    /**
     * 숙소명 검색에 사용하는 필드입니다.
     */
    @Field(type = FieldType.Text)
    private String name;

    /**
     * 숙소 주소 검색에 사용하는 필드입니다.
     */
    @Field(type = FieldType.Text)
    private String address;

    /**
     * 숙소 운영 상태입니다.
     *
     * 위치 검색에서는 ACTIVE 문서만 조회합니다.
     */
    @Field(type = FieldType.Keyword)
    private String status;

    /**
     * 숙소 위도와 경도를 하나의 geo_point로 저장합니다.
     *
     * Elasticsearch의 geo_distance 검색과
     * 거리순 정렬에서 사용하는 핵심 필드입니다.
     */
    @GeoPointField
    private GeoPoint location;

    private AccommodationSearchDocument(
        Long accommodationId,
        String name,
        String address,
        String status,
        GeoPoint location
    ) {
        this.accommodationId = accommodationId;
        this.name = name;
        this.address = address;
        this.status = status;
        this.location = location;
    }

    /**
     * Elasticsearch 검색 Document를 생성합니다.
     */
    public static AccommodationSearchDocument create(
        Long accommodationId,
        String name,
        String address,
        String status,
        double latitude,
        double longitude
    ) {
        return new AccommodationSearchDocument(
            accommodationId,
            name,
            address,
            status,
            new GeoPoint(
                latitude,
                longitude
            )
        );
    }
}
