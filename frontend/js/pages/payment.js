import { PaymentApi, ReservationApi, MemberApi, isLoggedIn, ApiError } from "../api.js";
import { mount, escapeHtml, errorBox, spinner, formatMoney, showToast, statusTag } from "../ui.js";
import { navigate } from "../router.js";

/**
 * 준비된 결제를 기억해 둔다.
 *
 * 결제 준비는 예약당 한 번만 허용되고(PAYMENT_ALREADY_EXISTS),
 * paymentId를 다시 조회할 수 있는 API가 없다. 결제 페이지에 다시 들어왔을 때
 * 무조건 준비 API를 부르면 409가 나면서 결제를 끝낼 방법이 사라지므로,
 * 준비에 성공한 결제 정보를 저장해 두고 재진입 시 재사용한다.
 */
const paymentCacheKey = (reservationId) => `rp_payment_${reservationId}`;

function readCachedPayment(reservationId) {
  try {
    const raw = sessionStorage.getItem(paymentCacheKey(reservationId));
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

function cachePayment(reservationId, payment) {
  try {
    sessionStorage.setItem(paymentCacheKey(reservationId), JSON.stringify(payment));
  } catch (e) {
    // 저장에 실패해도 결제 진행 자체는 막지 않는다.
  }
}

function clearCachedPayment(reservationId) {
  try {
    sessionStorage.removeItem(paymentCacheKey(reservationId));
  } catch (e) {
    // no-op
  }
}

export async function renderPayment(params) {
  if (!isLoggedIn()) {
    navigate("/login");
    return;
  }
  const reservationId = params.reservationId;
  mount(`<div class="container page" id="pay-body">${spinner()}</div>`);
  const body = document.getElementById("pay-body");

  let payment = readCachedPayment(reservationId);
  let reservation;

  try {
    reservation = await ReservationApi.detail(reservationId);
  } catch (err) {
    body.innerHTML = `<a href="#/reservations" class="backlink">← 내 예약</a>${errorBox(err)}`;
    return;
  }

  if (!payment) {
    try {
      payment = await PaymentApi.prepare(reservationId);
      cachePayment(reservationId, payment);
    } catch (err) {
      const alreadyExists = err instanceof ApiError && err.code === "PAYMENT_ALREADY_EXISTS";
      body.innerHTML = `
        <a href="#/reservations/${reservationId}" class="backlink">← 예약 상세</a>
        ${errorBox(err)}
        ${
          alreadyExists
            ? `<div class="info-box" style="margin-top:10px;">
                 이 예약은 이미 결제가 준비되어 있습니다. 예약 상세에서 현재 상태를 확인해 주세요.
               </div>`
            : ""
        }
      `;
      return;
    }
  }

  const orderName = `${reservation.accommodation.name} · ${reservation.room.name}`;
  const hasPortOneConfig = Boolean(payment.storeId && payment.channelKey && payment.portOnePaymentId);

  body.innerHTML = `
    <a href="#/reservations/${reservationId}" class="backlink">← 예약 상세</a>

    <div style="max-width:520px;margin:0 auto;">
      <h1 style="text-align:center;">결제</h1>
      <p class="lead" style="text-align:center;margin-bottom:28px;">${escapeHtml(orderName)}</p>

      <div class="card" style="margin-bottom:20px;">
        <div class="summary-line"><span>예약 번호</span><span>#${escapeHtml(reservationId)}</span></div>
        <div class="summary-line"><span>결제 번호</span><span>#${payment.paymentId}</span></div>
        <div class="summary-line"><span>상태</span><span>${statusTag(payment.status)}</span></div>
        <div class="summary-line total"><span>결제 금액</span><span>${formatMoney(payment.amount)}</span></div>
      </div>

      ${
        hasPortOneConfig
          ? `<button id="portone-btn" class="btn-primary btn-block btn-lg">${formatMoney(payment.amount)} 결제하기</button>`
          : `<div class="error-box" style="margin-bottom:10px;">
               PortOne 결제 설정(storeId/channelKey)이 없어 실제 결제를 열 수 없습니다.
               백엔드에 PORTONE_STORE_ID·PORTONE_CHANNEL_KEY가 설정돼 있는지 확인해 주세요.
             </div>`
      }
      <div id="portone-error" style="margin-top:10px;"></div>

      <details style="margin-top:24px;">
        <summary class="tiny" style="cursor:pointer;">실제 PG 없이 상태만 테스트 (Mock)</summary>
        <div style="display:flex;flex-direction:column;gap:10px;margin-top:12px;">
          <button id="approve-btn" class="btn-primary btn-block">Mock 결제 성공 처리</button>
        </div>
        <div id="mock-error" style="margin-top:10px;"></div>
      </details>
    </div>
  `;

  document.getElementById("portone-btn")?.addEventListener("click", () => runPortOneCheckout(payment, orderName, reservationId));

  document.getElementById("approve-btn").addEventListener("click", async (e) => {
    e.target.disabled = true;
    try {
      await PaymentApi.approve(payment.paymentId, payment.amount);
      clearCachedPayment(reservationId);
      showToast("결제가 완료되었습니다.");
      navigate(`/reservations/${reservationId}`);
    } catch (err) {
      document.getElementById("mock-error").innerHTML = errorBox(err);
      e.target.disabled = false;
    }
  });

}

/** PortOne V2 브라우저 SDK가 로드될 때까지 기다린다. index.html에서 <script>로 불러온다. */
function waitForPortOneSdk(timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    if (window.PortOne) {
      resolve(window.PortOne);
      return;
    }
    const start = Date.now();
    const timer = setInterval(() => {
      if (window.PortOne) {
        clearInterval(timer);
        resolve(window.PortOne);
      } else if (Date.now() - start > timeoutMs) {
        clearInterval(timer);
        reject(new Error("PortOne 결제 SDK를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요."));
      }
    }, 100);
  });
}

