export function mount(html) {
  document.getElementById("app").innerHTML = html;
}

/**
 * 파일을 고르지 않아도 비어 있는 `images` 파트가 전송되는데, 서버는 이를
 * IMAGE_001(업로드할 이미지 파일이 없습니다)로 거절한다. 이미지는 선택 항목이므로
 * 실제로 고른 파일이 없으면 필드 자체를 빼서 보낸다.
 */
export function dropEmptyImages(form, fileInput) {
  if (!fileInput?.files || fileInput.files.length === 0) {
    form.delete("images");
  }
}

/**
 * 네이티브 confirm() 대신 쓰는 인앱 확인창.
 *
 * 브라우저 기본 confirm()은 임베디드 웹뷰나 자동화 브라우저에서 통째로 막히는 경우가 있고
 * (여기서는 실제로 그런 환경에서 항상 false를 반환하는 걸 확인했다), 디자인도 사이트
 * 톤과 안 맞아서 자체 모달로 대체한다. 사용법은 `if (!(await confirmDialog("..."))) return;`.
 */
export function confirmDialog(message, { confirmLabel = "확인", cancelLabel = "취소", danger = true } = {}) {
  return new Promise((resolve) => {
    const overlay = document.createElement("div");
    overlay.className = "confirm-overlay";
    overlay.innerHTML = `
      <div class="confirm-box" role="alertdialog" aria-modal="true">
        <p class="confirm-message">${escapeHtml(message)}</p>
        <div class="confirm-actions">
          <button type="button" class="confirm-cancel">${escapeHtml(cancelLabel)}</button>
          <button type="button" class="${danger ? "btn-danger" : "btn-primary"}">${escapeHtml(confirmLabel)}</button>
        </div>
      </div>`;
    document.body.appendChild(overlay);

    const close = (result) => {
      document.removeEventListener("keydown", onKeydown);
      overlay.remove();
      resolve(result);
    };
    const onKeydown = (e) => {
      if (e.key === "Escape") close(false);
    };

    document.addEventListener("keydown", onKeydown);
    overlay.addEventListener("click", (e) => {
      if (e.target === overlay) close(false);
    });
    overlay.querySelector(".confirm-cancel").addEventListener("click", () => close(false));
    overlay.querySelector(".confirm-actions button:last-child").addEventListener("click", () => close(true));
  });
}

export function showToast(message) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => {
    toast.hidden = true;
  }, 2800);
}

