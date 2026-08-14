package com.roompick.domain.accommodation.service;

/**
 * 위치 기반 숙소 검색에서 사용하는 Bounding Box입니다.
 *
 * 정확한 거리 계산인 ST_Distance_Sphere()를 실행하기 전에
 * 위도/경도 범위로 검색 후보를 먼저 줄이기 위해 사용합니다.
 *
 * Bounding Box는 원형 검색 반경을 감싸는 사각형이므로
 * 최종 검색 결과는 반드시 정확한 거리 계산으로 다시 검증해야 합니다.
 */
public record AccommodationLocationBoundingBox(
    double minLatitude,
    double maxLatitude,
    double minLongitude,
    double maxLongitude
) {

    /**
     * 지구 평균 반지름입니다.
     *
     * Bounding Box 계산에만 사용하며,
     * 최종 거리 판정은 MySQL ST_Distance_Sphere()가 담당합니다.
     */
    private static final double EARTH_RADIUS_KM = 6370.986;

    private static final double MIN_LATITUDE_RADIAN = -Math.PI / 2.0;
    private static final double MAX_LATITUDE_RADIAN = Math.PI / 2.0;

    private static final double MIN_LONGITUDE_RADIAN = -Math.PI;
    private static final double MAX_LONGITUDE_RADIAN = Math.PI;

    /**
     * 검색 중심 좌표와 반경을 기준으로 Bounding Box를 계산합니다.
     *
     * 일반적인 위치에서는 위도/경도의 최소·최대 범위를 계산하고,
     * 검색 반경이 극점을 포함하면 모든 경도를 검색하도록 처리합니다.
     *
     * 날짜 변경선(경도 ±180도)을 넘어가는 경우에는
     * minLongitude가 maxLongitude보다 크게 반환될 수 있습니다.
     * Repository에서는 해당 경우를 별도 OR 조건으로 처리합니다.
     */
    public static AccommodationLocationBoundingBox calculate(
        double latitude,
        double longitude,
        double radiusKm
    ) {
        double latitudeRadian = Math.toRadians(latitude);
        double longitudeRadian = Math.toRadians(longitude);
        double angularDistance = radiusKm / EARTH_RADIUS_KM;

        double minLatitudeRadian =
            latitudeRadian - angularDistance;

        double maxLatitudeRadian =
            latitudeRadian + angularDistance;

        double minLongitudeRadian;
        double maxLongitudeRadian;

        /*
         * 검색 범위가 북극 또는 남극을 포함하지 않는 경우에는
         * 현재 위도에서 반경에 필요한 경도 범위를 계산합니다.
         */
        if (
            minLatitudeRadian > MIN_LATITUDE_RADIAN
                && maxLatitudeRadian < MAX_LATITUDE_RADIAN
        ) {
            double longitudeDelta = Math.asin(
                Math.sin(angularDistance)
                    / Math.cos(latitudeRadian)
            );

            minLongitudeRadian = normalizeLongitude(
                longitudeRadian - longitudeDelta
            );

            maxLongitudeRadian = normalizeLongitude(
                longitudeRadian + longitudeDelta
            );
        } else {
            /*
             * 검색 반경이 극점을 포함하면 특정 경도 범위로
             * 후보를 제한할 수 없으므로 전체 경도를 허용합니다.
             */
            minLatitudeRadian = Math.max(
                minLatitudeRadian,
                MIN_LATITUDE_RADIAN
            );

            maxLatitudeRadian = Math.min(
                maxLatitudeRadian,
                MAX_LATITUDE_RADIAN
            );

            minLongitudeRadian = MIN_LONGITUDE_RADIAN;
            maxLongitudeRadian = MAX_LONGITUDE_RADIAN;
        }

        return new AccommodationLocationBoundingBox(
            Math.toDegrees(minLatitudeRadian),
            Math.toDegrees(maxLatitudeRadian),
            Math.toDegrees(minLongitudeRadian),
            Math.toDegrees(maxLongitudeRadian)
        );
    }

    /**
     * 경도를 -180도 ~ 180도 범위로 정규화합니다.
     */
    private static double normalizeLongitude(
        double longitudeRadian
    ) {
        while (longitudeRadian < MIN_LONGITUDE_RADIAN) {
            longitudeRadian += Math.PI * 2.0;
        }

        while (longitudeRadian > MAX_LONGITUDE_RADIAN) {
            longitudeRadian -= Math.PI * 2.0;
        }

        return longitudeRadian;
    }
}
