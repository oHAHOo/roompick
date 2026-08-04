# GitHub Actions AI 에이전트 워크플로우 설계 문서

- 관련 Issue: #81 (W2 고도화 — GitHub Actions에 AI 에이전트 구현), #98 (issue에 agent 배정 시 해결 방향 코멘트 자동 등록)
- 관련 파일: `.github/workflows/claude.yml`, `.github/workflows/claude-triage.yml` (제안, 아래 10장 참고)
- 작성일: 2026-07-30 (10장 추가: 2026-08-04)

이 문서는 `claude.yml`을 왜 지금 구조로 설계했는지, 각 결정의 배경과 트레이드오프를 정리한다. 워크플로우 파일을 수정하기 전에 먼저 이 문서를 읽고, 구조를 바꾸면 이 문서도 함께 갱신한다.

---

## 1. 목표와 범위

이슈 #81의 원래 목표는 PR 리뷰 코멘트 대응, CI 실패 자동 진단·수정, 이슈 트리아지까지 포함했다. 이번 구현은 그중 **PR/이슈 코멘트에서 `@claude` 멘션에 반응하는 부분만** 다룬다.

CI 실패 자동 대응은 별도로 검토했으나 제외했다. 이유는 아래 3번 항목 참고.

---

## 2. 트리거 설계

### 2.1 트리거 이벤트 설명

| 이벤트 | 발생 시점 | 이 워크플로우에서의 용도 |
| --- | --- | --- |
| `issue_comment` (`created`) | 이슈 또는 PR에 새 코멘트가 달렸을 때 | 이슈/PR 코멘트에서 `@claude` 멘션 감지 |
| `pull_request_review_comment` (`created`) | PR의 특정 코드 라인에 리뷰 코멘트가 달렸을 때 | 코드 리뷰 코멘트에서 `@claude` 멘션 감지 |
| `pull_request_review` (`submitted`) | PR 리뷰(승인/변경 요청 등)가 제출됐을 때 | 리뷰 본문에서 `@claude` 멘션 감지 |
| `issues` (`opened`, `assigned`) | 이슈가 새로 생성되거나 담당자가 지정됐을 때 | 이슈 본문에서 `@claude` 멘션 감지 |

`pull_request_review_comment`와 `issue_comment`는 둘 다 "코멘트"지만 대상이 다르다. `issue_comment`는 이슈/PR 전체에 대한 일반 코멘트(GitHub 내부적으로 PR도 이슈로 취급), `pull_request_review_comment`는 코드 diff의 특정 줄에 달리는 리뷰 코멘트다. 두 경로 모두에서 `@claude`를 호출할 수 있도록 둘 다 등록했다.

`issues: types: [opened, assigned]`를 넣은 이유는 이슈 트리아지 시나리오(이슈가 새로 열리거나 누군가에게 할당됐을 때 에이전트가 초기 분석을 도울 수 있도록)를 염두에 둔 것이다. 다만 현재는 `@claude`가 본문에 포함된 경우에만 반응하므로, 실제로는 이슈 생성 시점에 작성자가 직접 `@claude`를 함께 적어야 동작한다.

### 2.2 GitHub App 설치와 기본 브랜치 요구사항