export function escapeHtml(str) {
  if (str === null || str === undefined) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

export function formatMoney(amount) {
  if (amount === null || amount === undefined) return "-";
  return `${Number(amount).toLocaleString("ko-KR")}원`;
}

/**
 * 타임세일/특가 할인이 적용된 가격 블록을 만든다.
 * discountApplied가 false면 배지 없이 현재가만 보여준다.
 */
export function priceBlock(pricePerNight, normalPricePerNight, discountApplied, { size = "1rem" } = {}) {
  if (!discountApplied || !normalPricePerNight || normalPricePerNight <= pricePerNight) {
    return `<span class="price" style="font-size:${size};">${formatMoney(pricePerNight)}</span>`;
  }
  const rate = Math.round((1 - pricePerNight / normalPricePerNight) * 100);
  return `
    <span class="tag bad" style="margin-right:6px;">${rate}% 할인</span>
    <span class="muted" style="text-decoration:line-through;font-size:0.85em;">${formatMoney(normalPricePerNight)}</span>
    <span class="price" style="font-size:${size};margin-left:4px;">${formatMoney(pricePerNight)}</span>
  `;
}

export function formatDate(iso) {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

export function formatDateTime(iso) {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return String(iso);
  return d.toLocaleString("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function errorBox(err) {
  const message = err?.message ?? "요청 중 오류가 발생했습니다.";
  const code = err?.code && err.code !== String(err.status) ? ` · ${err.code}` : "";
  return `<div class="error-box">${escapeHtml(message)}<span class="tiny">${escapeHtml(code)}</span></div>`;
}

export function spinner() {
  return `<div class="spinner">불러오는 중…</div>`;
}

export function skeletonGrid(count = 8) {
  return `<div class="skel-grid">${'<div class="skel skel-card"></div>'.repeat(count)}</div>`;
}

const STATUS_LABELS = {
  ACTIVE: "예약 가능",
  SOLD_OUT: "예약 마감",
  INACTIVE: "미공개",
  PENDING_PAYMENT: "결제 대기",
  CONFIRMED: "예약 확정",
  CANCELED: "취소됨",
  EXPIRED: "기간 만료",
  COMPLETED: "이용 완료",
  READY: "결제 준비",
  PAID: "결제 완료",
  FAILED: "결제 실패",
  REFUNDED: "환불 완료",
};

/**
 * 특가·타임세일 상태 라벨.
 *
 * SCHEDULED/ACTIVE/ENDED로 값이 같지만 의미가 다르다. 특히 ACTIVE는 객실에서는
 * "예약 가능", 판매에서는 "판매 중"이라 라벨을 분리한다.
 */
const SALE_STATUS_LABELS = {
  SCHEDULED: "판매 예정",
  ACTIVE: "판매 중",
  ENDED: "종료됨",
};

/** 특가 점유 대기열(Waitlist) 상태 라벨. */
const WAITLIST_STATUS_LABELS = {
  WAIT: "대기 중",
  HOLD: "결제 가능",
  CONFIRMED: "확정",
  EXPIRED: "만료됨",
};

/**
 * @param {string} status 서버가 준 상태 값
 * @param {"default"|"sale"|"waitlist"} kind 상태 값의 의미를 결정하는 문맥
 */
export function statusTag(status, kind = "default") {
  const labels = kind === "sale" ? SALE_STATUS_LABELS : kind === "waitlist" ? WAITLIST_STATUS_LABELS : STATUS_LABELS;
  const ok = ["ACTIVE", "CONFIRMED", "PAID", "HOLD"];
  const bad = ["SOLD_OUT", "CANCELED", "FAILED", "INACTIVE", "EXPIRED", "ENDED"];
  const wait = ["PENDING_PAYMENT", "READY", "SCHEDULED", "WAIT"];
  const cls = ok.includes(status) ? "ok" : bad.includes(status) ? "bad" : wait.includes(status) ? "wait" : "";
  return `<span class="tag ${cls}">${escapeHtml(labels[status] ?? status)}</span>`;
}

export function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function addDaysIso(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

/** datetime-local 입력에 넣을 로컬 시각 문자열(yyyy-MM-ddTHH:mm)을 만든다. */
export function localDateTimeInput(offsetHours = 0) {
  const d = new Date();
  d.setHours(d.getHours() + offsetHours, 0, 0, 0);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * datetime-local 값은 초가 없어서 `yyyy-MM-ddTHH:mm`으로 들어온다.
 * 서버의 LocalDateTime 파싱에 맞춰 초를 붙여준다.
 */
export function withSeconds(value) {
  if (!value) return value;
  return value.length === 16 ? `${value}:00` : value;
}

/**
 * 등록된 이미지가 없는 숙소·객실이 많아서, 깨진 이미지 아이콘 대신
 * id로 결정되는 그라데이션 자리표시자를 보여준다. 같은 숙소는 항상 같은 색이다.
 */
const GRADIENTS = [
  "linear-gradient(135deg,#667eea,#764ba2)",
  "linear-gradient(135deg,#4c6ef5,#22b8cf)",
  "linear-gradient(135deg,#f783ac,#e64980)",
  "linear-gradient(135deg,#20c997,#0ca678)",
  "linear-gradient(135deg,#ff922b,#f76707)",
  "linear-gradient(135deg,#845ef7,#5f3dc4)",
  "linear-gradient(135deg,#339af0,#1c7ed6)",
  "linear-gradient(135deg,#ff6b6b,#f03e3e)",
];

export function gradientFor(id) {
  return GRADIENTS[Math.abs(Number(id) || 0) % GRADIENTS.length];
}

/** 이미지 URL이 있으면 <img>, 없으면 그라데이션 + 이니셜 자리표시자를 반환한다. */
export function mediaBlock(url, id, name, className = "stay-media") {
  if (url) {
    return `<div class="${className}"><img src="${escapeHtml(url)}" alt="${escapeHtml(name)}" loading="lazy" /></div>`;
  }
  const initial = escapeHtml((name || "?").trim().charAt(0));
  return `<div class="${className} placeholder" style="background:${gradientFor(id)}">${initial}</div>`;
}
