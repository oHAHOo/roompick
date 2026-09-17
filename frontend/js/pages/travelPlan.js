import { PlaceApi, TravelPlanApi } from "../api.js";
import {
  mount,
  escapeHtml,
  errorBox,
  spinner,
  mediaBlock,
  todayIso,
  addDaysIso,
} from "../ui.js";

const state = {
  placeQuery: "",
  placeResults: [],
  selectedPlace: null,
  plan: null,
  generating: false,
};

export async function renderTravelPlan() {
  mount(`
    <section class="hero">
      <div class="hero-inner">
        <span class="hero-eyebrow">🧭 AI 여행 계획</span>
        <h1>날짜와 장소만 알려주세요</h1>
        <p>일정을 짜고, 등록된 숙소 중에서 그 일정에 맞는 곳을 추천해 드려요.</p>
        <form id="place-form" class="searchbar">
          <input
            type="text"
            id="place-query"
            placeholder="어디로 떠나시나요?  예) 강남역, 해운대"
            value="${escapeHtml(state.placeQuery)}"
            autocomplete="off"
          />
          <button type="submit" class="btn-primary">장소 검색</button>
        </form>
        <div id="place-results" style="max-width:560px;margin-top:14px;"></div>
      </div>
    </section>

    <div class="container page">
      <section class="section" id="plan-form-section" hidden></section>
      <section class="section" id="plan-result-section" hidden></section>
    </div>
  `);

  bindPlaceSearch();

  if (state.placeResults.length) renderPlaceResults();
  if (state.selectedPlace) renderPlanForm();
  if (state.plan) renderPlan();
}

function bindPlaceSearch() {
  document.getElementById("place-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    state.placeQuery = document.getElementById("place-query").value.trim();
    if (!state.placeQuery) return;

    const results = document.getElementById("place-results");
    results.innerHTML = `<div class="muted" style="padding:8px 4px;">검색 중…</div>`;
    try {
      state.placeResults = await PlaceApi.search(state.placeQuery, 5);
      renderPlaceResults();
    } catch (err) {
      results.innerHTML = errorBox(err);
    }
  });
}

function renderPlaceResults() {
  const results = document.getElementById("place-results");
  if (!results) return;

  if (state.placeResults.length === 0) {
    results.innerHTML = `<div class="muted" style="padding:8px 4px;">검색 결과가 없습니다.</div>`;
    return;
  }

  results.innerHTML = state.placeResults
    .map(
      (p, i) => `
      <button class="place-item pick-place" data-i="${i}" style="width:100%;text-align:left;">
        <span class="place-pin">📍</span>
        <span style="flex:1;min-width:0;">
          <span style="display:block;font-weight:600;">${escapeHtml(p.name)}</span>
          <span class="tiny">${escapeHtml(p.roadAddress || p.address)}</span>
        </span>
        <span class="tiny">이 장소로 계획 →</span>
      </button>`
    )
    .join("");

  results.querySelectorAll(".pick-place").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.selectedPlace = state.placeResults[Number(btn.dataset.i)];
      state.plan = null;
      document.getElementById("plan-result-section").hidden = true;
      renderPlanForm();
      document
        .getElementById("plan-form-section")
        .scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });
}

function renderPlanForm() {
  const section = document.getElementById("plan-form-section");
  if (!section) return;

  section.hidden = false;
  section.innerHTML = `
    <div class="section-head">
      <div>
        <h2>"${escapeHtml(state.selectedPlace.name)}" 여행 계획</h2>
        <p class="lead">숙박 날짜와 인원을 고르면 일정을 만들어 드려요.</p>
      </div>
    </div>
    <form id="plan-form" class="card" style="padding:20px;display:grid;gap:14px;max-width:520px;">
      <label>
        체크인
        <input type="date" id="check-in" value="${addDaysIso(1)}" min="${todayIso()}" required />
      </label>
      <label>
        체크아웃
        <input type="date" id="check-out" value="${addDaysIso(3)}" min="${todayIso()}" required />
      </label>
      <label>
        인원
        <input type="number" id="guest-count" value="2" min="1" required />
      </label>
      <button type="submit" class="btn btn-primary" id="plan-submit">계획 만들기</button>
      <p class="tiny">
        생성에 20초 이상 걸릴 수 있어요. 버튼을 한 번만 눌러 주세요.
      </p>
    </form>
  `;

  document.getElementById("plan-form").addEventListener("submit", submitPlan);
}