async function runPortOneCheckout(payment, orderName, reservationId) {
  const btn = document.getElementById("portone-btn");
  const errorEl = document.getElementById("portone-error");
  errorEl.innerHTML = "";
  btn.disabled = true;
  btn.textContent = "결제창 여는 중…";

  try {
    const [PortOne, me] = await Promise.all([
      waitForPortOneSdk(),
      MemberApi.me(),
    ]);

    // storeId/channelKey/portOnePaymentId는 결제 준비(prepare) 응답에서 그대로 받아 쓴다.
    // amount·orderName은 결제창에 표시만 될 뿐 실제 승인 금액은 서버가 PortOne API로
    // 다시 조회해 검증하므로(complete), 여기서 조작해도 결제 승인 금액은 바뀌지 않는다.
    const response = await PortOne.requestPayment({
      storeId: payment.storeId,
      channelKey: payment.channelKey,
      paymentId: payment.portOnePaymentId,
      orderName,
      totalAmount: payment.amount,
      currency: "CURRENCY_KRW",
      payMethod: "CARD",
      // 이니시스 V2 일반결제는 구매자 이메일·이름·휴대폰 번호가 모두 필수다.
      // 이메일·이름은 회원 정보 조회 API로 받아온 실제 값이다. 휴대폰 번호는
      // 이 프로젝트가 회원가입에서 아예 수집하지 않아 실제 값이 없어서,
      // 결제 흐름 자체를 검증하기 위한 임시 고정값을 대신 넣는다.
      customer: { email: me.email, fullName: me.name, phoneNumber: "01012345678" },
    });

    if (response?.code) {
      // 사용자가 결제창을 닫았거나 PG에서 거절된 경우. 서버에는 아직 아무 것도
      // 확정되지 않았으므로 그대로 다시 시도할 수 있게 버튼만 복구한다.
      errorEl.innerHTML = errorBox({
        message: response.message || "결제가 취소되었거나 실패했습니다.",
        code: response.pgCode || response.code,
      });
      btn.disabled = false;
      btn.textContent = `${formatMoney(payment.amount)} 결제하기`;
      return;
    }

    btn.textContent = "결제 확인 중…";
    const result = await PaymentApi.complete(payment.paymentId);
    clearCachedPayment(reservationId);
    showToast("결제가 완료되었습니다.");
    navigate(`/reservations/${reservationId}`);
    void result;
  } catch (err) {
    errorEl.innerHTML = errorBox(err);
    btn.disabled = false;
    btn.textContent = `${formatMoney(payment.amount)} 결제하기`;
  }
}
