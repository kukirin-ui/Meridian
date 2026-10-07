# Meridian — A-to-Z audit, enhancement roadmap and monetization plan

Scope: the shipped 1.8.6 source (`original/`), evaluated as product manager, UX designer and QA lead.
Every finding below is paired with the exact fix. Findings marked **[fixed in 2.0]** are already
implemented at the repo root (`app/`); the rest are specified so they can be built next.

---

## 0. What Meridian is (and the direction this roadmap takes)

Meridian is a **phone-native control deck for Termux power users**: a WebView shell that reads real
hardware telemetry straight from `/proc`, `/sys` and `BatteryManager`, runs shell commands inside
Termux through the `RUN_COMMAND` intent, streams their output live, and ships curated "recipes"
(Workstation / Machine / Library) that turn a phone into a dev box. No account, no server.

That positioning is good and rare. The direction kept throughout this plan:

* **Stay local-first.** No telemetry, no login. That is the selling point against every "phone
  optimizer" on Play.
* **Sell certainty, not features.** The audience is people who already type `pkg install`. They pay
  for things that save them an evening: a paywall they trust, recipes that work, a Plan that does not
  freeze the UI.
* **Make it feel like a pro tool.** Dense, calm, fast. Terminal aesthetics, not gamer RGB.

---

## 1. Phase 1 — Functional audit (critical → high → medium)

### 1.1 Critical: data loss, security, money

| # | Finding (original) | Severity | Status |
|---|---|---|---|
| C1 | `safePath()` returns `$HOME` for any invalid path, so `removePath("../x")` runs `rm -rf "$HOME"` | Critical — deletes the user's whole Termux home | **[fixed in 2.0]** |
| C2 | `TermuxResult` receiver is `android:exported="true"` with an intent-filter; any app can inject fake command output or trigger the "paid" path | Critical — spoofing | **[fixed in 2.0]** |
| C3 | Paywall = Revolut checkout rendered *inside the WebView* and a text scrape for "thank you"; any page containing those words writes `files/full.key` | Critical — bypassable in 10 s, no VAT handling, no invoice, no refund trail | **[fixed in 2.0]** |
| C4 | Gemini API key sent as `?key=` in the URL (logged by proxies/CDNs) | High | **[fixed in 2.0]** |
| C5 | API key stored in `localStorage`, which `allowBackup` copies to Google backup | High | **[fixed in 2.0]** |
| C6 | Heredoc with fixed delimiter when writing `cmd.sh` → a command containing the delimiter line escapes the heredoc | High — command injection | **[fixed in 2.0]** |
| C7 | `setAllowFileAccess(true)` + `usesCleartextTraffic="true"` for an app that loads only `file:///android_asset` | Medium | **[fixed in 2.0]** |

**C1 fix** (`app/src/main/java/com/meridian/shell/MainActivity.java`): `safePath` returns `null` on any doubt and every caller
refuses to run.

```java
private String safePath(String path) {
    if (path == null) return null;
    String value = path.trim();
    if (value.isEmpty() || value.contains("\n") || value.contains("\0") || value.contains("..")) return null;
    if (value.startsWith("/")) {
        String home = "/data/data/com.termux/files/home";
        if (!value.equals(home) && !value.startsWith(home + "/")) return null;
    }
    return value;
}
// every caller:
String q = quotedPath(path);
if (q == null) return "{\"ok\":false,\"error\":\"That path is outside the Termux home.\"}";
```

**C2 fix** (manifest): the receiver is private and the intent targets it explicitly.

```xml
<receiver android:name="com.meridian.shell.TermuxResult" android:exported="false" />
```
```java
Intent result = new Intent(this, TermuxResult.class);          // explicit, not action-based
result.setAction("com.meridian.shell.TERMUX_RESULT");
PendingIntent pi = PendingIntent.getBroadcast(this, 0, result,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
```

