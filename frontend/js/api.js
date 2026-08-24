const BASE_URL_KEY = "rp_api_base_url";
const ACCESS_TOKEN_KEY = "rp_access_token";
const REFRESH_TOKEN_KEY = "rp_refresh_token";
const DEFAULT_BASE_URL = "http://localhost:8080";

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function getBaseUrl() {
  return localStorage.getItem(BASE_URL_KEY) || DEFAULT_BASE_URL;
}

export function setBaseUrl(url) {
  localStorage.setItem(BASE_URL_KEY, url.replace(/\/+$/, ""));
}

/**
 * 정적 사이트는 어느 백엔드를 호출할지 스스로 알 수 없어 주소를 어딘가에 설정해야 한다.
 * 방문자 전원에게 보이는 UI 대신, 운영자가 배포 도메인에 ?apiBase=...로 한 번만 접속해
 * localStorage에 저장해두는 방식을 쓴다. 이후 방문에는 파라미터 없이도 유지된다.
 * 예) https://your-site.example/?apiBase=http://ec2-ip:8080
 */
(function initBaseUrlFromQuery() {
  const value = new URLSearchParams(location.search).get("apiBase");
  if (value) setBaseUrl(value);
})();

export function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setTokens({ accessToken, refreshToken }) {
  if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function isLoggedIn() {
  return Boolean(getAccessToken());
}

export function newIdempotencyKey() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return `rp-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function decodeToken(token) {
  try {
    const payload = token.split(".")[1];
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join("")
    );
    return JSON.parse(json);
  } catch (e) {
    return null;
  }
}

export function getCurrentRole() {
  const token = getAccessToken();
  if (!token) return null;
  const claims = decodeToken(token);
  return claims?.role ?? null;
}

let refreshInFlight = null;

async function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new ApiError(401, "NO_REFRESH_TOKEN", "다시 로그인해 주세요.");

  if (!refreshInFlight) {
    refreshInFlight = fetch(`${getBaseUrl()}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (res) => {
        const body = await res.json().catch(() => null);
        if (!res.ok || !body?.success) {
          clearTokens();
          throw new ApiError(401, body?.code ?? "REFRESH_FAILED", body?.message ?? "세션이 만료되었습니다.");
        }
        setTokens(body.data);
        return body.data.accessToken;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * @param {string} path e.g. "/api/v1/accommodations"
 * @param {object} options
 * @param {"GET"|"POST"|"PATCH"|"PUT"|"DELETE"} [options.method]
 * @param {object} [options.body] JSON body
 * @param {FormData} [options.form] multipart form body (mutually exclusive with body)
 * @param {boolean} [options.auth] attach Authorization header
 * @param {Record<string,string>} [options.headers] extra request headers
 */
export async function request(path, options = {}) {
  const { method = "GET", body, form, auth = false, headers: extraHeaders, _retry = false } = options;

  const headers = { ...extraHeaders };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = getAccessToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(`${getBaseUrl()}${path}`, {
      method,
      headers,
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
    });
  } catch (networkError) {
    throw new ApiError(0, "NETWORK_ERROR", "서버에 연결할 수 없습니다. API 서버 주소를 확인해 주세요.");
  }

  let payload = null;
  try {
    payload = await res.json();
  } catch (e) {
    // no body
  }

  if (res.status === 401 && auth && !_retry) {
    try {
      await refreshAccessToken();
      return request(path, { ...options, _retry: true });
    } catch (refreshError) {
      clearTokens();
      if (location.hash !== "#/login") {
        location.hash = "#/login";
      }
      throw refreshError;
    }
  }

  if (!res.ok || payload?.success === false) {
    throw new ApiError(res.status, payload?.code ?? String(res.status), payload?.message ?? "요청을 처리하지 못했습니다.");
  }

  return payload?.data;
}

// ---- Auth ----
export const AuthApi = {
  signup: (data) => request("/api/v1/auth/signup", { method: "POST", body: data }),
  login: (data) => request("/api/v1/auth/login", { method: "POST", body: data }),
  /**
   * 서버가 accessToken(헤더)과 refreshToken(body)을 모두 블랙리스트에 등록하므로
   * refreshToken을 함께 보내야 한다. 빠뜨리면 400이 나고 토큰이 무효화되지 않는다.
   */
  logout: () =>
    request("/api/v1/auth/logout", {
      method: "POST",
      body: { refreshToken: getRefreshToken() },
      auth: true,
    }),
};

// ---- Member ----
export const MemberApi = {
  /**
   * 액세스 토큰에는 회원 ID·역할만 있고 이메일이 없어서, 결제창 호출처럼
   * 구매자 이메일이 필요한 화면에서 이 API로 조회한다.
   */
  me: () => request("/api/v1/members/me", { auth: true }),
};

// ---- Accommodations ----
export const AccommodationApi = {
  list: (page = 0, size = 20) => request(`/api/v1/accommodations?page=${page}&size=${size}`),
  popular: (period = "DAILY", limit = 10) =>
    request(`/api/v1/accommodations/popular?period=${period}&limit=${limit}`),
  nearby: ({ keyword, latitude, longitude, radiusKm = 5, limit = 20 }) => {
    const params = new URLSearchParams({ latitude, longitude, radiusKm, limit });
    if (keyword) params.set("keyword", keyword);
    return request(`/api/v1/accommodations/search?${params.toString()}`);
  },
  /**
   * auth: true로 Authorization 헤더를 함께 보낸다. 이 엔드포인트는 인증 없이도
   * 호출할 수 있지만, ADMIN 토큰이 실려 있으면 서버가 INACTIVE 숙소도 반환한다.
   */
  detail: (id) => request(`/api/v1/accommodations/${id}`, { auth: true }),
  rooms: (id) => request(`/api/v1/accommodations/${id}/rooms`, { auth: true }),
};

// ---- Places ----
export const PlaceApi = {
  search: (query, limit = 5) =>
    request(`/api/v1/places/search?query=${encodeURIComponent(query)}&limit=${limit}`),
};

// ---- Rooms ----
export const RoomApi = {
  /**
   * 인증 없이도 호출할 수 있지만, ADMIN 토큰이 실려 있으면
   * 서버가 INACTIVE 객실도 반환한다.
   */
  detail: (roomId) => request(`/api/v1/rooms/${roomId}`, { auth: true }),
  availability: (roomId, { checkInDate, checkOutDate, guestCount }) =>
    request(
      `/api/v1/rooms/${roomId}/availability?checkInDate=${checkInDate}&checkOutDate=${checkOutDate}&guestCount=${guestCount}`
    ),
};

// ---- Reservations ----
export const ReservationApi = {
  /**
   * 예약 생성은 Idempotency-Key 헤더가 필수다(docs/RESERVATION_IDEMPOTENCY.md).
   * 같은 키로 같은 요청을 재전달하면 새 예약 대신 기존 예약이 반환되므로,
   * 호출 측에서 "한 번의 예약 시도"마다 키를 하나 만들어 전달한다.
   */
  create: (data, idempotencyKey) =>
    request("/api/v1/reservations", {
      method: "POST",
      body: data,
      auth: true,
      headers: { "Idempotency-Key": idempotencyKey },
    }),
  myList: (page = 0, size = 10) => request(`/api/v1/reservations?page=${page}&size=${size}`, { auth: true }),
  detail: (id) => request(`/api/v1/reservations/${id}`, { auth: true }),
  cancel: (id) => request(`/api/v1/reservations/${id}/cancel`, { method: "PATCH", auth: true }),
};

// ---- Payments ----
export const PaymentApi = {
  prepare: (reservationId) =>
    request(`/api/v1/reservations/${reservationId}/payments`, { method: "POST", auth: true }),
  approve: (paymentId, amount) =>
    request(`/api/v1/payments/${paymentId}/approve`, { method: "POST", body: { amount }, auth: true }),
  fail: (paymentId) => request(`/api/v1/payments/${paymentId}/fail`, { method: "POST", auth: true }),
  /**
   * PortOne 결제창에서 결제가 끝난 뒤 호출한다. body 없이 paymentId만 보내면
   * 서버가 저장해둔 portOnePaymentId로 PortOne 서버 API를 직접 조회·검증한다.
   */
  complete: (paymentId) => request(`/api/v1/payments/${paymentId}/complete`, { method: "POST", auth: true }),
};

// ---- Special offer occupy (선착순 특가 대기열) ----
export const SpecialOfferApi = {
  /** 현재 판매 중인 특가 목록. 인증 없이도 호출할 수 있다. */
  list: () => request("/api/v1/special-offers"),
  /**
   * 특가 점유 요청. 202로 접수만 되고 실제 처리는 Kafka로 비동기 처리되므로,
   * 직후 상태 조회가 아직 WAITLIST_NOT_FOUND일 수 있다 — 폴링으로 따라잡는다.
   */
  requestOccupy: (offerId) =>
    request(`/api/v1/special-offers/${offerId}/occupy-requests`, { method: "POST", auth: true }),
  myStatus: (offerId) =>
    request(`/api/v1/special-offers/${offerId}/occupy-requests/me`, { auth: true }),
};

// ---- Admin ----
export const AdminApi = {
  createAccommodation: (form) =>
    request("/api/v1/admin/accommodations", { method: "POST", form, auth: true }),
  createRoom: (accommodationId, form) =>
    request(`/api/v1/admin/accommodations/${accommodationId}/rooms`, { method: "POST", form, auth: true }),
  updateRoomStatus: (accommodationId, roomId, status) =>
    request(`/api/v1/admin/accommodations/${accommodationId}/rooms/${roomId}/status`, {
      method: "PATCH",
      body: { status },
      auth: true,
    }),

  /**
   * 논리 삭제. DB 행은 남기고 숙소와 소속 객실 전체를 INACTIVE로 바꾼다(docs/API_SPEC_ADMIN.md 7절).
   * 예약·결제·이미지 데이터는 지우지 않으며, 이미 INACTIVE여도 200으로 멱등하게 처리된다.
   * 되돌리려면 updateAccommodationStatus(id, "ACTIVE")를 호출한다. 소속 객실은 함께
   * 살아나지 않으므로 필요한 객실은 각각 다시 공개해야 한다.
   */
  deleteAccommodation: (accommodationId) =>
    request(`/api/v1/admin/accommodations/${accommodationId}`, { method: "DELETE", auth: true }),

  /**
   * 비공개된 숙소를 다시 운영 중 상태로 되돌리거나(ACTIVE), 비공개(INACTIVE)로 전환한다.
   */
  updateAccommodationStatus: (accommodationId, status) =>
    request(`/api/v1/admin/accommodations/${accommodationId}/status`, {
      method: "PATCH",
      body: { status },
      auth: true,
    }),

  /**
   * 논리 삭제. 객실 하나만 INACTIVE로 바꾼다(docs/API_SPEC_ADMIN.md 8절).
   * updateRoomStatus(..., "INACTIVE")와 최종 결과는 같지만, 현재 상태를 몰라도
   * 호출할 수 있고 이미 삭제된 객실에 다시 호출해도 200으로 멱등하다.
   */
  deleteRoom: (accommodationId, roomId) =>
    request(`/api/v1/admin/accommodations/${accommodationId}/rooms/${roomId}`, {
      method: "DELETE",
      auth: true,
    }),

  /**
   * 특정 객실에 특가를 등록한다.
   * 시각 필드 이름이 startsAt/endsAt인 점에 주의 (타임세일은 startAt/endAt).
   */
  createSpecialOffer: (accommodationId, roomId, data) =>
    request(
      `/api/v1/admin/accommodations/${accommodationId}/rooms/${roomId}/special-offers`,
      { method: "POST", body: data, auth: true }
    ),

  /**
   * 타임세일을 등록한다. roomId가 null이면 숙소 전체에 적용된다.
   */
  createTimeSale: (accommodationId, data) =>
    request(`/api/v1/admin/accommodations/${accommodationId}/time-sales`, {
      method: "POST",
      body: data,
      auth: true,
    }),
};
