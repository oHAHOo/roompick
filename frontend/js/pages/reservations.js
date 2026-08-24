import { ReservationApi, isLoggedIn } from "../api.js";
import { mount, escapeHtml, errorBox, spinner, formatMoney, statusTag, formatDate } from "../ui.js";
import { navigate } from "../router.js";

const state = { page: 0 };

export async function renderReservations() {
  if (!isLoggedIn()) {
    navigate("/login");
    return;
  }

  mount(`
    <div class="container page">
      <h1>내 예약</h1>
      <p class="lead" style="margin-bottom:28px;">예약 상태를 확인하고 결제·취소할 수 있어요.</p>
      <div id="reservations-body">${spinner()}</div>
    </div>
  `);

  const body = document.getElementById("reservations-body");

  try {
    const data = await ReservationApi.myList(state.page, 10);

    body.innerHTML = `
      ${
        data.content.length
          ? data.content
              .map(
                (r) => `
        <a class="res-card" href="#/reservations/${r.reservationId}">
          <div class="res-main">
            <div style="margin-bottom:4px;">${statusTag(r.status)}</div>
            <div class="stay-name" style="font-size:1.05rem;">${escapeHtml(r.accommodationName)}</div>
            <div class="muted">
              ${escapeHtml(r.roomName)} · ${formatDate(r.checkInDate)} ~ ${formatDate(r.checkOutDate)} · ${r.guestCount}명
            </div>
          </div>
          <div class="res-side">
            <div class="price">${formatMoney(r.totalAmount)}</div>
            <span class="tiny">상세 보기 →</span>
          </div>
        </a>`
              )
              .join("")
          : `<div class="empty">
              <div class="empty-icon">🧳</div>
              <p style="margin:0 0 16px;">아직 예약 내역이 없어요.</p>
              <a class="btn btn-primary" href="#/">숙소 둘러보기</a>
            </div>`
      }
      ${
        data.totalPages > 1
          ? `<div class="pagination">
              <button id="prev-page" ${data.pageNumber === 0 ? "disabled" : ""}>← 이전</button>
              <span class="page-info">${data.pageNumber + 1} / ${data.totalPages}</span>
              <button id="next-page" ${data.last ? "disabled" : ""}>다음 →</button>
            </div>`
          : ""
      }
    `;

    document.getElementById("prev-page")?.addEventListener("click", () => {
      state.page = Math.max(0, state.page - 1);
      renderReservations();
    });
    document.getElementById("next-page")?.addEventListener("click", () => {
      state.page += 1;
      renderReservations();
    });
  } catch (err) {
    body.innerHTML = errorBox(err);
  }
}
