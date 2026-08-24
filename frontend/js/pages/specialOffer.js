import { SpecialOfferApi, RoomApi, isLoggedIn, ApiError } from "../api.js";
import {
  mount, escapeHtml, errorBox, spinner, statusTag, formatDateTime, formatMoney, showToast, mediaBlock,
} from "../ui.js";
import { navigate } from "../router.js";

// 라우트를 오갈 때마다 이전 폴링이 남지 않도록 모듈 스코프에서 하나만 유지한다.
let pollTimer = null;

/**
 * 특가 단건을 ID로 바로 조회하는 API는 없다. offerId로 진입하면
 * 목록 API(GET /special-offers)에서 이 ID를 찾아 숙소·객실 정보를 보여주고,
 * 점유 신청/상태 조회는 기존 점유 API로 별도 진행한다. offerId 없이
 * 들어오면 입력창만 보여준다.
 */
export async function renderSpecialOffer(params) {
  clearInterval(pollTimer);

  if (!params?.offerId) {
    renderEntryForm();
    return;
  }

  if (!isLoggedIn()) {
    navigate("/login");
    return;
  }

  const offerId = params.offerId;

  mount(`
    <div class="container page" style="max-width:480px;margin:0 auto;">
      <a href="#/special-offers" class="backlink">← 특가 참여</a>
      <h1>선착순 특가</h1>
      <p class="lead" style="margin-bottom:16px;">특가 번호 #${escapeHtml(offerId)}</p>
      <div id="offer-info"></div>
      <div id="offer-body">${spinner()}</div>
    </div>
  `);

  // 다른 화면으로 이동하면 폴링을 멈춘다. 같은 해시로 재진입할 때는
  // 함수 시작부의 clearInterval이 처리하므로 이 리스너와 중복돼도 안전하다.
  window.addEventListener("hashchange", () => clearInterval(pollTimer), { once: true });

  const isActive = await loadOfferInfo(offerId);
  if (!isActive) {
    document.getElementById("offer-body").innerHTML = `
      <div class="card">
        <p style="margin:0;">이미 종료됐거나 만료된 특가입니다.</p>
      </div>
    `;
    return;
  }

  await refreshStatus(offerId);
}

/**
 * 특가 상세를 조회하는 공개 API는 없지만, 판매 중인 특가 목록 API
 * (GET /special-offers)에서 이 offerId를 찾으면 숙소·객실·가격·날짜를
 * 알 수 있다. 거기서 얻은 roomId로 객실 상세를 다시 조회해 이미지까지
 * 채운다. 특가가 이미 종료됐거나 조회에 실패하면 조용히 생략하고
 * 점유 신청/상태 흐름은 그대로 진행한다.
 */
async function loadOfferInfo(offerId) {
  const infoEl = document.getElementById("offer-info");
  try {
    const offers = await SpecialOfferApi.list();
    const offer = offers.find((o) => String(o.specialOfferId) === String(offerId));
    if (!offer) return false;

    let room = null;
    try {
      room = await RoomApi.detail(offer.roomId);
    } catch (e) {
      // 객실 상세 조회 실패해도 특가 요약 정보는 그대로 보여준다.
    }

    infoEl.innerHTML = `
      <a class="room-row" href="#/rooms/${offer.roomId}" style="margin-bottom:16px;">
        ${mediaBlock(room?.imageUrls?.[0], offer.roomId, offer.roomName, "room-media")}
        <div class="room-body">
          <div style="min-width:0;">
            <div class="stay-name" style="font-size:1.05rem;">${escapeHtml(offer.accommodationName)} · ${escapeHtml(offer.roomName)}</div>
            <div class="muted">숙박 ${escapeHtml(offer.checkInDate)} ~ ${escapeHtml(offer.checkOutDate)}</div>
          </div>
          <div style="text-align:right;">
            <div><b>${formatMoney(offer.price)}</b></div>
            <span class="tiny">객실 보기 →</span>
          </div>
        </div>
      </a>
    `;
    return true;
  } catch (e) {
    // 목록 조회 자체가 실패하면(네트워크 오류 등) 종료 여부를 판단할 수
    // 없으므로, 화면을 막지 않고 기존 점유 신청/상태 흐름을 그대로 진행한다.
    return true;
  }
}