async function submitPlan(e) {
  e.preventDefault();

  /*
   * 요청마다 유료 LLM 호출이 발생하므로 응답이 오기 전 중복 제출을 막는다.
   */
  if (state.generating) return;

  const result = document.getElementById("plan-result-section");
  const submit = document.getElementById("plan-submit");

  state.generating = true;
  submit.disabled = true;
  submit.textContent = "계획을 만들고 있어요…";

  result.hidden = false;
  result.innerHTML = `<div style="padding:24px 0;">${spinner()}</div>`;

  try {
    state.plan = await TravelPlanApi.create({
      latitude: state.selectedPlace.latitude,
      longitude: state.selectedPlace.longitude,
      checkInDate: document.getElementById("check-in").value,
      checkOutDate: document.getElementById("check-out").value,
      guestCount: Number(document.getElementById("guest-count").value),
    });
    renderPlan();
  } catch (err) {
    result.innerHTML = errorBox(err);
  } finally {
    state.generating = false;
    submit.disabled = false;
    submit.textContent = "계획 만들기";
  }
}

function renderPlan() {
  const section = document.getElementById("plan-result-section");
  if (!section) return;

  const plan = state.plan;

  section.hidden = false;
  section.innerHTML = `
    <div class="section-head">
      <div>
        <h2>여행 일정</h2>
        <p class="lead">
          ${escapeHtml(plan.checkInDate)} ~ ${escapeHtml(plan.checkOutDate)} · ${plan.guestCount}명
        </p>
      </div>
    </div>
    ${itineraryHtml(plan.itinerary)}

    <div class="section-head" style="margin-top:32px;">
      <div>
        <h2>이 일정에 맞는 숙소</h2>
        <p class="lead">등록된 숙소 중에서만 추천해요.</p>
      </div>
    </div>
    ${recommendationsHtml(plan.recommendedAccommodations)}
  `;
}

function itineraryHtml(itinerary) {
  if (!itinerary?.length) {
    return `<div class="empty"><div class="empty-icon">🗓️</div>일정을 만들지 못했어요.</div>`;
  }

  return `
    <div style="display:grid;gap:14px;">
      ${itinerary
        .map(
          (day) => `
          <div class="card" style="padding:18px;">
            <div style="display:flex;align-items:center;gap:10px;margin-bottom:10px;">
              <span class="tag info">${day.day}일차</span>
              <strong>${escapeHtml(day.summary ?? "")}</strong>
            </div>
            <ul style="margin:0;padding-left:20px;display:grid;gap:6px;">
              ${(day.activities ?? [])
                .map((activity) => `<li>${escapeHtml(activity)}</li>`)
                .join("")}
            </ul>
          </div>`
        )
        .join("")}
    </div>`;
}

function recommendationsHtml(recommendations) {
  if (!recommendations?.length) {
    return `
      <div class="empty">
        <div class="empty-icon">🗺️</div>
        이 근처에는 아직 등록된 숙소가 없어요. 일정만 참고해 주세요.
      </div>`;
  }

  return `
    <div class="grid">
      ${recommendations
        .map(
          (r) => `
          <a class="stay-card" href="#/accommodations/${r.accommodationId}">
            ${mediaBlock(r.imageUrl, r.accommodationId, r.name)}
            <div class="stay-body">
              <div class="stay-name">${escapeHtml(r.name)}</div>
              <div class="stay-addr">${escapeHtml(r.address)}</div>
              <p class="tiny" style="margin:8px 0 0;">${escapeHtml(r.reason ?? "")}</p>
              <div class="stay-foot">
                <span class="tag info">${formatDistance(r.distanceKm)}</span>
                <span class="tiny">숙소 보기 →</span>
              </div>
            </div>
          </a>`
        )
        .join("")}
    </div>`;
}

function formatDistance(km) {
  if (typeof km !== "number") return "";
  return km < 1 ? `${Math.round(km * 1000)}m` : `${km.toFixed(1)}km`;
}