**C3 fix** — offline signed licence keys, checkout in the system browser (details in §5):

```java
// MainActivity.Deck.activate(key): MERIDIAN-<b64url payload>.<b64url sig>, P-256 / SHA256withECDSA
PublicKey pub = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(LICENSE_PUBKEY, DEFAULT)));
Signature v = Signature.getInstance("SHA256withECDSA"); v.initVerify(pub); v.update(payload);
if (!v.verify(sig)) return "{\"ok\":false,\"error\":\"The key did not verify.\"}";
```

**C4 fix**: `conn.setRequestProperty("x-goog-api-key", key)` and never put the key in the URL.
**C5 fix**: `res/xml/backup_rules.xml` + `data_extraction_rules.xml` exclude `app_webview/` and
`files/first.run`. **C6 fix**: random heredoc tag per job
(`"__MERIDIAN_" + Long.toHexString(new SecureRandom().nextLong()) + "__"`), and the tag is rejected if
the command happens to contain it. **C7 fix**: `setAllowFileAccess(false)`,
`setAllowContentAccess(false)`, `usesCleartextTraffic="false"`.

### 1.2 High: things that freeze, hang, or silently fail

| # | Finding | Status |
|---|---|---|
| H1 | `MeridianPhone.run()` and `askNet()` are **synchronous** `@JavascriptInterface` calls. `run` blocks the JS thread for up to 20 s (startup `uname` probe), `askNet` for up to 60 s while the model answers. The dashboard stops repainting; Android may show ANR. | **[fixed in 2.0]** — `askNetStart(ticket,…)` → `window.onAskDone(ticket, json)`; startup probe moved to a background job |
| H2 | New file / new folder use `prompt()`, but no `WebChromeClient` is installed → `prompt()` returns `null` and the buttons do nothing | **[fixed in 2.0]** — inline name field + `goodName()` |
| H3 | `loadFiles` filters out any entry containing a space → "My Project/" never shows up | **[fixed in 2.0]** |
| H4 | No way to cancel a running job; the console just spins. `cancelJob` existed in Java but had no UI | **[fixed in 2.0]** — `Stop` button in the run bar, Ctrl+C in the dock |
| H5 | Interactive commands (`vim`, `htop`, `python`, `ssh host`) start under `script | nc`, never get a TTY, and hang forever | **[fixed in 2.0]** — `interactiveCmd()` guard → `openInTermux(cmd)` hands the command to Termux's own terminal |
| H6 | `openTermux` is called from JS but not defined in the bridge → `TypeError` on the Doctor page | **[fixed in 2.0]** |
| H7 | `powerMode()` writes `Settings.Global.low_power`; that needs `WRITE_SECURE_SETTINGS`, which a sideloaded app can never hold → every tap throws and falls through to `openPower()` while the UI says "set" | **[fixed in 2.0]** — buttons honestly open the system power screen and the note says so |
| H8 | `killBackgroundProcesses()` is a no-op since Android 8 for apps you do not own; "Free RAM" reports success | **[fixed in 2.0]** — `dropCache` left as a Termux-side action with honest copy; the fake success is gone |
| H9 | `sick()` paints **SYSTEM FAULT** whenever any console block ever contained `exit 1` (e.g. `grep` with no match), or when Termux is not bonded | **[fixed in 2.0]** — health is memory ≥ 94 % or SoC ≥ 46 °C only |
| H10 | `kill -9` on `dpkg`/`apt` during cancel and before every package job leaves the package database locked/half-written | **[fixed in 2.0]** — cancel sends TERM to the job tree, waits 1 s, KILLs only the wrapper; a live `dpkg`/`apt` is never killed and stale locks are cleared only when no such process exists, followed by `dpkg --configure -a` |
| H11 | Termux truncates oversized results: `stdout` arrives empty with `stdout_original_length` set, and the block shows nothing | **[fixed in 2.0]** — `TermuxResult` detects the truncation marker and falls back to the live-stream buffer (`pendingOut`), which is capped per block |
| H12 | `isCommand()` regex recognises ~30 binaries; `cargo build`, `./run.sh`, `pnpm i` are treated as prose | **[fixed in 2.0]** — the dock always executes; prose detection is only a hint |

