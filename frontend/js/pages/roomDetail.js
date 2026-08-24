import {
  RoomApi, ReservationApi, AdminApi, isLoggedIn, getCurrentRole, newIdempotencyKey,
} from "../api.js";
import {
  mount, escapeHtml, errorBox, spinner, formatMoney, statusTag,
  todayIso, addDaysIso, gradientFor, showToast, priceBlock,
} from "../ui.js";
import { navigate } from "../router.js";

function gallery(room) {
  const urls = room.imageUrls ?? [];
  if (urls.length === 0) {
    return `<div class="gallery single">
      <div class="g-fill" style="background:${gradientFor(room.roomId)}">${escapeHtml(room.name.charAt(0))}</div>
    </div>`;
  }
  const shown = urls.slice(0, 3);
  return `<div class="gallery${shown.length === 1 ? " single" : ""}">
    ${shown.map((u) => `<img src="${escapeHtml(u)}" alt="${escapeHtml(room.name)}" />`).join("")}
  </div>`;
}

/**
 * 관리자에게만 보이는 객실 공개 상태 패널.
 *
 * ADMIN 토큰으로는 INACTIVE 객실도 상세 조회가 되므로, 실제 room.status를 받아
 * 초기 상태를 그대로 반영한다. INACTIVE로 새로고침해도 관리자는 계속 조회할 수 있다.
 */
function adminPanel(accommodationId, roomId, status) {
  if (!accommodationId) {
    return `
      <div class="card" style="margin-top:20px;border-color:var(--border-strong);">
        <h3>관리자</h3>
        <p class="muted" style="margin:0;">
          숙소 상세를 거쳐 들어오면 이 객실을 비공개하거나 특가를 등록할 수 있습니다.
        </p>
      </div>`;
  }
  const active = status !== "INACTIVE";
  return `
    <div class="card" id="admin-panel" style="margin-top:20px;border-color:var(--border-strong);">
      <h3>관리자</h3>
      <div id="admin-status" style="margin-bottom:12px;">
        현재 상태 ${statusTag(active ? "ACTIVE" : "INACTIVE")}
        <span class="muted">${active ? "사용자 화면에 노출되고 있습니다." : "사용자 화면에서 숨겨졌습니다."}</span>
      </div>
      <div class="row" style="margin-top:4px;">
        <a class="btn btn-primary" href="#/accommodations/${accommodationId}/rooms/${roomId}/special-offers/new">특가 등록</a>
        <button id="toggle-status" class="${active ? "btn-danger" : "btn-primary"}">
          ${active ? "객실 비공개" : "다시 공개하기"}
        </button>
      </div>
      <div id="admin-error" style="margin-top:12px;"></div>
    </div>`;
}