function renderEntryForm() {
  mount(`
    <div class="container page" style="max-width:420px;margin:0 auto;">
      <h1>선착순 특가 참여</h1>
      <p class="lead" style="margin-bottom:24px;">
        참여할 특가 번호를 입력하세요. 특가 번호는 관리자가 등록 후 안내한 번호입니다.
      </p>
      <div class="card">
        <form id="offer-entry-form">
          <label>특가 번호 (offerId)
            <input type="number" name="offerId" min="1" required placeholder="예) 7" />
          </label>
          <button type="submit" class="btn-primary btn-lg">참여하러 가기</button>
        </form>
      </div>
    </div>
  `);

  document.getElementById("offer-entry-form").addEventListener("submit", (e) => {
    e.preventDefault();
    const offerId = new FormData(e.target).get("offerId");
    navigate(`/special-offers/${offerId}`);
  });
}

async function refreshStatus(offerId) {
  const body = document.getElementById("offer-body");
  try {
    const status = await SpecialOfferApi.myStatus(offerId);
    renderStatus(offerId, status);
  } catch (err) {
    if (err instanceof ApiError && err.code === "WAITLIST_NOT_FOUND") {
      renderNoRequest(offerId);
      return;
    }
    body.innerHTML = errorBox(err);
  }
}

function renderNoRequest(offerId) {
  const body = document.getElementById("offer-body");
  body.innerHTML = `
    <div class="card">
      <p style="margin:0 0 16px;">아직 이 특가에 참여 신청을 하지 않았습니다.</p>
      <button id="request-btn" class="btn-primary btn-block btn-lg">특가 참여 신청</button>
      <div id="offer-error" style="margin-top:12px;"></div>
    </div>
  `;

  document.getElementById("request-btn").addEventListener("click", async (e) => {
    const btn = e.currentTarget;
    btn.disabled = true;
    btn.textContent = "신청 중…";
    try {
      await SpecialOfferApi.requestOccupy(offerId);
      showToast("참여 신청이 접수됐습니다.");
      // Kafka로 비동기 처리되므로 접수 직후에는 WAITLIST_NOT_FOUND가 나올 수 있다.
      // refreshStatus가 그 경우 다시 이 화면을 그리고, 폴링은 WAIT를 본 뒤부터 시작한다.
      startPolling(offerId);
      await refreshStatus(offerId);
    } catch (err) {
      document.getElementById("offer-error").innerHTML = errorBox(err);
      btn.disabled = false;
      btn.textContent = "특가 참여 신청";
    }
  });
}

function renderStatus(offerId, status) {
  const body = document.getElementById("offer-body");
  const { status: st, requestedAt, holdExpiresAt } = status;

  body.innerHTML = `
    <div class="card">
      <div style="margin-bottom:14px;">${statusTag(st, "waitlist")}</div>
      ${waitMessage(st, requestedAt, holdExpiresAt)}
    </div>
  `;

  if (st === "WAIT") {
    startPolling(offerId);
  } else {
    clearInterval(pollTimer);
  }
}

function waitMessage(status, requestedAt, holdExpiresAt) {
  switch (status) {
    case "WAIT":
      return `
        <p style="margin:0;">현재 대기 중입니다. 순서가 되면 자동으로 결제 가능 상태로 바뀝니다.</p>
        <p class="tiny" style="margin-top:8px;">신청 시각: ${formatDateTime(requestedAt)} · 4초마다 자동 갱신</p>
      `;
    case "HOLD":
      return `
        <div class="success-box" style="margin-bottom:0;">
          결제 가능 상태로 전환됐습니다! ${formatDateTime(holdExpiresAt)}까지 결제를 완료해야 합니다.
        </div>
        <p class="tiny" style="margin-top:12px;">
          위 객실 정보의 "객실 보기"로 이동해 같은 숙박 날짜로 예약을 진행해 주세요.
          예약·결제 자체는 이 화면이 아닌 일반 예약 화면에서 이루어집니다.
        </p>
      `;
    case "CONFIRMED":
      return `<p style="margin:0;">이 특가로 예약이 확정됐습니다. <a href="#/reservations">내 예약</a>에서 확인하세요.</p>`;
    case "EXPIRED":
      return `<p style="margin:0;">참여 기회가 만료됐습니다.</p>`;
    default:
      return "";
  }
}

function startPolling(offerId) {
  clearInterval(pollTimer);
  pollTimer = setInterval(() => refreshStatus(offerId), 4000);
}
