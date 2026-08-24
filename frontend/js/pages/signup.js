import { AuthApi } from "../api.js";
import { mount, errorBox, showToast } from "../ui.js";
import { navigate } from "../router.js";

export function renderSignup() {
  mount(`
    <div class="auth-wrap">
      <div class="auth-card">
        <h1>회원가입</h1>
        <p class="lead" style="margin-bottom:26px;">30초면 충분해요.</p>

        <form id="signup-form">
          <label>이름
            <input type="text" name="name" required maxlength="50" placeholder="홍길동" />
          </label>
          <label>이메일
            <input type="email" name="email" required placeholder="you@example.com" autocomplete="email" />
          </label>
          <label>비밀번호
            <input type="password" name="password" required minlength="8" maxlength="64" autocomplete="new-password" />
            <span class="hint">영문과 숫자를 포함해 8자 이상 64자 이하</span>
          </label>
          <label>비밀번호 확인
            <input type="password" name="passwordConfirm" required minlength="8" maxlength="64" autocomplete="new-password" />
          </label>
          <div id="signup-error"></div>
          <button type="submit" class="btn-primary btn-block btn-lg">가입하기</button>
        </form>

        <div class="auth-foot">
          이미 계정이 있으신가요? <a href="#/login">로그인</a>
        </div>
      </div>
    </div>
  `);

  document.getElementById("signup-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const form = new FormData(e.target);
    const errorEl = document.getElementById("signup-error");
    const btn = e.target.querySelector("button[type=submit]");
    errorEl.innerHTML = "";

    if (form.get("password") !== form.get("passwordConfirm")) {
      errorEl.innerHTML = errorBox({ message: "비밀번호가 일치하지 않습니다." });
      return;
    }

    btn.disabled = true;
    btn.textContent = "가입 중…";
    try {
      await AuthApi.signup({
        name: form.get("name"),
        email: form.get("email"),
        password: form.get("password"),
      });
      showToast("회원가입이 완료되었습니다. 로그인해 주세요.");
      navigate("/login");
    } catch (err) {
      errorEl.innerHTML = errorBox(err);
      btn.disabled = false;
      btn.textContent = "가입하기";
    }
  });
}
