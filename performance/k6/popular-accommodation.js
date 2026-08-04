import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 인기 숙소 조회 API의 공통 부하 테스트 스크립트입니다.
 *
 * 실행 시 환경변수로 테스트 조건을 변경할 수 있습니다.
 *
 * BASE_URL   : 테스트 대상 서버
 * PERIOD     : DAILY 또는 WEEKLY
 * LIMIT      : 조회 개수 1~20
 * VUS        : 동시 사용자 수
 * DURATION   : 테스트 지속 시간
 * THINK_TIME : 각 요청 후 대기 시간(초)
 * CACHE_STATE: 결과 구분용 태그
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PERIOD = (__ENV.PERIOD || 'DAILY').toUpperCase();
const LIMIT = Number(__ENV.LIMIT || 10);
const VUS = Number(__ENV.VUS || 10);
const DURATION = __ENV.DURATION || '30s';
const THINK_TIME = Number(__ENV.THINK_TIME || 0.1);
const CACHE_STATE = __ENV.CACHE_STATE || 'unspecified';

/**
 * 잘못된 테스트 설정으로 성능 결과가 왜곡되지 않도록
 * 테스트 시작 전에 입력값을 검증합니다.
 */
if (PERIOD !== 'DAILY' && PERIOD !== 'WEEKLY') {
    throw new Error(`PERIOD는 DAILY 또는 WEEKLY여야 합니다: ${PERIOD}`);
}

if (!Number.isInteger(LIMIT) || LIMIT < 1 || LIMIT > 20) {
    throw new Error(`LIMIT는 1 이상 20 이하의 정수여야 합니다: ${LIMIT}`);
}

if (!Number.isInteger(VUS) || VUS < 1) {
    throw new Error(`VUS는 1 이상의 정수여야 합니다: ${VUS}`);
}

export const options = {
    vus: VUS,
    duration: DURATION,

    /**
     * 평균뿐 아니라 중앙값과 p95·p99를 결과에 표시합니다.
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
     * 실행 결과를 시나리오별로 구분하기 위한 공통 태그입니다.
     */
    tags: {
        test_name: 'popular-accommodation-cache',
        period: PERIOD,
        limit: String(LIMIT),
        cache_state: CACHE_STATE,
    },
};

export default function () {
    const url =
        `${BASE_URL}/api/v1/accommodations/popular` +
        `?period=${PERIOD}&limit=${LIMIT}`;

    const response = http.get(url, {
        tags: {
            endpoint: 'popular-accommodations',
        },
    });

    let responseBody = null;

    try {
        responseBody = response.json();
    } catch (error) {
        responseBody = null;
    }

    const data =
        responseBody !== null && Array.isArray(responseBody.data)
            ? responseBody.data
            : null;

    check(response, {
        'HTTP 상태가 200이다': (result) => result.status === 200,
        'success 값이 true이다': () =>
            responseBody !== null && responseBody.success === true,
        'data 값이 배열이다': () => data !== null,
        'data 개수가 limit을 초과하지 않는다': () =>
            data !== null && data.length <= LIMIT,
    });

    if (THINK_TIME > 0) {
        sleep(THINK_TIME);
    }
}
