import { RoomApi, AdminApi, isLoggedIn, getCurrentRole } from "../api.js";
import {
  mount, escapeHtml, errorBox, spinner, showToast, statusTag, formatMoney,
  withSeconds, addDaysIso, localDateTimeInput,
} from "../ui.js";

/**
 * 관리자 전용 특가 등록 화면. accommodationId·roomId는 URL(params.accId,
 * params.id)에서 그대로 받으므로 입력창이 없다 — 객실 상세의 "특가 등록"
 * 버튼을 눌러야만 이 화면에 온다.
 *
 * 특가는 객실 단위로만 등록할 수 있고, 등록 직후 상태는 SCHEDULED다.
 * 스케줄러가 startsAt/endsAt에 맞춰 ACTIVE·ENDED로 전환한다.
 * 시각 필드 이름이 startsAt/endsAt인 점이 타임세일(startAt/endAt)과 다르다.
 */
export async function renderSpecialOfferCreate(params) {
  const accommodationId = params.accId;
  const roomId = params.id;

  if (!isLoggedIn()) {
    location.hash = "#/login";
    return;
  }
  if (getCurrentRole() !== "ADMIN") {
    mount(`
      <div class="container page">
        <h1>관리자 전용</h1>
        <div class="error-box">ADMIN 권한이 있는 계정으로 로그인해 주세요.</div>
      </div>
    `);
    return;
  }

  mount(`<div class="container page" id="offer-create-body">${spinner()}</div>`);
  const body = document.getElementById("offer-create-body");

  // 이름을 보여주기 위한 조회일 뿐이라 실패해도 등록 자체는 막지 않는다.
  let roomName = `객실 #${roomId}`;
  try {
    const room = await RoomApi.detail(roomId);
    roomName = room.name;
  } catch (e) {
    // no-op — 위 기본값을 그대로 쓴다.
  }

  const backHref = `#/accommodations/${accommodationId}/rooms/${roomId}`;

  body.innerHTML = `
    <a href="${backHref}" class="backlink">← ${escapeHtml(roomName)}</a>
    <h1>특가 등록</h1>
    <p class="lead" style="margin-bottom:24px;">
      ${escapeHtml(roomName)}의 특정 숙박 날짜를 정해진 기간 동안만 특가로 판매합니다.
      등록 직후에는 <b>판매 예정</b> 상태이고, 판매 시작 시각이 되면 자동으로 판매 중으로 바뀝니다.
    </p>

    <div class="card" style="max-width:560px;">
      <form id="offer-form">
        <label>특가 가격
          <input type="number" name="price" required min="1" step="1" placeholder="80000" />
          <span class="hint">0원보다 커야 합니다.</span>
        </label>

        <div class="row">
          <label>판매 시작
            <input type="datetime-local" name="startsAt" required value="${localDateTimeInput(1)}" />
          </label>
          <label>판매 종료
            <input type="datetime-local" name="endsAt" required value="${localDateTimeInput(25)}" />
          </label>
        </div>

        <div class="row">
          <label>체크인 날짜
            <input type="date" name="checkInDate" required value="${addDaysIso(14)}" />
          </label>
          <label>체크아웃 날짜
            <input type="date" name="checkOutDate" required value="${addDaysIso(16)}" />
          </label>
        </div>

        <div id="offer-error"></div>
        <button type="submit" class="btn-primary btn-lg">특가 등록</button>
      </form>
      <div id="offer-result" style="margin-top:12px;"></div>
    </div>
  `;

  document.getElementById("offer-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const errorEl = document.getElementById("offer-error");
    const submitBtn = e.target.querySelector("button[type=submit]");
    errorEl.innerHTML = "";
    submitBtn.disabled = true;

    try {
      const created = await AdminApi.createSpecialOffer(
        accommodationId,
        roomId,
        {
          price: Number(form.get("price")),
          startsAt: withSeconds(form.get("startsAt")),
          endsAt: withSeconds(form.get("endsAt")),
          checkInDate: form.get("checkInDate"),
          checkOutDate: form.get("checkOutDate"),
        }
      );

      document.getElementById("offer-result").innerHTML = `
        <div class="success-box">
          특가가 등록되었습니다. (특가 ID ${created.specialOfferId}) ${statusTag(created.status, "sale")}<br />
          ${formatMoney(created.price)} ·
          숙박 ${escapeHtml(created.checkInDate)} ~ ${escapeHtml(created.checkOutDate)}
        </div>`;
      showToast("특가가 등록되었습니다.");
      e.target.reset();
    } catch (err) {
      errorEl.innerHTML = errorBox(err);
    } finally {
      submitBtn.disabled = false;
    }
  });
}