**H1 pattern** (reusable for every slow bridge call):

```java
@JavascriptInterface
public void askNetStart(final String ticket, final String base, final String key, final String model, final String goal) {
    new Thread(() -> {
        final String result = askNet(base, key, model, goal);
        runOnUiThread(() -> web.evaluateJavascript(
            "window.onAskDone && window.onAskDone(" + JSONObject.quote(ticket) + "," + JSONObject.quote(result) + ")", null));
    }, "meridian-ask").start();
}
```
```js
const ticket = String(Date.now());
window.onAskDone = (t, raw) => { if (t !== ticket) return; showPlan(JSON.parse(raw)); };
MeridianPhone.askNetStart(ticket, base, key, model, goal);
```

**H5 guard** (`index.html`):

```js
function interactiveCmd(cmd) {
  const first = String(cmd || "").split("\n").map(function (l) { return l.trim(); }).filter(function (l) { return l && l.charAt(0) !== "#"; })[0] || "";
  if (/^(vim?|nvim|nano|micro|less|more|top|htop|btop|tmux|screen|zsh|fish|bash|sh)(\s|$)/.test(first)) return true;
  if (/^(python3?|node|irb|lua|php -a|sqlite3)\s*$/.test(first)) return true;
  if (/^termux-change-repo(\s|$)/.test(first) || /^proot-distro\s+login\b/.test(first)) return true;
  if (/^ssh\s/.test(first)) {
    const words = first.split(/\s+/).slice(1).filter(function (w) { return w.charAt(0) !== "-"; });
    if (words.length <= 1) return true;
  }
  return false;
}
```

### 1.3 Medium: performance, correctness, polish

| # | Finding | Status |
|---|---|---|
| M1 | Four `setInterval`s at 500/500/80 ms → **12.5 Hz** graph repaint + two 2 Hz full `innerHTML` rebuilds; each tick crosses the JS bridge and reads `/proc` | **[fixed in 2.0]** — 1000/1000/250 ms, `live()` cache window 240 ms, skip when `document.hidden`, memory bar mutates `style.width` instead of `outerHTML` |
| M2 | `props()` forks `getprop` ~25× per status call | **[fixed in 2.0]** — `SystemProperties` via reflection first, results cached for the process lifetime |
| M3 | Light theme has hard-coded dark colours in 14 selectors (gauges, terminal, dock, glyphs) → unreadable | **[fixed in 2.0]** — every colour is a token (`--ink --termbg --termfg --track --paper --bad --accent2`) |
| M4 | Console history is lost on every process death | **[fixed in 2.0]** — `m-sessions` in `localStorage`, capped at 40 blocks / 24 kB each |
| M5 | Hardware back exits the app from any sub-page | **[fixed in 2.0]** — `window.meridianBack()` pops editor → folder → home before finishing |
| M6 | Dead handlers (`rundoctor`, `data-runtweak`, `data-copycmd`, `ramclean`, `data-idea`) and a drag-and-drop promise in the Files guide that was never implemented | **[fixed in 2.0]** |
| M7 | Speed-test result lived in a DOM node that `render()` replaced → result vanished if anything repainted mid-test | **[fixed in 2.0]** — result is state (`speedText`), rendered by `homePage()` |
| M8 | Stale "Connecting to Termux…" blocks survive restarts | **[fixed in 2.0]** — marked as interrupted on boot |

### 1.4 Stress matrix (what was exercised)

Executed against 2.0 via a jsdom harness with a stubbed bridge (`/tmp/smoke`, outside the repo):