`issue_comment`, `pull_request_review_comment`, `pull_request_review`, `issues` 이벤트는 **저장소의 기본 브랜치(`develop`)에 있는 워크플로우 파일**을 기준으로 실행 여부가 결정된다. PR 브랜치에만 워크플로우 파일이 있고 아직 기본 브랜치에 병합되지 않았다면, 그 PR에 `@claude` 코멘트를 남겨도 워크플로우 자체가 인식되지 않는다. (실제로 PR #87 머지 전 테스트 시 이 문제로 트리거가 발생하지 않는 것을 확인했다.)

반면 `pull_request` 이벤트(이 워크플로우에서는 사용하지 않음)는 예외적으로 PR의 head 브랜치 워크플로우 파일을 사용한다. 병합 전 신규 워크플로우를 테스트해야 한다면 이 차이를 감안해야 한다.

### 2.3 왜 `@claude` 멘션 방식인가

```yaml
on:
  issue_comment:
    types: [created]
  pull_request_review_comment:
    types: [created]
  pull_request_review:
    types: [submitted]
  issues:
    types: [opened, assigned]
```

4가지 이벤트 모두 "사람이 명시적으로 부를 때만" 실행되도록 `if:` 조건에서 `@claude` 문자열 포함 여부를 검사한다.

- 라벨 부착 방식(예: `ai-review` 라벨) 대신 멘션 방식을 선택한 이유: 코멘트 하나로 트리거와 요청 내용을 동시에 전달할 수 있어 라벨 관리 부담이 없다.
- 모든 코멘트에 무조건 반응하는 방식은 채택하지 않았다. 비용이 예측 불가능해지고 스팸성 반응이 될 수 있다.

### 2.4 CI 실패 자동 대응을 제외한 이유

`workflow_run` 이벤트로 CI 실패마다 자동 반응하는 워크플로우를 별도 설계했으나 다음 이유로 보류했다.

- CI 실패는 사람이 트리거를 여는 게 아니라 **실패할 때마다 무조건 실행**되므로, `@claude` 멘션과 비용 성격이 다르다 (실패 빈도만큼 실행 횟수가 그대로 늘어남).
- 같은 원인으로 반복 실패하면 중복 코멘트가 쌓이는 문제가 있고, 이를 막으려면 별도의 중복 판단 로직(그 자체로 추가 복잡도/비용)이 필요하다.
- 비용을 예측 가능한 범위로 유지하는 것을 우선시해, 이번 스코프에서는 제외하고 필요 시 별도 이슈로 분리하기로 했다 (이슈 #81 본문에도 명시).

---

## 3. 무한 루프·중복 트리거 방지

```yaml
if: |
  (github.event_name == 'issue_comment' && github.event.comment.user.type != 'Bot' && contains(github.event.comment.body, '@claude')) ||
  ...
```

### 3.1 문제 상황

에이전트가 답변 코멘트를 남기면 그 코멘트도 `issue_comment` 이벤트를 다시 발생시킨다. 답변 안에 `@claude` 문자열이 포함되면(예: 사용 예시를 안내하는 경우) 워크플로우가 재귀적으로 계속 트리거될 수 있다.

### 3.2 해결 방식과 변경 이력

- 최초 구현: `github.event.comment.user.login != 'github-actions[bot]'`
  - `GITHUB_TOKEN`으로 남긴 코멘트의 작성자 로그인만 걸러내는 방식.
- 리뷰 피드백으로 발견된 문제: `claude-code-action`이 실제로 응답을 남기는 계정은 `github-actions[bot]`이 아니라 **`claude[bot]`**이다. 즉 최초 구현은 실제 응답 봇을 걸러내지 못했다.
- 최종 수정: `github.event.comment.user.type != 'Bot'`
  - 로그인 이름 하나를 비교하는 대신, GitHub이 제공하는 계정 타입(`User` vs `Bot`) 자체로 판단한다. 어떤 이름의 봇 계정이든 (현재의 `claude[bot]`이든 나중에 바뀌는 계정이든) 동일한 조건으로 걸러진다.
- 실제 테스트로 검증됨: 이슈 #81에 `@claude` 테스트 코멘트를 남긴 뒤, 에이전트의 응답 코멘트(작성자 `claude`, `Bot` 타입)에 대해서는 워크플로우가 `skipped` 상태로 표시되고 실행되지 않는 것을 Actions 탭에서 확인했다.

---

## 4. 비용·실행 시간 상한

```yaml
runs-on: ubuntu-latest
timeout-minutes: 10
```

GitHub Actions job의 기본 타임아웃은 360분(6시간)이다. 에이전트 응답이 비정상적으로 길어지거나 멈추는 경우 비용과 실행 시간이 과도하게 소모될 수 있어 10분으로 제한했다. 일반적인 코멘트 응답/간단한 작업은 이 시간 내에 끝난다.

같은 PR/이슈에 대한 트리거 빈도 자체를 제한하는 로직(예: N분 내 재호출 금지)은 추가하지 않았다. `@claude` 멘션은 사람이 직접 입력하는 행위라 남용 가능성이 낮다고 판단했다. 필요성이 드러나면 추가한다.

---

## 5. 인증 방식

```yaml
with:
  claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
```

- 별도 API 키를 발급받아 과금하는 대신, 기존 Claude Pro 구독 계정으로 `claude setup-token`을 실행해 OAuth 토큰을 발급받아 사용한다.
- 저장소 Secrets에 `CLAUDE_CODE_OAUTH_TOKEN`으로 등록되어 있다.
- 추가로 **Claude Code GitHub App** (https://github.com/apps/claude) 설치가 필요하다. OAuth 토큰만으로는 부족하고, 앱이 저장소에 설치되어 있어야 OIDC 토큰을 앱 토큰으로 교환하는 인증 과정이 성립한다. (설치 전 테스트 시 `Claude Code is not installed on this repository` 401 에러로 확인됨)
- 참고로 이 GitHub App은 보안 정책상 `.github/workflows/` 디렉토리 내 파일을 에이전트가 직접 수정하지 못하도록 제한하고 있다. 즉 워크플로우 파일 자체를 에이전트가 스스로 변경할 수 없다.

---

## 6. 권한 범위

```yaml
permissions:
  contents: write
  pull-requests: write
  issues: write
  id-token: write
```

- `contents: write`, `pull-requests: write`, `issues: write`: 에이전트가 코드 커밋, PR/이슈 코멘트 작성을 하기 위해 필요.
- `id-token: write`: OIDC 토큰 발급 (GitHub App 인증 교환에 사용).
- GitHub Actions의 `permissions` 필드는 브랜치 단위로 범위를 나눌 수 없다. 즉 "main/develop 제외 나머지 브랜치에만 커밋 권한"을 워크플로우 파일 안에서 표현할 방법이 없다.
- 이 한계를 보완하는 것은 워크플로우 파일이 아니라 **저장소의 브랜치 보호 규칙**이다. main/develop에 "Require a pull request before merging"을 설정하면, 에이전트가 실수로 이 브랜치에 push를 시도해도 강제 반영되지 않는다.
- **현재 상태: 브랜치 보호 규칙 미설정 (블로커).** 저장소 관리자 권한이 필요해 이 저장소에서는 아직 설정하지 못했다. 설정 방법은 별도 안내 참고, 완료 전까지는 워크플로우 레벨 방어(`if:` 조건, 봇 필터링)에만 의존하는 상태임을 인지하고 있어야 한다.

---

## 7. 외부 Action 버전 고정

```yaml
uses: actions/checkout@11d5960a326750d5838078e36cf38b85af677262 # v4
uses: anthropics/claude-code-action@be7b93b1907a4abad570368f3c74b6fe3807510b # v1
```

- 최초 구현은 `@v4`, `@v1` 같은 가변 태그를 사용했다.
- 코드 리뷰에서 지적된 문제: 이 워크플로우는 OAuth 토큰(Secret)과 `contents/pull-requests/issues: write` 권한을 함께 사용하므로, 가변 태그가 가리키는 코드가 향후 변경될 경우 공급망 공격에 노출될 위험이 있다.
- 해결: 두 Action 모두 태그가 가리키는 실제 commit SHA를 GitHub API로 확인한 뒤, 그 SHA로 고정하고 태그는 주석으로만 남겼다. 버전을 올릴 때는 새 태그의 SHA를 다시 확인해서 갱신해야 한다.

---

## 8. 응답 포맷

별도의 커스텀 헤더(`prompt`나 템플릿)를 추가하지 않았다. `claude-code-action`이 기본적으로 아래와 같은 포맷을 자동 생성하기 때문에 추가 설정 없이도 이슈 #81의 요구사항("팀원이 언제 에이전트가 개입했는지 PR에서 구분 가능함")을 충족한다.

```
**Claude finished @{user}'s task in {N}s** —— [View job](링크)
```

- 작성자 계정이 `claude` + `Bot` 타입으로 GitHub UI에서 명확히 구분된다.
- 실행 시간과 실행 로그 링크가 코멘트에 자동 포함된다.

---

## 9. 남은 작업

- [ ] main/develop 브랜치 보호 규칙 설정 (저장소 관리자 조치 필요, 완료조건 "의도치 않은 브랜치 직접 푸시 방지"의 마지막 방어선)
- CI 실패 자동 대응은 이번 스코프에서 제외 — 필요 시 별도 이슈로 재검토 (비용 통제 방안이 먼저 정해져야 함)

---

## 10. `claude-triage.yml` — 이슈 트리아지 계획 코멘트 (이슈 #98)

### 10.1 목표와 범위

`claude.yml`은 이슈 본문에 `@claude`가 포함돼 있어야만 동작하고, 담당자(assignee)를 `claude`로 지정하는 것만으로는 아무 반응이 없다. 이슈 #98의 원래 요구사항은 담당자 지정 시점에 코드를 바로 수정하지 않고 해결 방향 "계획"만 이슈 댓글로 자동 등록하는 것이었다. 실제 코드 구현(브랜치/커밋/PR 생성)은 이 워크플로우의 범위가 아니며 별도 이슈로 분리한다.

`claude.yml`을 확장하지 않고 별도 워크플로우 파일(`claude-triage.yml`)로 분리한 이유: 트리거 조건과 허용 동작 범위(코멘트만 vs 코드 수정까지)가 서로 다르고, 하나의 `if:` 조건에 섞으면 실수로 트리거만으로 코드 수정 권한을 가진 액션이 실행될 위험이 있다.

### 10.2 트리거: 담당자 지정(assignee) → 라벨(label)로 변경

**당초 설계**는 이슈 #98 본문 그대로 담당자 지정(`assignee_trigger`)을 트리거로 사용하는 것이었다. 그러나 실제로 이 방식은 다음 두 조건이 전부 충족돼야 동작한다.

1. `claude`가 이 저장소의 담당자 지정 가능 목록(`GET /repos/{owner}/{repo}/assignees`)에 있어야 함
2. GitHub 이슈 UI에서 봇 계정을 담당자로 지정하려면, GitHub 자체의 **Copilot → Coding agent → Partner Agents**에서 Claude를 활성화해야 함 (이는 Anthropic의 `claude-code-action`과는 무관한 **GitHub 네이티브 기능**이다)

이 저장소가 속한 계정에서 Settings → Copilot → Cloud agent 화면을 직접 확인한 결과, "Copilot Pro/Pro+/Business/Enterprise 라이선스가 없다"는 경고와 함께 Partner Agents 섹션 자체가 노출되지 않았다. 즉 유료 Copilot 라이선스가 없는 현재 상태로는 담당자 지정 방식이 원천적으로 막혀 있다.

**최종 결정**: 담당자 지정 대신 **라벨(label) 트리거**로 변경한다. 라이선스나 조직 관리자 설정 없이 `claude-code-action`의 `label_trigger` 입력만으로 즉시 동작 가능하다.

```yaml
on:
  issues:
    types: [labeled]

jobs:
  triage-plan:
    if: github.event.label.name == 'claude-triage'
```

```yaml
with:
  label_trigger: "claude-triage"
```

사용법: 이슈에 `claude-triage` 라벨을 붙이면 트리거된다. 담당자 지정과 달리 라벨은 저장소에 쓰기 권한이 있는 사람이면 누구나 관리자 설정 없이 바로 만들고 붙일 수 있다. (`claude-triage` 라벨이 저장소에 미리 존재해야 하며, 없으면 사람이 먼저 만들어야 한다.)

> 이슈 #98 본문의 완료조건("agent 배정 시")과 문자 그대로는 다르지만, "담당자(assignee) 개념을 GitHub 봇 계정 UI로 흉내낼 방법이 이 저장소 환경(무료 티어)에서 없다"는 것을 확인한 뒤 내린 대체 결정이다. 이슈 코멘트에도 이 사유를 남겨 완료조건 문구를 라벨 기준으로 조정할지 논의가 필요하다.

### 10.3 코드 수정 방지 — `prompt`만으로는 부족함

당초 이슈 작업 항목에는 "`prompt` 입력이 동작 범위를 실제로 강제하는지 확인"이 포함돼 있었다. 확인 결과: **`prompt`만으로는 코드 수정을 막을 수 없다.** `prompt`는 지시문일 뿐 도구 접근을 제한하지 않으므로, 모델이 지시를 무시하고 파일을 수정할 가능성을 코드 레벨에서 차단하지 못한다.

실제 강제 수단은 `claude_args`로 도구 자체를 비활성화하는 것이다.

```yaml
claude_args: |
  --disallowedTools Edit,Write
```

`Edit`, `Write` 도구를 비활성화하면 모델이 프롬프트를 무시하려 해도 파일을 편집/생성할 방법이 없다. 이 워크플로우의 권한도 `contents: write`가 아닌 `contents: read`로 최소화한다 (댓글 작성에는 `issues: write`만 있으면 된다).

### 10.4 남은 준비 작업

담당자 지정 방식의 라이선스 블로커는 라벨 방식으로 전환하면서 해소됐다. 다만 실제 배치 전에 아래는 사람이 직접 해야 한다.

- [ ] 저장소에 `claude-triage` 라벨 생성 (Issues → Labels → New label)
- [x] `claude-triage.yml`을 `.github/workflows/claude-triage.yml`로 커밋 완료
- [ ] 테스트 이슈에 `claude-triage` 라벨을 붙여 워크플로우가 트리거되고, 계획 댓글만 남고 코드 수정/PR이 발생하지 않는지 검증
- [ ] 이슈 #98 완료조건 문구("agent 배정 시")를 라벨 트리거 기준으로 조정할지 이슈에 코멘트로 논의

### 10.5 현재 상태

- `claude-triage.yml`은 [`.github/workflows/claude-triage.yml`](../.github/workflows/claude-triage.yml)에 배치 완료됐다 (Claude Code GitHub App은 이 디렉토리를 직접 수정할 수 없어 사람이 직접 커밋함).
- 라벨 트리거 방식은 조직/라이선스 설정에 의존하지 않으므로, `claude-triage` 라벨만 저장소에 만들면 바로 동작 가능한 상태다.
