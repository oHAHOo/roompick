import { AccommodationApi, PlaceApi, SpecialOfferApi, RoomApi, ApiError } from "../api.js";
import { mount, escapeHtml, errorBox, skeletonGrid, mediaBlock, formatMoney } from "../ui.js";

const state = {
  page: 0,
  period: "DAILY",
  placeQuery: "",
  placeResults: [],
  selectedPlace: null,
  nearby: null,
};

function stayCard(a, rank) {
  return `
    <a class="stay-card" href="#/accommodations/${a.accommodationId}">
      ${rank ? `<span class="rank-badge">${rank}위</span>` : ""}
      ${mediaBlock(a.imageUrl, a.accommodationId, a.name)}
      <div class="stay-body">
        <div class="stay-name">${escapeHtml(a.name)}</div>
        <div class="stay-addr">${escapeHtml(a.address)}</div>
        <div class="stay-foot">
          ${
            typeof a.distanceKm === "number"
              ? `<span class="tag info">${formatDistance(a.distanceKm)}</span>`
              : `<span class="tiny">자세히 보기 →</span>`
          }
        </div>
      </div>
    </a>`;
}

function formatDistance(km) {
  return km < 1 ? `${Math.round(km * 1000)}m` : `${km.toFixed(1)}km`;
}

function offerCard(o) {
  return `
    <a class="stay-card" href="#/special-offers/${o.specialOfferId}">
      <span class="rank-badge">특가</span>
      ${mediaBlock(null, o.roomId, o.roomName)}
      <div class="stay-body">
        <div class="stay-name">${escapeHtml(o.accommodationName)} · ${escapeHtml(o.roomName)}</div>
        <div class="stay-addr">숙박 ${escapeHtml(o.checkInDate)} ~ ${escapeHtml(o.checkOutDate)}</div>
        <div class="stay-foot">
          <span class="tag info">${formatMoney(o.price)}</span>
          <span class="tiny">참여하기 →</span>
        </div>
      </div>
    </a>`;
}

export async function renderHome() {
  mount(`
    <section class="hero">
      <div class="hero-inner">
        <span class="hero-eyebrow">✨ 지금 예약 가능한 숙소</span>
        <h1>오늘 밤, <em>딱 맞는 방</em>을 찾아보세요</h1>
        <p>지역을 검색하면 주변 숙소를 거리순으로 보여드려요. 날짜만 고르면 바로 예약까지 이어집니다.</p>
        <form id="place-form" class="searchbar">
          <input
            type="text"
            id="place-query"
            placeholder="어디로 떠나시나요?  예) 강남역, 해운대"
            value="${escapeHtml(state.placeQuery)}"
            autocomplete="off"
          />
          <button type="submit" class="btn-primary">검색</button>
        </form>
        <div id="place-results" style="max-width:560px;margin-top:14px;"></div>
      </div>
    </section>

    <div class="container page">
      <section class="section" id="nearby-section" hidden></section>

      <section class="section" id="offers-section" hidden>
        <div class="section-head">
          <div>
            <h2>🔥 선착순 특가</h2>
            <p class="lead">지금 판매 중인 특가예요. 서두르세요!</p>
          </div>
        </div>
        <div id="offers-list">${skeletonGrid(4)}</div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>인기 숙소</h2>
            <p class="lead">조회수가 많은 순으로 모았어요.</p>
          </div>
          <div class="segmented" id="period-seg">
            <button data-period="DAILY" class="${state.period === "DAILY" ? "active" : ""}">일간</button>
            <button data-period="WEEKLY" class="${state.period === "WEEKLY" ? "active" : ""}">주간</button>
          </div>
        </div>
        <div id="popular-list">${skeletonGrid(4)}</div>
      </section>

      <section class="section">
        <div class="section-head">
          <div>
            <h2>전체 숙소</h2>
            <p class="lead">등록된 모든 숙소를 둘러보세요.</p>
          </div>
        </div>
        <div id="all-list">${skeletonGrid(8)}</div>
      </section>
    </div>
  `);

  bindSearch();
  bindPeriod();
  loadOffers();
  loadPopular();
  loadAll();

  if (state.selectedPlace) renderNearby();
  if (state.placeResults.length) renderPlaceResults();
}

