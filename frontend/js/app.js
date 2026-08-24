import { route, startRouter, setNotFound, navigate } from "./router.js";
import { AuthApi, isLoggedIn, getCurrentRole, clearTokens, getBaseUrl, setBaseUrl } from "./api.js";
import { showToast } from "./ui.js";
import { renderHome } from "./pages/home.js";
import { renderLogin } from "./pages/login.js";
import { renderSignup } from "./pages/signup.js";
import { renderAccommodationDetail } from "./pages/accommodationDetail.js";
import { renderRoomDetail } from "./pages/roomDetail.js";
import { renderReservations } from "./pages/reservations.js";
import { renderReservationDetail } from "./pages/reservationDetail.js";
import { renderPayment } from "./pages/payment.js";
import { renderAdmin } from "./pages/admin.js";
import { renderSpecialOffer } from "./pages/specialOffer.js";
import { renderRoomCreate } from "./pages/roomCreate.js";
import { renderTimeSaleCreate } from "./pages/timeSaleCreate.js";
import { renderSpecialOfferCreate } from "./pages/specialOfferCreate.js";

route("/", renderHome);
route("/login", renderLogin);
route("/signup", renderSignup);
route("/accommodations/:id", renderAccommodationDetail);
// "new"가 :id 패턴에 먼저 잡히지 않도록 동적 객실 라우트보다 앞에 둔다.
route("/accommodations/:accId/rooms/new", renderRoomCreate);
route("/accommodations/:accId/timesales/new", renderTimeSaleCreate);
// 숙소를 거쳐 들어온 객실. 숙소 ID를 알 수 있어 관리자 상태 변경까지 가능하다.
route("/accommodations/:accId/rooms/:id", renderRoomDetail);
route("/accommodations/:accId/rooms/:id/special-offers/new", renderSpecialOfferCreate);
route("/rooms/:id", renderRoomDetail);
route("/reservations", renderReservations);
route("/reservations/:id", renderReservationDetail);
route("/payments/:reservationId", renderPayment);
route("/admin", renderAdmin);
route("/special-offers", renderSpecialOffer);
route("/special-offers/:offerId", renderSpecialOffer);

setNotFound(() => {
  document.getElementById("app").innerHTML = `
    <div class="container page">
      <div class="empty">
        <div class="empty-icon">🧭</div>
        <p style="margin:0 0 16px;">페이지를 찾을 수 없습니다.</p>
        <a class="btn btn-primary" href="#/">홈으로</a>
      </div>
    </div>`;
});

// 백엔드 주소는 화면에 노출하지 않는다. 설정 방법은 두 가지뿐이다.
//   1) 배포 도메인에 ?apiBase=http://... 로 한 번 방문 (api.js의 initBaseUrlFromQuery)
//   2) 브라우저 콘솔에서 직접: RoomPickConfig.setApiBase("http://...")
window.RoomPickConfig = {
  getApiBase: getBaseUrl,
  setApiBase: (url) => {
    setBaseUrl(url);
    console.log(`[RoomPick] API 서버 주소를 ${getBaseUrl()}로 변경했습니다. 새로고침하세요.`);
  },
};

export function renderNav() {
  const nav = document.getElementById("nav");
  const loggedIn = isLoggedIn();
  const role = getCurrentRole();

  nav.innerHTML = `
    <a href="#/">숙소 찾기</a>
    ${loggedIn ? `<a href="#/reservations">내 예약</a>` : ""}
    ${loggedIn && role === "ADMIN" ? `<a href="#/admin">관리자</a>` : ""}
    ${
      loggedIn
        ? `<button class="link" id="logout-btn">로그아웃</button>`
        : `<a href="#/login">로그인</a>
           <a href="#/signup" class="cta">회원가입</a>`
    }
  `;

  document.getElementById("logout-btn")?.addEventListener("click", async () => {
    try {
      await AuthApi.logout();
    } catch (e) {
      // 로그아웃 API 실패와 무관하게 로컬 토큰은 정리한다.
    }
    clearTokens();
    showToast("로그아웃되었습니다.");
    renderNav();
    navigate("/");
  });
}

renderNav();
startRouter();
