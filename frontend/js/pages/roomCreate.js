import { AccommodationApi, AdminApi, isLoggedIn, getCurrentRole } from "../api.js";
import { mount, escapeHtml, errorBox, spinner, showToast, dropEmptyImages } from "../ui.js";
import { navigate } from "../router.js";

/**
 * 관리자 전용 객실 등록 화면. accommodationId는 URL(params.accId)에서 그대로 받으므로
 * 입력창이 없다 — 숙소 상세의 "객실 등록" 버튼을 눌러야만 이 화면에 온다.
 */
export async function renderRoomCreate(params) {
  const accommodationId = params.accId;

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

  mount(`<div class="container page" id="room-create-body">${spinner()}</div>`);
  const body = document.getElementById("room-create-body");

  // 이름을 보여주기 위한 조회일 뿐이라 실패해도 등록 자체는 막지 않는다.
  let accommodationName = `숙소 #${accommodationId}`;
  try {
    const detail = await AccommodationApi.detail(accommodationId);
    accommodationName = detail.name;
  } catch (e) {
    // no-op — 위 기본값을 그대로 쓴다.
  }

  body.innerHTML = `
    <a href="#/accommodations/${accommodationId}" class="backlink">← ${escapeHtml(accommodationName)}</a>
    <h1>객실 등록</h1>
    <p class="lead" style="margin-bottom:24px;">${escapeHtml(accommodationName)}에 새 객실을 등록합니다.</p>

    <div class="card" style="max-width:560px;">
      <form id="room-create-form">
        <label>객실 번호
          <input type="text" name="roomNumber" required maxlength="30" />
        </label>
        <label>객실명
          <input type="text" name="name" required maxlength="100" />
        </label>
        <label>설명
          <textarea name="description" rows="3"></textarea>
        </label>
        <div class="row">
          <label>1박 가격
            <input type="number" name="pricePerNight" min="0" required />
          </label>
          <label>기준 인원
            <input type="number" name="standardCapacity" min="1" required />
          </label>
          <label>최대 인원
            <input type="number" name="maxCapacity" min="1" required />
          </label>
        </div>
        <label>이미지
          <input type="file" name="images" accept="image/jpeg,image/png,image/webp" multiple />
          <span class="hint">최대 10장, 파일당 10MB, jpg/png/webp · 첫 장이 대표 이미지</span>
        </label>
        <div id="room-create-error"></div>
        <button type="submit" class="btn-primary btn-lg">객실 등록</button>
      </form>
      <div id="room-create-result" style="margin-top:12px;"></div>
    </div>
  `;

  document.getElementById("room-create-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    dropEmptyImages(form, e.target.querySelector('input[name="images"]'));
    const errorEl = document.getElementById("room-create-error");
    errorEl.innerHTML = "";
    const submitBtn = e.target.querySelector("button[type=submit]");
    submitBtn.disabled = true;
    try {
      const created = await AdminApi.createRoom(accommodationId, form);
      // 등록 직후 객실은 INACTIVE이고, 공개 조회 API는 INACTIVE 객실을 404로 막는다.
      // 그래서 객실 화면으로 보내기 전에 여기서 바로 공개할 수 있게 한다.
      document.getElementById("room-create-result").innerHTML = `
        <div class="info-box">
          객실이 등록되었습니다. 등록 직후 상태는 <b>INACTIVE</b>여서 아직 사용자 화면에
          보이지 않습니다.
          <div style="margin-top:12px;">
            <button id="publish-now" class="btn-primary">지금 공개하기</button>
          </div>
          <div id="publish-result" style="margin-top:10px;"></div>
        </div>
      `;
      showToast("객실이 등록되었습니다.");

      document.getElementById("publish-now").addEventListener("click", async (ev) => {
        ev.target.disabled = true;
        ev.target.textContent = "공개 중…";
        try {
          await AdminApi.updateRoomStatus(accommodationId, created.roomId, "ACTIVE");
          showToast("객실을 공개했습니다.");
          navigate(`/accommodations/${accommodationId}/rooms/${created.roomId}`);
        } catch (publishError) {
          document.getElementById("publish-result").innerHTML = errorBox(publishError);
          ev.target.disabled = false;
          ev.target.textContent = "지금 공개하기";
        }
      });
    } catch (err) {
      errorEl.innerHTML = errorBox(err);
    } finally {
      submitBtn.disabled = false;
    }
  });
}