function bindSearch() {
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
        <span class="tiny">주변 보기 →</span>
      </button>`
    )
    .join("");

  results.querySelectorAll(".pick-place").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.selectedPlace = state.placeResults[Number(btn.dataset.i)];
      renderNearby();
      document.getElementById("nearby-section").scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });
}

async function renderNearby() {
  const section = document.getElementById("nearby-section");
  if (!section) return;

  section.hidden = false;
  section.innerHTML = `
    <div class="section-head">
      <div>
        <h2>"${escapeHtml(state.selectedPlace.name)}" 주변</h2>
        <p class="lead">반경 5km 이내 숙소를 거리순으로 보여드려요.</p>
      </div>
      <button id="clear-nearby">검색 지우기</button>
    </div>
    <div id="nearby-list">${skeletonGrid(4)}</div>
  `;

  document.getElementById("clear-nearby").addEventListener("click", () => {
    state.selectedPlace = null;
    state.placeResults = [];
    state.placeQuery = "";
    renderHome();
  });

  const list = document.getElementById("nearby-list");
  try {
    const data = await AccommodationApi.nearby({
      latitude: state.selectedPlace.latitude,
      longitude: state.selectedPlace.longitude,
      radiusKm: 5,
      limit: 20,
    });
    const items = Array.isArray(data) ? data : data.content ?? [];
    list.innerHTML = items.length
      ? `<div class="grid">${items.map((a) => stayCard(a)).join("")}</div>`
      : `<div class="empty"><div class="empty-icon">🗺️</div>이 근처에는 아직 등록된 숙소가 없어요.</div>`;
  } catch (err) {
    list.innerHTML = errorBox(err);
  }
}

function bindPeriod() {
  document.querySelectorAll("#period-seg button").forEach((btn) => {
    btn.addEventListener("click", () => {
      state.period = btn.dataset.period;
      document.querySelectorAll("#period-seg button").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      loadPopular();
    });
  });
}

/**
 * 판매 중인 특가가 없으면 섹션 자체를 숨긴다.
 * 목록 조회가 실패해도(예: 네트워크 오류) 홈 화면 전체를 막지 않고
 * 이 섹션만 조용히 숨긴다.
 */
async function loadOffers() {
  const section = document.getElementById("offers-section");
  const list = document.getElementById("offers-list");
  try {
    const offers = await filterOutReserved(await SpecialOfferApi.list());
    if (!offers.length) {
      section.hidden = true;
      return;
    }
    section.hidden = false;
    list.innerHTML = `<div class="grid">${offers.map((o) => offerCard(o)).join("")}</div>`;
  } catch (err) {
    section.hidden = true;
  }
}

// 객실이 예약 불가로 확정된 상태임을 뜻하는 에러 코드. 그 외 에러(네트워크 오류 등)는
// 판단할 수 없으므로 안전하게 그대로 목록에 남긴다.
const ROOM_UNBOOKABLE_CODES = new Set(["ROOM_INACTIVE", "ROOM_NOT_FOUND"]);

/**
 * 특가 대상 객실이 (누구에 의해서든) 이미 그 날짜로 예약돼 있거나, 객실 자체가
 * 더 이상 예약 불가 상태면 목록에서 뺀다. 객실 예약 가능 여부 조회는 인증 없이도
 * 되는 공개 API라 로그인 여부와 무관하게 항상 적용한다. guestCount는 available
 * 판단과 무관해 최소값 1로 고정한다.
 */
async function filterOutReserved(offers) {
  const results = await Promise.allSettled(
    offers.map((o) =>
      RoomApi.availability(o.roomId, {
        checkInDate: o.checkInDate,
        checkOutDate: o.checkOutDate,
        guestCount: 1,
      })
    )
  );
  return offers.filter((o, i) => {
    const r = results[i];
    if (r.status === "fulfilled") return r.value?.available !== false;
    return !(r.reason instanceof ApiError && ROOM_UNBOOKABLE_CODES.has(r.reason.code));
  });
}

async function loadPopular() {
  const list = document.getElementById("popular-list");
  list.innerHTML = skeletonGrid(4);
  try {
    const data = await AccommodationApi.popular(state.period, 8);
    list.innerHTML = data.length
      ? `<div class="grid">${data.map((a) => stayCard(a, a.rank)).join("")}</div>`
      : `<div class="empty"><div class="empty-icon">📊</div>아직 집계된 인기 숙소가 없어요.</div>`;
  } catch (err) {
    list.innerHTML = errorBox(err);
  }
}

async function loadAll() {
  const list = document.getElementById("all-list");
  list.innerHTML = skeletonGrid(8);
  try {
    const data = await AccommodationApi.list(state.page, 12);
    list.innerHTML = `
      ${
        data.content.length
          ? `<div class="grid">${data.content.map((a) => stayCard(a)).join("")}</div>`
          : `<div class="empty"><div class="empty-icon">🏨</div>등록된 숙소가 없습니다.</div>`
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
      loadAll();
      document.getElementById("all-list").scrollIntoView({ behavior: "smooth", block: "start" });
    });
    document.getElementById("next-page")?.addEventListener("click", () => {
      state.page += 1;
      loadAll();
      document.getElementById("all-list").scrollIntoView({ behavior: "smooth", block: "start" });
    });
  } catch (err) {
    list.innerHTML = errorBox(err);
  }
}