* every page renders with empty / partial / malformed bridge JSON;
* Pro gating when trial = 0, bad key, good key, key without public key compiled in;
* console: start job → busy → Stop → exit code announced; ↑/↓ history; wrap; clear; new session;
  interactive guard routes `vim` to Termux; `startJob` returning `busy` / `missing`;
* files: names with spaces, nested folder, editor save / rename / delete / new file, outside-home path refused;
* async Plan round-trip with ticket mismatch ignored;
* hardware back from editor, folder and page; theme toggle + `prefers-color-scheme` change;
* `node --check` on the extracted script.

Java is review-only here (no Android SDK on the build host). First thing to do on a workstation:
`gradle wrapper --gradle-version 8.9 && ./gradlew assembleDebug` and install on a device
with Termux + `allow-external-apps=true`.

---

## 2. Phase 2 — Appearance & UI/UX overhaul

### 2.1 Design language (implemented in 2.0)

* **Tokens, not colours.** Two full palettes (dark default, light) defined once; the UI never
  references a hex value directly. Adding a third theme (OLED black, Solarized) is a 12-line change.
* **Terminal-grade type.** 11 px uppercase labels (`.leads span`) with 0.08 em tracking, monospace
  only inside `.term`, 10 px nav labels (was 8 px — below any legibility guideline).
* **Honest status.** Health text is real DOM text ("System optimal" / "System strained"), not a
  pseudo-element — screen readers and copy/paste get it.
* **Focus everywhere.** `:focus-visible` ring on all interactive elements; disabled buttons look
  disabled.

### 2.2 Navigation

* 9 tabs → **8** (Key folded into Support; APIs renamed Keys). Each tab is `flex: 1 0 64px`, has an
  active indicator (`.on::before`) and `aria-current="page"`.
* **Pro tabs wear a `PRO` chip** (`.lock::after`) instead of silently redirecting.
* **≥ 720 px (tablet, DeX, foldable open)**: nav becomes a left rail, content gets a max-width
  column — one `@media` block, no JS.

### 2.3 Motion (all respect `prefers-reduced-motion`)

| Interaction | Motion |
|---|---|
| Page change | 220 ms `rise` (fade + 8 px) on each card in `.winbody` |
| Running job | `.runbar .pulse` 1.1 s blinking dot next to the elapsed-time text and the Stop button |
| Toast (`toast()`) | 180 ms rise, auto-dismiss 2.4 s, `role="status"` |
| Gauge update | `transition: width 400ms ease-out` on `.bar i`; the number snaps |
| Button press | `tap()` → `MeridianPhone.haptic()`; next pass: add `:active { scale: .98 }` with an 80 ms transition |

Next (not yet built): shared-element feel between Files list → editor using the View Transitions API
(`document.startViewTransition`) — Chromium WebView ≥ 111 supports it and it degrades to a cut.

### 2.4 Console as the hero

* Run bar with live elapsed time, Stop, wrap toggle, clear, "Open in Termux".
* Failed blocks get a red left rule (`.out.fail`) and a haptic.
* Dock input: `autocapitalize="off" spellcheck="false" enterkeyhint="send"`, ↑/↓ history (persisted),
  Esc clears, Ctrl+C stops.
* "Still waiting…" hint after 8 s so a stuck job never looks like a frozen app.

---

## 3. Phase 3 — Feature expansion

### 3.1 Five new features, ranked by value ÷ effort

1. **Recipe Marketplace (Pro).** Recipes are already data (`[title, cmd, note]`). Load extra packs
   from a signed JSON URL (`https://…/recipes.json`, verified with the same P-256 key), let users
   publish their own as a Gist. Pack ideas: *AI on-device* (llama.cpp + a 1–3 B GGUF), *Pentest kit*,
   *Media box* (yt-dlp + ffmpeg), *Web dev* (bun/pnpm). This is the single best lever for both
   retention and the Pro upsell.
   ```js
   async function loadPack(url) {
     const { payload, sig } = await (await fetch(url)).json();
     if (!(await verifyPack(payload, sig))) return toast("Pack signature invalid");
     RECIPES.push(...JSON.parse(atob(payload)));
     saveLocal("m-packs", RECIPES); render();
   }
   ```
