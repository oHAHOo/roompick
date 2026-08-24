import { AccommodationApi, AdminApi, isLoggedIn, getCurrentRole } from "../api.js";
import {
  mount, escapeHtml, errorBox, spinner, formatMoney, mediaBlock, gradientFor,
  showToast, confirmDialog, priceBlock, statusTag,
} from "../ui.js";
import { navigate } from "../router.js";

function gallery(detail) {
  const urls = detail.imageUrls ?? [];
  if (urls.length === 0) {
    return `<div class="gallery single">
      <div class="g-fill" style="background:${gradientFor(detail.accommodationId)}">
        ${escapeHtml(detail.name.charAt(0))}
      </div>
    </div>`;
  }
  const shown = urls.slice(0, 3);
  return `<div class="gallery${shown.length === 1 ? " single" : ""}">
    ${shown.map((u) => `<img src="${escapeHtml(u)}" alt="${escapeHtml(detail.name)}" />`).join("")}
  </div>`;
}

/**
 * 관리자에게만 보이는 패널. 객실 등록은 화면에 바로 그리지 않고 전용 페이지
 * (#/accommodations/{id}/rooms/new)로 이동하는 버튼만 둔다.
 *
 * ADMIN 토큰으로는 INACTIVE 숙소도 상세 조회가 되므로, 현재 상태에 따라
 * 비공개(DELETE 논리 삭제) 또는 다시 공개하기(PATCH 상태 변경) 버튼을 보여준다.
 * 다시 공개해도 비공개 전환 시 함께 내려간 객실들은 자동으로 살아나지 않는다.
 */
function adminPanel(id, roomCount, status) {
  const active = status !== "INACTIVE";
  return `
    <div class="card" id="admin-panel" style="margin-top:28px;border-color:var(--border-strong);">
      <h3>관리자</h3>
      <div id="admin-status" style="margin-bottom:12px;">
        현재 상태 ${statusTag(active ? "ACTIVE" : "INACTIVE")}
        <span class="muted">${active ? "사용자 화면에 노출되고 있습니다." : "사용자 화면에서 숨겨졌습니다."}</span>
      </div>
      <div class="row" style="margin-top:4px;">
        <a class="btn btn-primary" href="#/accommodations/${id}/rooms/new">객실 등록</a>
        <a class="btn btn-primary" href="#/accommodations/${id}/timesales/new">타임세일 등록</a>
        <button id="toggle-acc-status" class="${active ? "btn-danger" : "btn-primary"}">
          ${active ? "숙소 비공개" : "다시 공개하기"}
        </button>
      </div>
      <p class="muted" style="margin:12px 0 0;">
        ${
          active
            ? `비공개로 전환하면 이 숙소와 소속 객실 ${roomCount}개가 모두 사용자 화면에서 숨겨집니다 (상태: INACTIVE). 예약·결제 이력은 남습니다.`
            : "다시 공개해도 비공개 전환 시 함께 내려간 객실은 자동으로 살아나지 않습니다. 필요한 객실은 객실 상세에서 각각 다시 공개하세요."
        }
      </p>
      <div id="admin-delete-error" style="margin-top:12px;"></div>
    </div>`;
}