export async function renderRoomDetail(params) {
  const roomId = params.id;
  // 숙소 상세를 거쳐 들어오면 숙소 ID를 알 수 있다. 객실 상태 변경 API가
  // 숙소 ID를 요구하는데 객실 상세 응답에는 들어있지 않아서 URL로 넘긴다.
  const accommodationId = params.accId ?? null;
  const isAdmin = isLoggedIn() && getCurrentRole() === "ADMIN";

  mount(`<div class="container page" id="room-body">${spinner()}</div>`);
  const body = document.getElementById("room-body");

  let room;
  try {
    room = await RoomApi.detail(roomId);
  } catch (err) {
    body.innerHTML = `<a href="#/" class="backlink">← 숙소 목록</a>${errorBox(err)}`;
    return;
  }

  const backHref = accommodationId ? `#/accommodations/${accommodationId}` : "#/";
  const backLabel = accommodationId ? "← 숙소 상세" : "← 숙소 목록";

  body.innerHTML = `
    <a href="${backHref}" class="backlink">${backLabel}</a>

    ${gallery(room)}

    <div class="detail-grid">
      <div>
        <h1>${escapeHtml(room.name)}</h1>
        <p class="lead" style="margin-bottom:20px;">
          객실 ${escapeHtml(room.roomNumber)} · 기준 ${room.standardCapacity}명 · 최대 ${room.maxCapacity}명
        </p>

        <div class="card">
          <h3>객실 소개</h3>
          <p style="color:var(--ink-2);margin:0;">
            ${escapeHtml(room.description || "등록된 소개글이 없습니다.")}
          </p>
        </div>

        ${isAdmin ? adminPanel(accommodationId, roomId, room.status) : ""}
      </div>

      <aside>
        <div class="booking-box">
          <div style="display:flex;align-items:baseline;flex-wrap:wrap;gap:6px;margin-bottom:16px;">
            ${priceBlock(room.pricePerNight, room.normalPricePerNight, room.discountApplied, { size: "1.5rem" })}
            <span class="muted">/ 1박</span>
          </div>

          <form id="avail-form">
            <div class="row tight">
              <label>체크인
                <input type="date" name="checkInDate" min="${todayIso()}" value="${addDaysIso(7)}" required />
              </label>
              <label>체크아웃
                <input type="date" name="checkOutDate" min="${todayIso()}" value="${addDaysIso(9)}" required />
              </label>
            </div>
            <label>인원
              <input type="number" name="guestCount" min="1" max="${room.maxCapacity}" value="2" required />
              <span class="hint">최대 ${room.maxCapacity}명까지 가능합니다.</span>
            </label>
            <button type="submit" class="btn-dark btn-block btn-lg">예약 가능 여부 확인</button>
          </form>

          <div id="avail-result" style="margin-top:16px;"></div>
        </div>
      </aside>
    </div>
  `;

  if (isAdmin && accommodationId) {
    let status = room.status ?? "ACTIVE";
    const toggleBtn = document.getElementById("toggle-status");

    toggleBtn.addEventListener("click", async () => {
      const goingInactive = status === "ACTIVE";
      const errorEl = document.getElementById("admin-error");
      errorEl.innerHTML = "";
      toggleBtn.disabled = true;
      toggleBtn.textContent = goingInactive ? "비공개 처리 중…" : "공개 처리 중…";

      try {
        if (goingInactive) {
          // 논리 삭제 API. Void 응답이라(data: null) 결과 상태를 서버에서 받지 않고 로컬로 반영한다.
          await AdminApi.deleteRoom(accommodationId, roomId);
          status = "INACTIVE";
        } else {
          const result = await AdminApi.updateRoomStatus(accommodationId, roomId, "ACTIVE");
          status = result.status;
        }

        document.getElementById("admin-status").innerHTML = `
          현재 상태 ${statusTag(status)}
          <span class="muted">${
            status === "ACTIVE"
              ? "사용자 화면에 노출되고 있습니다."
              : "사용자 화면에서 숨겨졌습니다. 이 화면을 벗어나면 다시 들어올 수 없습니다."
          }</span>`;

        toggleBtn.className = status === "ACTIVE" ? "btn-danger" : "btn-primary";
        toggleBtn.textContent = status === "ACTIVE" ? "객실 비공개" : "다시 공개하기";
        showToast(status === "ACTIVE" ? "객실을 다시 공개했습니다." : "객실을 비공개했습니다.");
      } catch (err) {
        errorEl.innerHTML = errorBox(err);
        toggleBtn.textContent = status === "ACTIVE" ? "객실 비공개" : "다시 공개하기";
      } finally {
        toggleBtn.disabled = false;
      }
    });
  }

  document.getElementById("avail-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const checkInDate = form.get("checkInDate");
    const checkOutDate = form.get("checkOutDate");
    const guestCount = Number(form.get("guestCount"));
    const resultEl = document.getElementById("avail-result");
    resultEl.innerHTML = `<div class="muted" style="text-align:center;padding:10px;">확인 중…</div>`;

    try {
      const avail = await RoomApi.availability(roomId, { checkInDate, checkOutDate, guestCount });

      resultEl.innerHTML = `
        <div style="margin-bottom:12px;">${statusTag(avail.status)}</div>
        <div class="summary-line">
          <span>${formatMoney(avail.pricePerNight)} × ${avail.nightCount}박</span>
          <span>${formatMoney(avail.pricePerNight * avail.nightCount)}</span>
        </div>
        <div class="summary-line total"><span>총 결제 금액</span><span>${formatMoney(avail.totalAmount)}</span></div>
        ${
          avail.available
            ? `<button id="reserve-btn" class="btn-primary btn-block btn-lg" style="margin-top:16px;">예약하기</button>`
            : `<div class="error-box" style="margin-top:14px;">${escapeHtml(avail.unavailableReason || "선택한 날짜에는 예약할 수 없습니다.")}</div>`
        }
        <div id="reserve-error"></div>
      `;

      // 이 조회 결과로 만들 예약 한 건에 대응하는 키다. 같은 버튼을 다시 눌러도
      // 같은 키가 전달되므로 중복 클릭으로 예약이 두 건 생기지 않는다.
      const idempotencyKey = newIdempotencyKey();

      document.getElementById("reserve-btn")?.addEventListener("click", async () => {
        if (!isLoggedIn()) {
          navigate("/login");
          return;
        }
        const btn = document.getElementById("reserve-btn");
        btn.disabled = true;
        btn.textContent = "예약 생성 중…";
        try {
          const reservation = await ReservationApi.create(
            { roomId: Number(roomId), checkInDate, checkOutDate, guestCount },
            idempotencyKey
          );
          navigate(`/reservations/${reservation.reservationId}`);
        } catch (err) {
          document.getElementById("reserve-error").innerHTML = `<div style="margin-top:12px;">${errorBox(err)}</div>`;
          btn.disabled = false;
          btn.textContent = "예약하기";
        }
      });
    } catch (err) {
      resultEl.innerHTML = errorBox(err);
    }
  });
}
