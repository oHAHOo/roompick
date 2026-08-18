import http from 'k6/http';
import { check } from 'k6';

/**
 * 여러 특가 상품이 동시에 진행 중일 때의 점유 요청 부하를 측정합니다.
 *
 * OFFER_IDS(쉼표 구분)로 넘긴 여러 특가에 VU를 균등 분배해 각 특가마다
 * VUS_PER_OFFER명이 동시에 점유 요청을 보내는 상황을 재현합니다.
 * 파티션 키가 offerId이므로, 파티션 수가 특가 수보다 적으면 여러 특가가
 * 같은 파티션에 몰려 서로의 처리 순서에 영향을 주는지 확인하는 용도입니다.
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OFFER_IDS = (__ENV.OFFER_IDS || '').split(',').map((s) => s.trim()).filter(Boolean);
const VUS_PER_OFFER = Number(__ENV.VUS_PER_OFFER || 20);
const SIGNUP_PASSWORD = 'roompick-load-test-1234';

if (OFFER_IDS.length === 0) {
    throw new Error('OFFER_IDS 환경변수가 필요합니다 (쉼표로 구분된 특가 ID 목록).');
}

if (!Number.isInteger(VUS_PER_OFFER) || VUS_PER_OFFER < 1) {
    throw new Error(`VUS_PER_OFFER는 1 이상의 정수여야 합니다: ${VUS_PER_OFFER}`);
}

const TOTAL_VUS = OFFER_IDS.length * VUS_PER_OFFER;

export const options = {
    setupTimeout: '600s',
    scenarios: {
        multi_offer_occupy_burst: {
            executor: 'per-vu-iterations',
            vus: TOTAL_VUS,
            iterations: 1,
            maxDuration: '60s',
        },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

export function setup() {
    const accessTokens = [];

    for (let i = 0; i < TOTAL_VUS; i++) {
        const email = `multi-offer-load-${Date.now()}-${i}@roompick.com`;

        const signupResponse = http.post(
            `${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({ email, password: SIGNUP_PASSWORD, name: `다중특가 부하테스트 ${i}` }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (signupResponse.status !== 201 && signupResponse.status !== 200) {
            throw new Error(`회원가입 실패 (VU ${i}): status=${signupResponse.status}`);
        }

        const loginResponse = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password: SIGNUP_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (loginResponse.status !== 200) {
            throw new Error(`로그인 실패 (VU ${i}): status=${loginResponse.status}`);
        }

        accessTokens.push(loginResponse.json('data.accessToken'));
    }

    return { accessTokens, offerIds: OFFER_IDS };
}

export default function (data) {
    const vuIndex = __VU - 1;
    const accessToken = data.accessTokens[vuIndex % data.accessTokens.length];
    const offerId = data.offerIds[vuIndex % data.offerIds.length];

    const response = http.post(
        `${BASE_URL}/api/v1/special-offers/${offerId}/occupy-requests`,
        null,
        { headers: { Authorization: `Bearer ${accessToken}` } }
    );

    check(response, {
        'HTTP 상태가 202이다': (result) => result.status === 202,
    });
}
