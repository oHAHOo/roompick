import { AuthApi, setTokens } from "../api.js";
import { mount, errorBox, showToast } from "../ui.js";
import { navigate } from "../router.js";
import { renderNav } from "../app.js";

export function renderLogin() {
  mount(`
    <div class="auth-wrap">
      <div class="auth-card">
        <h1>다시 오셨네요</h1>
        <p class="lead" style="margin-bottom:26px;">예약을 확인하려면 로그인하세요.</p>

        <form id="login-form">
          <label>이메일
            <input type="email" name="email" placeholder="you@example.com" required autocomplete="email" />
          </label>
          <label>비밀번호
            <input type="password" name="password" required autocomplete="current-password" />
          </label>
          <div id="login-error"></div>
          <button type="submit" class="btn-primary btn-block btn-lg">로그인</button>
        </form>

        <div class="auth-foot">
          아직 계정이 없으신가요? <a href="#/signup">회원가입</a>
        </div>
      </div>
    </div>
  `);

  document.getElementById("login-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const errorEl = document.getElementById("login-error");
    const btn = e.target.querySelector("button[type=submit]");
    errorEl.innerHTML = "";
    btn.disabled = true;
    btn.textContent = "로그인 중…";
    try {
      const data = await AuthApi.login({
        email: form.get("email"),
        password: form.get("password"),
      });
      setTokens(data);
      showToast("로그인되었습니다.");
      renderNav();
      navigate("/");
    } catch (err) {
      errorEl.innerHTML = errorBox(err);
      btn.disabled = false;
      btn.textContent = "로그인";
    }
  });
}