export async function renderAccommodationDetail(params) {
  const id = params.id;
  const isAdmin = isLoggedIn() && getCurrentRole() === "ADMIN";

  mount(`<div class="container page" id="acc-body">${spinner()}</div>`);
  const body = document.getElementById("acc-body");

  try {
    const [detail, rooms] = await Promise.all([
      AccommodationApi.detail(id),
      AccommodationApi.rooms(id),
    ]);

    body.innerHTML = `
      <a href="#/" class="backlink">← 숙소 목록</a>

      ${gallery(detail)}

      <div class="detail-grid">
        <div>
          <h1>${escapeHtml(detail.name)}</h1>
          <p class="lead" style="margin-bottom:22px;">📍 ${escapeHtml(detail.address)}</p>

          <div class="card" style="margin-bottom:32px;">
            <h3>숙소 소개</h3>
            <p style="color:var(--ink-2);margin:0 0 16px;">
              ${escapeHtml(detail.description || "등록된 소개글이 없습니다.")}
            </p>
            <div class="row tight" style="gap:10px;">
              <span class="tag info">체크인 ${escapeHtml((detail.checkInTime || "").slice(0, 5))}</span>
              <span class="tag info">체크아웃 ${escapeHtml((detail.checkOutTime || "").slice(0, 5))}</span>
            </div>
          </div>

          <div class="section-head">
            <div>
              <h2>객실 선택</h2>
              <p class="lead">${rooms.length}개의 객실을 예약할 수 있어요.</p>
            </div>
          </div>

          ${
            rooms.length
              ? rooms
                  .map(
                    (r) => `
            <a class="room-row" href="#/accommodations/${id}/rooms/${r.roomId}">
              ${mediaBlock(r.imageUrl, r.roomId, r.name, "room-media")}
              <div class="room-body">
                <div style="min-width:0;">
                  <div class="stay-name" style="font-size:1.05rem;">
                    ${escapeHtml(r.name)}${r.status === "INACTIVE" ? ` ${statusTag("INACTIVE")}` : ""}
                  </div>
                  <div class="muted">기준 ${r.standardCapacity}명 · 최대 ${r.maxCapacity}명</div>
                </div>
                <div style="text-align:right;">
                  <div>${priceBlock(r.pricePerNight, r.normalPricePerNight, r.discountApplied)}<small>/박</small></div>
                  <span class="tiny">날짜 선택 →</span>
                </div>
              </div>
            </a>`
                  )
                  .join("")
              : `<div class="empty"><div class="empty-icon">🛏️</div>예약 가능한 객실이 아직 없습니다.</div>`
          }

          ${isAdmin ? adminPanel(id, rooms.length, detail.status) : ""}
        </div>

        <aside>
          <div class="booking-box">
            <h3>예약 안내</h3>
            <div class="summary-line"><span>체크인</span><span>${escapeHtml((detail.checkInTime || "").slice(0, 5))}부터</span></div>
            <div class="summary-line"><span>체크아웃</span><span>${escapeHtml((detail.checkOutTime || "").slice(0, 5))}까지</span></div>
            <div class="summary-line"><span>객실 수</span><span>${rooms.length}개</span></div>
            ${
              rooms.length
                ? `<div class="summary-line total">
                    <span>최저가</span>
                    <span>${formatMoney(Math.min(...rooms.map((r) => r.pricePerNight)))}</span>
                  </div>
                  <p class="tiny" style="margin-top:14px;">객실을 선택하면 날짜별 예약 가능 여부를 확인할 수 있어요.</p>`
                : ""
            }
          </div>
        </aside>
      </div>
    `;

    let status = detail.status ?? "ACTIVE";

    document.getElementById("toggle-acc-status")?.addEventListener("click", async () => {
      const goingInactive = status === "ACTIVE";
      if (goingInactive) {
        const confirmed = await confirmDialog(
          `"${detail.name}" 숙소와 소속 객실 ${rooms.length}개를 모두 비공개로 전환할까요?`,
          { confirmLabel: "비공개로 전환" }
        );
        if (!confirmed) return;
      }

      const btn = document.getElementById("toggle-acc-status");
      const errorEl = document.getElementById("admin-delete-error");
      errorEl.innerHTML = "";
      btn.disabled = true;
      btn.textContent = goingInactive ? "비공개 처리 중…" : "공개 처리 중…";

      try {
        if (goingInactive) {
          await AdminApi.deleteAccommodation(id);
          status = "INACTIVE";
          showToast("숙소를 비공개했습니다.");
        } else {
          const result = await AdminApi.updateAccommodationStatus(id, "ACTIVE");
          status = result.status;
          showToast("숙소를 다시 공개했습니다.");
        }

        document.getElementById("admin-status").innerHTML = `
          현재 상태 ${statusTag(status)}
          <span class="muted">${status === "ACTIVE" ? "사용자 화면에 노출되고 있습니다." : "사용자 화면에서 숨겨졌습니다."}</span>`;

        btn.className = status === "ACTIVE" ? "btn-danger" : "btn-primary";
        btn.textContent = status === "ACTIVE" ? "숙소 비공개" : "다시 공개하기";
      } catch (err) {
        errorEl.innerHTML = errorBox(err);
        btn.textContent = status === "ACTIVE" ? "숙소 비공개" : "다시 공개하기";
      } finally {
        btn.disabled = false;
      }
    });
  } catch (err) {
    body.innerHTML = `<a href="#/" class="backlink">← 숙소 목록</a>${errorBox(err)}`;
  }
}
