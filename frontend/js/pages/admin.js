import { AdminApi, getCurrentRole, isLoggedIn } from "../api.js";
import {
  mount, errorBox, showToast, dropEmptyImages,
} from "../ui.js";
import { navigate } from "../router.js";

let state = {
  lastAccommodationId: "",
};

export function renderAdmin() {
  if (!isLoggedIn()) {
    navigate("/login");
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

  mount(`
    <div class="container page">
      <h1>관리자</h1>
      <p class="lead" style="margin-bottom:28px;">새 숙소를 등록합니다.</p>
      <p class="tiny" style="margin-top:-20px;margin-bottom:20px;">
        객실 등록, 특가·타임세일 등록은 각 숙소·객실 상세 페이지의 관리자 패널에서 합니다
        — <a href="#/">숙소 목록으로</a>
      </p>
      <div id="admin-body" style="max-width:620px;"></div>
    </div>
  `);

  renderAccommodationForm(document.getElementById("admin-body"));
}

function renderAccommodationForm(body) {
  body.innerHTML = `
    <div class="card">
      <form id="acc-form">
        <label>숙소명
          <input type="text" name="name" required maxlength="100" />
        </label>
        <label>주소
          <input type="text" name="address" required maxlength="255" />
        </label>
        <label>설명
          <textarea name="description" rows="3"></textarea>
        </label>
        <div class="row">
          <label>위도 (latitude)
            <input type="number" name="latitude" step="0.000001" min="-90" max="90" required />
          </label>
          <label>경도 (longitude)
            <input type="number" name="longitude" step="0.000001" min="-180" max="180" required />
          </label>
        </div>
        <div class="row">
          <label>체크인 시간
            <input type="time" name="checkInTime" step="1" required />
          </label>
          <label>체크아웃 시간
            <input type="time" name="checkOutTime" step="1" required />
          </label>
        </div>
        <label>이미지
          <input type="file" name="images" accept="image/jpeg,image/png,image/webp" multiple />
          <span class="hint">최대 10장, 파일당 10MB, jpg/png/webp · 첫 장이 대표 이미지</span>
        </label>
        <div id="acc-error"></div>
        <button type="submit" class="btn-primary btn-lg">숙소 등록</button>
      </form>
      <div id="acc-result" style="margin-top:12px;"></div>
    </div>
  `;

  document.getElementById("acc-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    // HTML time input omits seconds when step isn't honored by the browser; normalize to HH:mm:ss.
    for (const key of ["checkInTime", "checkOutTime"]) {
      const value = form.get(key);
      if (value && value.length === 5) form.set(key, `${value}:00`);
    }
    dropEmptyImages(form, e.target.querySelector('input[name="images"]'));
    const errorEl = document.getElementById("acc-error");
    errorEl.innerHTML = "";
    const submitBtn = e.target.querySelector("button[type=submit]");
    submitBtn.disabled = true;
    try {
      const created = await AdminApi.createAccommodation(form);
      state.lastAccommodationId = String(created.accommodationId);
      document.getElementById("acc-result").innerHTML = `
        <div class="info-box">
          숙소가 등록되었습니다. (accommodationId = ${created.accommodationId})<br />
          <a href="#/accommodations/${created.accommodationId}">숙소 페이지로 이동해서 객실 등록하기 →</a>
        </div>
      `;
      showToast("숙소가 등록되었습니다.");
      e.target.reset();
    } catch (err) {
      errorEl.innerHTML = errorBox(err);
    } finally {
      submitBtn.disabled = false;
    }
  });
}