2. **Scheduled jobs.** Pick a recipe + cron-like interval; Meridian writes a `termux-job-scheduler`
   entry (`termux-job-scheduler --script ~/.meridian/jobs/<id>.sh --period-ms 3600000`) and shows
   last-run status from `~/.meridian/jobs/<id>.log`. Needs `termux-api` — Doctor already checks it.
3. **Shareable hardware report.** One tap renders the Hardware/System/Battery pages into a
   self-contained HTML/PNG (`html2canvas`-free: use the existing DOM + `<foreignObject>` SVG) and
   calls `Intent.ACTION_SEND`. Free users share with a small Meridian footer → organic growth.
4. **LAN remote dashboard (Pro).** Tiny HTTP server (`NanoHTTPD`-style, 200 lines) on
   `0.0.0.0:7331` serving the same `index.html` with the bridge replaced by `fetch("/api/…")`.
   Open the phone's dashboard on a laptop. Token in the URL, LAN only, off by default.
5. **Agent mode for Plan (Pro, later).** Plan already returns a list of commands. Add "Run plan"
   that executes them sequentially, stops on first non-zero exit, and feeds the failure back to the
   model once for a repair step. Guard rails: never `rm -rf`, never `curl | sh` without confirmation.

### 3.2 Settings & customization (add a Settings sheet from About)

| Setting | Storage key | Default |
|---|---|---|
| Theme: System / Dark / Light / OLED | `m-theme` | system |
| Accent colour (6 presets) | `m-accent` | mint |
| Refresh rate: Battery-saver (2 s) / Normal (1 s) / Live (250 ms graph) | `m-rate` | normal |
| Console font size 11–16 px | `m-termsize` | 12 |
| Keep screen awake while a job runs | `m-awake` | on |
| Haptics | `m-haptic` | on |
| Default shell for "Open in Termux" | `m-shell` | bash |
| Reset trial/licence (debug builds only) | — | — |

Implementation: one `settings` object hydrated from `localStorage`, applied in `applyTheme()` and
the interval setup; the sheet is a `<dialog>` so it is modal and Esc-closable for free.

### 3.3 Quality of life

* **Keyboard shortcuts** (external keyboard / DeX): `Ctrl+K` focus dock, `Ctrl+L` clear,
  `Ctrl+C` stop, `Ctrl+1…8` tabs, `/` focus dock from anywhere, `?` cheat-sheet overlay.
  ```js
  document.addEventListener("keydown", e => {
    if (e.ctrlKey && e.key === "k") { e.preventDefault(); dock.focus(); }
    if (e.ctrlKey && /^[1-8]$/.test(e.key)) { e.preventDefault(); go(TABS[+e.key - 1]); }
  });
  ```
* **Bulk actions in Files**: long-press enters selection mode; move / delete / zip (`zip -r`) the
  selection in one job. Already gated behind `files-write`.
* **Dark mode**: done, follows the OS and remembers an override.
* **Copy any block** (`navigator.clipboard` fallback to `MeridianPhone.clip`), **re-run** button on
  each finished block, **pin** a command to the Commands page.

---

## 4. Phase 4 — Availability, performance, accessibility

### 4.1 Cross-platform

The UI is a single static HTML file talking to a 40-method bridge. That is a portability asset:

| Target | Approach | Effort |
|---|---|---|
| Android (now) | WebView + Java bridge | shipped |
| Tablet / DeX / foldables | done via the 720 px rail; add `resizeableActivity="true"` | trivial |
| Desktop (Linux/macOS/Windows) | **Tauri** shell; bridge methods become Rust commands reading `/proc`, `sysctl`, WMI. Console runs the local shell with a PTY (`portable-pty`) — which also fixes H5 properly | medium |
| Web (PWA) | Static hosting + a "LAN remote" bridge (`fetch` to the phone). Install prompt via `manifest.webmanifest`; no telemetry possible without the phone, so position it as the remote view, not a standalone product | small |
| iOS | Not viable as the same product (no Termux, no `/proc`). Ship the remote-view PWA only | n/a |

Keep `index.html` free of Android-isms: every `MeridianPhone.*` call already goes through a
`try/catch`; wrap them in one `bridge` object so Tauri can swap `invoke()` in.

### 4.2 Performance & "99.9 % uptime" for a local-first app

Uptime here means **the app never hangs and never loses state**:

* No synchronous bridge call may take > 50 ms — enforce with a debug assertion that logs any
  `MeridianPhone.*` call over budget. Remaining sync offenders in 2.0: `probe()`, `speedStatus()`
  (both fast) — migrate anyway for consistency using the `askNetStart` ticket pattern.
* Live stream backpressure: `live.log` is read from the end; cap at 24 kB per block (done).
* `StrictMode` in debug builds to catch disk I/O on the main thread.
* Cold start budget 600 ms: defer `loadSense()` until first paint (done, async job), inline critical
  CSS (already a single file), set `android:windowBackground` to the dark token to kill the white flash.
* Crash safety: wrap `render()` in `try/catch` that shows the error and a "Reset UI state" button
  instead of a blank page.
* Memory: `MAX_BLOCKS = 40`, old blocks truncated — a day of use stays under 2 MB of `localStorage`.

### 4.3 Accessibility (WCAG 2.2 AA target)

| Requirement | Status |
|---|---|
| All controls reachable and operable by keyboard, visible focus | **[fixed in 2.0]** |
| `aria-label` on icon-only buttons, `role="tablist"/"tab"` on nav, `aria-current` | **[fixed in 2.0]** |
| Live regions: `#srstatus role="status" aria-live="polite"` announces job start / exit code | **[fixed in 2.0]** |
| Minimum 44 × 44 px touch targets (header buttons 36 px → raise to 44 in the next pass) | partial |
| Contrast ≥ 4.5:1 in both themes — tokens chosen for it; verify muted text (`--muted`) on light | partial |
| `prefers-reduced-motion` disables all transitions and the pulse | **[fixed in 2.0]** |
| Text scales with system font size (use `rem`, not `px`, for body copy) | todo — mechanical |
| Terminal output readable by TalkBack: add "Output, N lines" summary before each block | todo |

---

## 5. Monetization — how Meridian makes money

### 5.1 Principles

* **Charge once, up front, for the pro tabs.** The audience hates subscriptions for local tools and
  loves paying once for something that respects them. One-time purchase has the best conversion here.
* **Never run checkout inside your own WebView** (C3). Use a merchant of record so VAT/GST, invoices,
  chargebacks and refunds are their problem.
* **Trial, not crippleware.** 7 days of everything, then a clear, polite gate.

### 5.2 Model (implemented in 2.0)

| | Free | Pro — €9.99 one-time (launch at €6.99 for the first 500) |
|---|---|---|
| Telemetry pages, Doctor, Setup, Commands, Links | ✓ | ✓ |
| Console | 1 session | unlimited sessions |
| Files | browse + view | create / edit / rename / delete, bulk ops |
| Build recipes | — | ✓ |
| Plan (AI, bring your own key) | — | ✓ |
| Tweaks | — | ✓ |
| Future: Recipe Marketplace, LAN remote, Agent mode | — | ✓ |

Flow: **Buy** → system browser → Lemon Squeezy / Paddle / Gumroad checkout → key e-mailed →
paste in **Support → Enter licence key** → verified offline (P-256). Keys are issued with
`tools/license/sign.mjs`; wire it to the store's "order created" webhook so delivery is
instant. Google Play distribution: swap `activate()/unlocked()` for Play Billing — the UI does not
change.

