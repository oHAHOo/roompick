import http from 'k6/http';
import { check } from 'k6';

/**
 * RoomPick 한정 수량 특가 점유 요청 동시 접수 측정 스크립트입니다.
 *
 * setup()에서 VU 수만큼 회원을 새로 가입시켜 각자의 액세스 토큰을 발급받고,
 * 각 VU가 같은 특가 상품(offerId)에 정확히 한 번씩 점유 요청을 보내
 * 하나의 burst를 만듭니다.
 *
 * 이 스크립트는 접수(Producer) 단계의 처리량·오류율만 측정합니다.
 * 실제 파티션 처리 순서와 최종 HOLD/WAIT 배정 결과는 접수 응답만으로는
 * 알 수 없으므로, occupy-requests/me 조회 API로 별도 검증해야 합니다.
 */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const OFFER_ID = __ENV.OFFER_ID;
const VUS = Number(__ENV.VUS || 30);
const SIGNUP_PASSWORD = 'roompick-load-test-1234';

if (!OFFER_ID) {
    throw new Error(
        'OFFER_ID 환경변수가 필요합니다. '
            + '관리자 특가 등록 API로 ACTIVE 상태의 특가를 먼저 만들고 그 ID를 전달하세요.'
    );
}

if (!Number.isInteger(VUS) || VUS < 1) {
    throw new Error(`VUS는 1 이상의 정수여야 합니다: ${VUS}`);
}

export const options = {
    // setup()에서 VU 수만큼 회원가입+로그인을 순차로 처리하므로
    // VU 수가 크면 k6 기본 60초 제한을 넘길 수 있다.
    setupTimeout: '600s',
    scenarios: {
        occupy_request_burst: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '60s',
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

/**
 * VU마다 서로 다른 회원으로 요청을 보내야 파티션 안에서
 * 여러 건의 점유 요청이 실제로 경쟁하는 상황을 재현할 수 있습니다.
 * 로그인/회원가입은 부하 측정 대상이 아니므로 setup 단계에서 미리 끝냅니다.
 */
export function setup() {
    const accessTokens = [];

    for (let i = 0; i < VUS; i++) {
        const email = `special-offer-load-${Date.now()}-${i}@roompick.com`;

        const signupResponse = http.post(
            `${BASE_URL}/api/v1/auth/signup`,
            JSON.stringify({
                email,
                password: SIGNUP_PASSWORD,
                name: `특가 부하테스트 회원 ${i}`,
            }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (signupResponse.status !== 201 && signupResponse.status !== 200) {
            throw new Error(
                `회원가입 실패 (VU ${i}): status=${signupResponse.status}`
            );
        }

        const loginResponse = http.post(
            `${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ email, password: SIGNUP_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' } }
        );

        if (loginResponse.status !== 200) {
            throw new Error(
                `로그인 실패 (VU ${i}): status=${loginResponse.status}`
            );
        }

        accessTokens.push(loginResponse.json('data.accessToken'));
    }

    return { accessTokens };
}

export default function (data) {
    const accessToken = data.accessTokens[(__VU - 1) % data.accessTokens.length];

    const response = http.post(
        `${BASE_URL}/api/v1/special-offers/${OFFER_ID}/occupy-requests`,
        null,
        {
            headers: {
                Authorization: `Bearer ${accessToken}`,
            },
        }
    );

    check(response, {
        'HTTP 상태가 202이다': (result) => result.status === 202,
    });
}
