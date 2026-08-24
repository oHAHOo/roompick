import { AccommodationApi, AdminApi, isLoggedIn, getCurrentRole } from "../api.js";
import {
  mount, escapeHtml, errorBox, spinner, showToast, statusTag,
  localDateTimeInput, withSeconds,
} from "../ui.js";

/**
 * 관리자 전용 타임세일 등록 화면. accommodationId는 URL(params.accId)에서
 * 그대로 받으므로 입력창이 없다 — 숙소 상세의 "타임세일 등록" 버튼을 눌러야만
 * 이 화면에 온다.
 *
 * 적용 대상을 숙소 전체로 두면 roomId를 보내지 않고(null), 특정 객실을
 * 고르면 해당 객실에만 적용된다. 같은 대상에 기간이 겹치는 타임세일이
 * 있으면 TIME_SALE_PERIOD_OVERLAP(409)로 거절된다.
 */
export async function renderTimeSaleCreate(params) {
  const accommodationId = params.accId;

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

  mount(`<div class="container page" id="timesale-create-body">${spinner()}</div>`);
  const body = document.getElementById("timesale-create-body");

  // 이름을 보여주기 위한 조회일 뿐이라 실패해도 등록 자체는 막지 않는다.
  let accommodationName = `숙소 #${accommodationId}`;
  let rooms = [];
  try {
    const [detail, roomList] = await Promise.all([
      AccommodationApi.detail(accommodationId),
      AccommodationApi.rooms(accommodationId),
    ]);
    accommodationName = detail.name;
    // 타임세일은 사용자에게 공개될 객실에만 의미가 있으므로 비공개 객실은 목록에서 제외한다.
    rooms = roomList.filter((r) => r.status !== "INACTIVE");
  } catch (e) {
    // no-op — 위 기본값을 그대로 쓴다. 객실 목록 조회에 실패해도
    // "숙소 전체" 적용은 여전히 가능해야 하므로 등록 자체는 막지 않는다.
  }

  body.innerHTML = `
    <a href="#/accommodations/${accommodationId}" class="backlink">← ${escapeHtml(accommodationName)}</a>
    <h1>타임세일 등록</h1>
    <p class="lead" style="margin-bottom:24px;">
      ${escapeHtml(accommodationName)}에 지정한 기간 동안 정가에서 할인율만큼 자동으로 깎아 판매합니다.
    </p>

    <div class="card" style="max-width:560px;">
      <form id="timesale-form">
        <label>적용 대상
          <select name="target" id="timesale-target">
            <option value="ACCOMMODATION">숙소 전체</option>
            <option value="ROOM">특정 객실만</option>
          </select>
        </label>

        <label id="timesale-room-field" hidden>객실
          ${
            rooms.length
              ? `<select name="roomId">
                  ${rooms
                    .map(
                      (r) => `<option value="${r.roomId}">${escapeHtml(r.name)}</option>`
                    )
                    .join("")}
                </select>`
              : `<input type="number" name="roomId" min="1" />
                 <span class="hint">공개된 객실이 없거나 목록을 불러오지 못해 ID를 직접 입력해야 합니다.</span>`
          }
        </label>

        <label>할인율 (%)
          <input type="number" name="discountRate" required min="1" max="99" value="20" />
          <span class="hint">1% 이상 99% 이하</span>
        </label>

        <div class="row">
          <label>시작 시각
            <input type="datetime-local" name="startAt" required value="${localDateTimeInput(1)}" />
          </label>
          <label>종료 시각
            <input type="datetime-local" name="endAt" required value="${localDateTimeInput(25)}" />
          </label>
        </div>

        <div id="timesale-error"></div>
        <button type="submit" class="btn-primary btn-lg">타임세일 등록</button>
      </form>
      <div id="timesale-result" style="margin-top:12px;"></div>
    </div>
  `;

  const targetSelect = document.getElementById("timesale-target");
  const roomField = document.getElementById("timesale-room-field");
  const roomControl = roomField.querySelector("select, input");

  /**
   * hidden만으로는 폼 컨트롤이 여전히 값을 가진 채 FormData에 포함된다.
   * disabled까지 같이 꺼야 "숙소 전체"를 골랐을 때 이전에 선택해 둔
   * 객실이 실수로 함께 전송되지 않는다.
   */
  function syncRoomField() {
    const roomTargeted = targetSelect.value === "ROOM";
    roomField.hidden = !roomTargeted;
    roomControl.disabled = !roomTargeted;
  }

  syncRoomField();
  targetSelect.addEventListener("change", syncRoomField);

  document.getElementById("timesale-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const errorEl = document.getElementById("timesale-error");
    const submitBtn = e.target.querySelector("button[type=submit]");
    errorEl.innerHTML = "";
    submitBtn.disabled = true;

    const roomTargeted = form.get("target") === "ROOM";
    const roomIdValue = form.get("roomId");

    try {
      const created = await AdminApi.createTimeSale(accommodationId, {
        // 숙소 전체 적용은 roomId를 null로 보낸다.
        roomId: roomTargeted && roomIdValue ? Number(roomIdValue) : null,
        discountRate: Number(form.get("discountRate")),
        startAt: withSeconds(form.get("startAt")),
        endAt: withSeconds(form.get("endAt")),
      });

      document.getElementById("timesale-result").innerHTML = `
        <div class="success-box">
          타임세일이 등록되었습니다. (타임세일 ID ${created.timeSaleId}) ${statusTag(created.status, "sale")}<br />
          대상: ${created.roomId ? `객실 ${created.roomId}` : "숙소 전체"} ·
          ${created.discountRate}% 할인
        </div>`;
      showToast("타임세일이 등록되었습니다.");
      e.target.reset();
      syncRoomField();
    } catch (err) {
      errorEl.innerHTML = errorBox(err);
    } finally {
      submitBtn.disabled = false;
    }
  });
}
