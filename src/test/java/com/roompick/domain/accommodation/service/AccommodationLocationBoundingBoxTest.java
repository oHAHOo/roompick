package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 위치 검색 Bounding Box 계산을 검증하는 테스트입니다.
 *
 * 일반적인 좌표뿐 아니라
 * 날짜변경선과 극지방 경계값도 함께 검증합니다.
 */
class AccommodationLocationBoundingBoxTest {

    private static final double TOLERANCE = 0.001;

    @Test
    void 서울시청_기준_5km_Bounding_Box를_계산한다() {
        // given
        double latitude = 37.5665;
        double longitude = 126.9780;
        double radiusKm = 5.0;

        // when
        AccommodationLocationBoundingBox boundingBox =
            AccommodationLocationBoundingBox.calculate(
                latitude,
                longitude,
                radiusKm
            );

        // then
        assertThat(boundingBox.minLatitude())
            .isLessThan(latitude);

        assertThat(boundingBox.maxLatitude())
            .isGreaterThan(latitude);

        assertThat(boundingBox.minLongitude())
            .isLessThan(longitude);

        assertThat(boundingBox.maxLongitude())
            .isGreaterThan(longitude);

        /*
         * 서울 위도에서 반경 5km는 대략
         * 위도 ±0.045도,
         * 경도 ±0.057도 범위가 됩니다.
         */
        assertThat(boundingBox.minLatitude())
            .isCloseTo(37.5215, within(TOLERANCE));

        assertThat(boundingBox.maxLatitude())
            .isCloseTo(37.6115, within(TOLERANCE));

        assertThat(boundingBox.minLongitude())
            .isCloseTo(126.9213, within(TOLERANCE));

        assertThat(boundingBox.maxLongitude())
            .isCloseTo(127.0347, within(TOLERANCE));
    }

    @Test
    void 날짜변경선을_넘으면_minLongitude가_maxLongitude보다_커진다() {
        // given
        double latitude = 0.0;
        double longitude = 179.9;
        double radiusKm = 50.0;

        // when
        AccommodationLocationBoundingBox boundingBox =
            AccommodationLocationBoundingBox.calculate(
                latitude,
                longitude,
                radiusKm
            );

        // then
        /*
         * +180도를 넘어간 경도는 -180도 쪽으로 정규화됩니다.
         *
         * 예:
         * 179.45 ~ -179.65
         *
         * 이 경우 Repository에서는 BETWEEN 하나가 아니라
         * 두 경도 구간을 OR 조건으로 조회합니다.
         */
        assertThat(boundingBox.minLongitude())
            .isGreaterThan(boundingBox.maxLongitude());

        assertThat(boundingBox.minLongitude())
            .isBetween(179.0, 180.0);

        assertThat(boundingBox.maxLongitude())
            .isBetween(-180.0, -179.0);
    }

    @Test
    void 검색_반경이_북극을_포함하면_전체_경도를_허용한다() {
        // given
        double latitude = 89.9;
        double longitude = 30.0;
        double radiusKm = 50.0;

        // when
        AccommodationLocationBoundingBox boundingBox =
            AccommodationLocationBoundingBox.calculate(
                latitude,
                longitude,
                radiusKm
            );

        // then
        assertThat(boundingBox.maxLatitude())
            .isCloseTo(90.0, within(TOLERANCE));

        assertThat(boundingBox.minLatitude())
            .isLessThan(latitude);

        /*
         * 극점을 포함하는 검색에서는
         * 특정 경도 범위로 후보를 안전하게 제한할 수 없으므로
         * 전체 경도를 사용합니다.
         */
        assertThat(boundingBox.minLongitude())
            .isCloseTo(-180.0, within(TOLERANCE));

        assertThat(boundingBox.maxLongitude())
            .isCloseTo(180.0, within(TOLERANCE));
    }

    /**
     * AssertJ offset 생성 코드를 테스트 본문에서 짧게 사용하기 위한 메서드입니다.
     */
    private static org.assertj.core.data.Offset<Double> within(
        double value
    ) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
