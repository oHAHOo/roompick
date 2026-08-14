import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 위치 기반 숙소 검색 API의 공통 부하 테스트 스크립트입니다.
 *
 * 동일한 요청 조건으로 MySQL과 Elasticsearch 검색 성능을
 * 비교할 수 있도록 테스트 조건을 환경변수로 전달받습니다.
 *
 * BASE_URL    : 테스트 대상 서버
 * KEYWORD     : 숙소명/주소 검색어, 빈 문자열이면 keyword 없이 검색
 * LATITUDE    : 검색 중심 위도
 * LONGITUDE   : 검색 중심 경도
 * RADIUS_KM   : 검색 반경(km)
 * LIMIT       : 최대 조회 개수
 * VUS         : 동시 사용자 수
 * DURATION    : 테스트 지속 시간
 * THINK_TIME  : 각 요청 후 대기 시간(초)
 * SEARCH_ENGINE:
 *               mysql 또는 elasticsearch
 *               결과 구분용 태그이며 API 요청에는 포함하지 않습니다.
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const KEYWORD = __ENV.KEYWORD || '';
const LATITUDE = Number(__ENV.LATITUDE || 37.5665);
const LONGITUDE = Number(__ENV.LONGITUDE || 126.9780);
const RADIUS_KM = Number(__ENV.RADIUS_KM || 5);
const LIMIT = Number(__ENV.LIMIT || 20);
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '30s';
const THINK_TIME = Number(__ENV.THINK_TIME || 0.1);
const SEARCH_ENGINE =
    (__ENV.SEARCH_ENGINE || 'mysql').toLowerCase();

/**
 * 잘못된 테스트 설정으로 결과가 왜곡되지 않도록
 * 부하 테스트 시작 전에 입력값을 검증합니다.
 */
if (
    !Number.isFinite(LATITUDE)
    || LATITUDE < -90
    || LATITUDE > 90
) {
    throw new Error(
        `LATITUDE는 -90 이상 90 이하의 숫자여야 합니다: ${LATITUDE}`
    );
}

if (
    !Number.isFinite(LONGITUDE)
    || LONGITUDE < -180
    || LONGITUDE > 180
) {
    throw new Error(
        `LONGITUDE는 -180 이상 180 이하의 숫자여야 합니다: ${LONGITUDE}`
    );
}

if (
    !Number.isFinite(RADIUS_KM)
    || RADIUS_KM <= 0
    || RADIUS_KM > 100
) {
    throw new Error(
        `RADIUS_KM은 0 초과 100 이하의 숫자여야 합니다: ${RADIUS_KM}`
    );
}

if (
    !Number.isInteger(LIMIT)
    || LIMIT < 1
    || LIMIT > 100
) {
    throw new Error(
        `LIMIT는 1 이상 100 이하의 정수여야 합니다: ${LIMIT}`
    );
}

if (!Number.isInteger(VUS) || VUS < 1) {
    throw new Error(
        `VUS는 1 이상의 정수여야 합니다: ${VUS}`
    );
}

if (
    SEARCH_ENGINE !== 'mysql'
    && SEARCH_ENGINE !== 'mysql-bounding-box'
    && SEARCH_ENGINE !== 'elasticsearch'
) {
    throw new Error(
        'SEARCH_ENGINE은 mysql, mysql-bounding-box 또는 '
        + `elasticsearch여야 합니다: ${SEARCH_ENGINE}`
    );
}

export const options = {
    vus: VUS,
    duration: DURATION,

    /**
     * 평균뿐 아니라 중앙값과 p95·p99까지 기록하여
     * 검색 엔진별 응답 시간 분포를 비교합니다.
     */
    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(90)',
        'p(95)',
        'p(99)',
        'max',
    ],

    /**
     * HTTP 오류율과 응답 검증 실패율이 1% 미만이어야
     * 테스트를 성공으로 처리합니다.
     */
    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },

    /**
     * MySQL과 Elasticsearch 측정 결과를
     * 같은 스크립트에서 구분하기 위한 태그입니다.
     */
    tags: {
        test_name: 'accommodation-location-search',
        search_engine: SEARCH_ENGINE,
        radius_km: String(RADIUS_KM),
        limit: String(LIMIT),
        keyword_enabled:
            KEYWORD.trim().length > 0 ? 'true' : 'false',
    },
};

export default function () {
    const queryParameters = [
        `latitude=${encodeURIComponent(LATITUDE)}`,
        `longitude=${encodeURIComponent(LONGITUDE)}`,
        `radiusKm=${encodeURIComponent(RADIUS_KM)}`,
        `limit=${encodeURIComponent(LIMIT)}`,
    ];

    /**
     * keyword가 비어 있는 경우에는 요청 파라미터 자체를 제외하여
     * 순수 위치 검색과 keyword 포함 검색을 명확히 구분합니다.
     */
    if (KEYWORD.trim().length > 0) {
        queryParameters.push(
            `keyword=${encodeURIComponent(KEYWORD.trim())}`
        );
    }

    const url =
        `${BASE_URL}/api/v1/accommodations/search`
        + `?${queryParameters.join('&')}`;

    const response = http.get(
        url,
        {
            tags: {
                endpoint: 'accommodation-location-search',
            },
        }
    );

    let responseBody = null;

    try {
        responseBody = response.json();
    } catch (error) {
        responseBody = null;
    }

    const data =
        responseBody !== null
        && Array.isArray(responseBody.data)
            ? responseBody.data
            : null;

    check(response, {
        'HTTP 상태가 200이다':
            (result) => result.status === 200,

        'success 값이 true이다':
            () =>
                responseBody !== null
                && responseBody.success === true,

        'data 값이 배열이다':
            () => data !== null,

        'data 개수가 limit을 초과하지 않는다':
            () =>
                data !== null
                && data.length <= LIMIT,

        '검색 결과가 거리순으로 정렬되어 있다':
            () => {
                if (data === null) {
                    return false;
                }

                for (let index = 1; index < data.length; index += 1) {
                    if (
                        data[index - 1].distanceKm
                        > data[index].distanceKm
                    ) {
                        return false;
                    }
                }

                return true;
            },
    });

    if (THINK_TIME > 0) {
        sleep(THINK_TIME);
    }
}