### 5.3 Second revenue line: AI credits (optional subscription, later)

Plan currently requires users to bring an API key. Most will not. Offer **Meridian AI** at
€2.99/month: a 60-line Cloudflare Worker proxies to a cheap model, authenticates the licence key,
rate-limits per key, and never sees the phone's data beyond the goal text. Margin at current token
prices is > 80 %. Keep BYOK free forever so power users are not alienated.

### 5.4 Distribution & growth (zero budget)

1. **F-Droid-style trust**: keep the repo public (MIT), publish SHA-256 of each APK, sign releases.
   Power users pay precisely because they can read the source.
2. **GitHub Sponsors / Ko-fi** button on About — some users want to give €3 without a licence.
3. **Hardware report share card** (feature 3) carries the app name → organic reach on
   r/termux, r/androiddev, XDA.
4. **Recipe packs as content marketing**: each pack is a blog post + a reason to install.
5. **Launch sequence**: Reddit r/termux post with the audit itself ("what I fixed in my app and why")
   → Product Hunt → XDA thread. Charge from day one; do not launch free and flip later.

### 5.5 Revenue sanity check

At a conservative 2 % trial→paid conversion and 1,500 installs/month from organic Termux traffic:
30 sales × €9.99 ≈ **€300/month** at launch, compounding with packs. The AI subscription at 5 %
attach on the paid base adds a slow but recurring floor. The real upside is the Marketplace
(user-published paid packs with a 20 % platform cut), which turns the app into a channel.

---

## 6. Choosing between `original/` (1.8.6) and the root app (2.0)

The 2.0 app **is** the repository root (`app/`). `original/` is the 1.8.6 archive.

| | `original/` 1.8.6 | root `app/` 2.0.0 |
|---|---|---|
| Builds | needs your own project files | `./gradlew assembleDebug` |
| Data safety | `rm -rf $HOME` reachable | path validation fails closed |
| Paywall | bypassable, in-WebView | signed offline keys, browser checkout, 7-day trial |
| UI thread | blocks on `run`/`askNet` | async, cancellable |
| Console | no Stop, no history, no persistence | Stop, history, wrap, sessions, Termux handoff |
| Files | spaces hidden, new file broken | full CRUD with validation |
| Theme | light mode broken | tokenised, system-following |
| Tablet | phone layout only | side rail ≥ 720 px |
| A11y | none | focus, labels, live regions, reduced motion |

Recommendation: ship 2.0 from the root. Keep `original/` as the reference for behaviour questions and
delete it once 2.0 has been in users' hands for a release cycle.

The download page at [meridian0.grok.me](https://meridian0.grok.me) still serves 1.8.6 until you
upload a 2.0 APK and checksum. This repo does not host that site.

---

## 7. Execution order (prioritised backlog)

1. Build 2.0 on a workstation (`./gradlew assembleDebug`), install, run the Doctor page on a real device. Fix any
   bridge mismatch the jsdom harness could not see (Java is review-only here).
2. Generate the licence keypair, set `LICENSE_PUBKEY`, set `PAY_URL` to a Lemon Squeezy product.
3. Raise header buttons to 44 px, convert body `px` → `rem`, run an axe scan in Chrome remote debugging.
4. Settings sheet (§3.2) — small, unblocks theme/accents/refresh rate.
5. Keyboard shortcuts + bulk Files actions (§3.3).
6. Shareable hardware report (§3.1 #3) — growth loop.
7. Recipe Marketplace (§3.1 #1) — Pro value and content pipeline.
8. Scheduled jobs (§3.1 #2).
9. LAN remote dashboard (§3.1 #4) → becomes the PWA/Tauri bridge.
10. Meridian AI proxy + subscription (§5.3), Agent mode (§3.1 #5).
