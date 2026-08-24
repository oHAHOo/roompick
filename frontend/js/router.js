const routes = [];

/**
 * @param {string} pattern e.g. "/rooms/:id"
 * @param {(params: Record<string,string>, query: URLSearchParams) => void|Promise<void>} handler
 */
export function route(pattern, handler) {
  const paramNames = [];
  const regex = new RegExp(
    "^" +
      pattern
        .split("/")
        .map((segment) => {
          if (segment.startsWith(":")) {
            paramNames.push(segment.slice(1));
            return "([^/]+)";
          }
          return segment.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        })
        .join("/") +
      "$"
  );
  routes.push({ regex, paramNames, handler });
}

function parseHash() {
  const raw = location.hash.slice(1) || "/";
  const [pathPart, queryPart] = raw.split("?");
  return { path: pathPart || "/", query: new URLSearchParams(queryPart || "") };
}

let notFoundHandler = () => {
  document.getElementById("app").innerHTML = `<div class="empty">페이지를 찾을 수 없습니다.</div>`;
};

export function setNotFound(handler) {
  notFoundHandler = handler;
}

export async function resolve() {
  const { path, query } = parseHash();
  for (const r of routes) {
    const match = path.match(r.regex);
    if (match) {
      const params = {};
      r.paramNames.forEach((name, i) => (params[name] = decodeURIComponent(match[i + 1])));
      window.scrollTo(0, 0);
      await r.handler(params, query);
      return;
    }
  }
  notFoundHandler();
}

export function startRouter() {
  window.addEventListener("hashchange", resolve);
  window.addEventListener("DOMContentLoaded", resolve);
  if (document.readyState !== "loading") resolve();
}

export function navigate(path) {
  location.hash = `#${path}`;
}
