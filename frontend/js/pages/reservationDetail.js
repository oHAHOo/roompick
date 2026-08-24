import { ReservationApi, isLoggedIn } from "../api.js";
import {
  mount, escapeHtml, errorBox, spinner, formatMoney, statusTag,
  showToast, formatDate, formatDateTime, confirmDialog,
} from "../ui.js";
import { navigate } from "../router.js";

export async function renderReservationDetail(params) {
  if (!isLoggedIn()) {
    navigate("/login");
    return;
  }

  const id = params.id;
  mount(`<div class="container page" id="rd-body">${spinner()}</div>`);
  const body = document.getElementById("rd-body");

  async function load() {
    body.innerHTML = spinner();
    try {
      const r = await ReservationApi.detail(id);
      const pending = r.status === "PENDING_PAYMENT";

      body.innerHTML = `
        <a href="#/reservations" class="backlink">← 내 예약</a>

        <div style="margin-bottom:6px;">${statusTag(r.status)}</div>
        <h1>${escapeHtml(r.accommodation.name)}</h1>
        <p class="lead" style="margin-bottom:28px;">📍 ${escapeHtml(r.accommodation.address)}</p>

        ${
          pending
            ? `<div class="info-box" style="margin-bottom:20px;">
                결제를 완료해야 예약이 확정됩니다. 마감: ${formatDateTime(r.expiresAt)}
              </div>`
            : r.status === "CONFIRMED"
            ? `<div class="success-box" style="margin-bottom:20px;">예약이 확정되었습니다. 즐거운 여행 되세요!</div>`
            : ""
        }

        <div class="detail-grid">
          <div>
            <div class="card">
              <h3>예약 정보</h3>
              <div class="summary-line"><span>객실</span><span>${escapeHtml(r.room.name)} (${escapeHtml(r.room.roomNumber)})</span></div>
              <div class="summary-line"><span>체크인</span><span>${formatDate(r.checkInDate)}</span></div>
              <div class="summary-line"><span>체크아웃</span><span>${formatDate(r.checkOutDate)}</span></div>
              <div class="summary-line"><span>숙박</span><span>${r.nightCount}박</span></div>
              <div class="summary-line"><span>인원</span><span>${r.guestCount}명</span></div>
              <div class="summary-line"><span>예약 번호</span><span>#${r.reservationId}</span></div>
              ${r.canceledAt ? `<div class="summary-line"><span>취소 시각</span><span>${formatDateTime(r.canceledAt)}</span></div>` : ""}
            </div>
          </div>

          <aside>
            <div class="booking-box">
              <h3>결제 금액</h3>
              <div class="summary-line">
                <span>${formatMoney(r.pricePerNight)} × ${r.nightCount}박</span>
                <span>${formatMoney(r.pricePerNight * r.nightCount)}</span>
              </div>
              <div class="summary-line total"><span>총액</span><span>${formatMoney(r.totalAmount)}</span></div>

              <div style="display:flex;flex-direction:column;gap:9px;margin-top:20px;">
                ${pending ? `<button id="pay-btn" class="btn-primary btn-block btn-lg">결제 진행</button>` : ""}
                ${
                  // 결제 전(PENDING_PAYMENT) 예약만 취소할 수 있다. 결제 완료 예약을 취소하려면
                  // 환불이 함께 이뤄져야 하는데 아직 구현되지 않아 서버가 409
                  // RESERVATION_NOT_CANCELABLE로 거절한다. 눌러도 실패할 버튼은 아예 보여주지 않는다.
                  pending
                    ? `<button id="cancel-btn" class="btn-danger btn-block">예약 취소</button>`
                    : ""
                }
                ${
                  r.status === "CONFIRMED"
                    ? `<p class="tiny" style="margin:4px 0 0;text-align:center;">
                         결제가 완료된 예약은 화면에서 취소할 수 없습니다. 고객센터로 문의해 주세요.
                       </p>`
                    : ""
                }
              </div>
              <div id="rd-error" style="margin-top:12px;"></div>
            </div>
          </aside>
        </div>
      `;

      document.getElementById("pay-btn")?.addEventListener("click", () => {
        navigate(`/payments/${r.reservationId}`);
      });

      document.getElementById("cancel-btn")?.addEventListener("click", async () => {
        const confirmed = await confirmDialog("예약을 취소하시겠습니까?", { confirmLabel: "예약 취소" });
        if (!confirmed) return;
        const errorEl = document.getElementById("rd-error");
        errorEl.innerHTML = "";
        try {
          await ReservationApi.cancel(id);
          showToast("예약이 취소되었습니다.");
          load();
        } catch (err) {
          errorEl.innerHTML = errorBox(err);
        }
      });
    } catch (err) {
      body.innerHTML = `<a href="#/reservations" class="backlink">← 내 예약</a>${errorBox(err)}`;
    }
  }

  await load();
}
