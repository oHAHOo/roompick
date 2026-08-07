import http from 'k6/http';
import { check } from 'k6';

/**
 * RoomPick 인기 숙소 Cold cache 동시 요청 전용 측정 스크립트입니다.
 *
 * 각 VU가 정확히 한 번 요청하여 하나의 burst를 만들며,
 * 응답 캐시 삭제와 Redis·MySQL 카운터 측정은 실행 셸이 담당합니다.
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PERIOD = (__ENV.PERIOD || 'DAILY').toUpperCase();
const LIMIT = Number(__ENV.LIMIT || 10);
const VUS = Number(__ENV.VUS || 10);

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
    scenarios: {
        cold_cache_burst: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    summaryTrendStats: [
        'avg',
        'min',
        'med',
        'p(95)',
        'p(99)',
        'max',
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

export default function () {
    const response = http.get(
        `${BASE_URL}/api/v1/accommodations/popular`
            + `?period=${PERIOD}&limit=${LIMIT}`
    );

    check(response, {
        'HTTP 상태가 200이다': (result) => result.status === 200,
    });
}
