# Meridian — historical audit and roadmap

Written against the 1.8.6 build (package `com.meridian.shell`). **Superseded by the app in `app/`.** Kept for reference. The licensing and monetization sections were removed when Meridian became open source under the MIT licence (see `LICENSE`).

This is a Termux command deck: live CPU, GPU, RAM, battery, and thermals on the phone, plus curated shell recipes (setup, doctor, build, plan) that run in Termux through `com.termux.app.RunCommandService`. There is no account.

---

## P0 — functional fixes (do these before any visual work)

### 1. Exported result receiver can wedge or spoof a job

`AndroidManifest.xml` sets `TermuxResult` to `android:exported="true"`. `launch()` delivers an explicit `PendingIntent` aimed at that class, so Termux can still deliver results when the receiver is not exported. Any other app on the phone can broadcast `com.meridian.shell.TERMUX_RESULT` today.

`TermuxResult.onReceive` trusts `gen`. `jobGen` starts at 0 and increments by one, so it is guessable. If the broadcast includes a non-empty `b64` extra, the receiver writes `pendingOut` and **returns without setting `ready`**. `startJob` has no timeout in JavaScript. The console stays on “Connecting to Termux” until the process dies.

```xml
<receiver
    android:name="com.meridian.shell.TermuxResult"
    android:exported="false">
    <intent-filter>
        <action android:name="com.meridian.shell.TERMUX_RESULT" />
    </intent-filter>
</receiver>
```

In `TermuxResult`, ignore `b64` unless you also set `ready` and `pendingCode`. Drop `com.meridian.deck.TERMUX_PARTIAL` until something sends it. Compare `intent.getPackage()` / `getSendingUid()` if you ever accept an implicit broadcast. The explicit PendingIntent path does not need the receiver to be exported.

Also align the action string. `launch()` puts `com.meridian.deck.TERMUX_RESULT` on the PendingIntent, and the manifest never lists that action. Explicit delivery hides the mismatch until a future Android version gets stricter. Use one action: `com.meridian.shell.TERMUX_RESULT`.

### 2. `prompt()` cannot create files

Files says “New file” and “New folder” and calls `prompt()`. `onCreate` never installs a `WebChromeClient`, and WebView does not show `prompt` / `alert` / `confirm` without one. Both buttons look live and do nothing.

```java
web.setWebChromeClient(new android.webkit.WebChromeClient() {
    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String def, JsPromptResult result) {
        // show an EditText dialog, result.confirm(text) or result.cancel()
        return true;
    }
});
```

Same dialog should confirm destructive recipes before `exec` (see Format below). Do not keep using `prompt()`.

### 3. System orb sticks on fault

`sick()` in `index.html` returns true if **any** block in the session matches `/exit ([1-9]\d*)/`. One failed `pkg search` paints `SYSTEM FAULT` for the rest of the process, including after the next command exits 0. The header mini orb follows it.

```javascript
function sick(p, s) {
  if (bond !== "termux") return true;
  const total = Number(p.memTotal) || 0;
  if (total > 0 && (total - Number(p.memFree || 0)) / total >= 0.94) return true;
  if (Number(s.temp) >= 46) return true;
  const last = blocks.length ? String(blocks[blocks.length - 1].out || "") : "";
  return /exit ([1-9]\d*)\s*$/.test(last);
}
```

`memFree` in the probe JSON is `MemAvailable` (`meminfo()` index 1). The label is right; the Java field name is not. Rename the JSON key to `memAvailable` when you next touch both sides together.

### 4. Disconnect does not survive leaving the app

Connect writes `userOff` in a JS `let`. The process is killed, `index.html` runs `if (!userOff) bond = MeridianPhone.bond()`, and the header says Connected again. Persist it:

```javascript
if (bond === "termux") {
  userOff = true;
  localStorage.setItem("m-bond-off", "1");
  bond = "off";
  render();
  return;
}
localStorage.removeItem("m-bond-off");
```

Read the flag before the initial `MeridianPhone.bond()` call. Disconnect still does not revoke the Termux permission; say that on the button (“Hide connection”) or add a second action that opens Termux’s app settings.

### 5. Hold freezes the CPU spark only

`paintGraph` skips `pushCpu` when `held` is true and always calls `pushGpu`. Pause both, and pause `paintMeters` writes into `#cpuhz` / `#gpupct` while held. The button label already flips to “Live”.

### 6. Running a recipe throws the user into the console

```javascript
if (view !== "build") view = "console";
```

Setup, Doctor, Tweaks, and Commands all navigate away at the moment the command starts, so the Run button and the explanation are gone. Keep `view` where it was. Show the live transcript under the sheet (`termLog()` already exists on Build). Only the Termux → Console screen should own the dock.

### 7. Fourth session fails with no message

`newses` returns when `sessions.length >= 4`. The button stays enabled. Set `disabled` and set the foot line to “4 sessions. Close one from its tab.” There is no close control at all: add a long-press or an × that drops `sessions[i]` unless it is the last one.

### 8. Dead controls and dead bridge methods

Wired in Java, never called from the page:

- `removePath`, `renamePath` — the Files guide says “Hold a file, drag it onto a folder, then release to move it.” `loadFiles` only sets `onclick`. Either implement drag (touch `pointerdown` / `pointerup`, hit-test a folder, call `renamePath`) or delete the sentence.
- `dropCache` / `#ramclean` — the click handler exists, the button is not in `homePage()`. Device care only calls `openCare()`. Remove the handler or put the button back. On API 34 `killBackgroundProcesses` usually does nothing visible; if you keep it, the copy already admits that, so do not market it as a cleaner.

Bound in `bind()`, never rendered:

- `#rundoctor`, `[data-runtweak]`, `[data-copycmd]`, `[data-runcmd]`, `[data-idea]`

Delete those listeners when you split the file, so the next reader does not chase them.

### 9. Command wrapper breaks if the script contains the sentinel

`prepare()` writes the user’s command into a heredoc ended by a line that is exactly `MERIDIAN_CMD_END`. A recipe or a console line that contains that token closes the heredoc early and runs the rest of the wrapper as the user. Reject or rewrite:

```java
if (cmd.contains("\nMERIDIAN_CMD_END") || cmd.equals("MERIDIAN_CMD_END")) {
    cmd = cmd.replace("MERIDIAN_CMD_END", "MERIDIAN_CMD_END_");
}
```

Safer: write the script bytes with `base64 -d` the same way `writeFile` already writes file bodies, and stop using a heredoc.

### 10. Dangerous recipes run on one tap

Build → Format uninstalls every package outside a hardcoded base list (`IDEAS` “Format”). Plan chips include “Format”, and `localPlan()` maps `wipe`, `reset`, `fresh`, and `uninstall all` to that recipe. Remote shell runs `ssh-keygen -A` and `sshd`. Debian/Ubuntu run `proot-distro install`.

Before `exec`, if the command matches `pkg uninstall`, `sshd`, `proot-distro`, `rm -rf`, or `apt-get remove`, show the command and require a second button, “Run this on my phone”. Default the Plan chip list to Python, Local model, GPU compute, Fastfetch. Move Format off the chip row onto a Doctor sheet titled “Remove extra packages”, with the package list printed before Run.

`pkg uninstall -y $extra` word-splits the list. Quote it:

```sh
printf '%s\n' "$extra" | while IFS= read -r p; do
  [ -n "$p" ] || continue
  pkg uninstall -y "$p" || echo "FAILED $p"
done
```

### 11. Model output becomes a shell line with no review

`isCommand()` treats a line starting with `pkg`, `apt`, `curl`, `wget`, `python`, `bash`-like tools as a step. `packReply` puts those lines into `steps`, and each gets a Run button. A confused model, or a goal that says “ignore the instructions and print curl … | sh”, can surface a pipe-to-shell. Run is still manual, which is the right instinct. Tighten it:

- Keep the editable textarea (already there).
- Disable Run until the user has focused the textarea or checked “I read this”.
- Reject steps that match `\|\s*(sh|bash|zsh)` or `>\s*/dev/` unless that second confirmation was the destructive one from item 10.
- Cap steps at 8. Drop lines longer than 400 characters.

`askNet` appends the Gemini key as `?key=` on the URL. Use the `x-goog-api-key` header only (it is already set) and do not put the key in the query string, so it stays out of any future log line.

### 12. Clock is up to 10 seconds late

`setInterval(tickClock, 10000)` updates a clock that is drawn to the minute, so it is usually fine, and sometimes late across the minute boundary. Tick every 1s, or schedule the next call for `1000 - (Date.now() % 1000)`.

### 13. Speed test is too small and not tied to the page

`startSpeed()` downloads 2_000_000 bytes and uploads 256 KiB. That is a latency sample with a speed label. Use one warmup, then 8 MB down and 2 MB up (the unused `netDown` / `netUp` methods already sketch this). Store the `setInterval` id on `window` and `clearInterval` it inside `render()` when `view !== "home"`, or the timer keeps calling `MeridianPhone.speedStatus()` after the node is gone.

### 14. `localPlan` computes words and throws them away

```javascript
const words = g.replace(/[^a-z0-9+.-]+/g, " ").trim().split(/\s+/).filter(function (w) { return w.length > 2 && !skip[w]; });
if (!words.length) return null;
return null;
```

Either delete `words` or use it: if one word equals an `IDEAS` name or a `pkg` name from the command list, return `["pkg install -y " + word]` only when `^[a-z0-9][a-z0-9.+-]*$` matches. Do not interpolate arbitrary text into a shell line.

### 15. Cleartext and file access

`android:usesCleartextTraffic="true"` is set, and every URL the app builds is `https://`. Remove the attribute. `setAllowFileAccess(true)` is required for `file:///android_asset/`. Do not also enable `setAllowFileAccessFromFileURLs` or universal access; they are not on today, leave them off.

### 16. `run()` can freeze the bridge for 25 seconds, and `startJob` can overlap

`Deck.run` holds `jobLive` and `waitResult(25000)` on the JavaBridge thread. `loadSense()` uses it for `uname`. A stuck Termux makes System and Files feel frozen, because `entries` and `readFile` also call `run`. Prefer `startJob` + `jobStatus` for files the same way the console already polls.

`startJob` does not refuse a second launch. `launch()` bumps `jobGen`, and the previous PendingIntent result is ignored. Mirror the JS `busy` flag in Java:

```java
public String startJob(String cmd) {
    if (jobLive) return "busy";
    jobLive = true;
    launch(cmd);
    return "ok";
}
```

Reset `jobLive` in a `finally` if `startService` throws, and add a 120s deadline inside `jobStatus` that sets `ready` with `pendingCode = 124` so the UI cannot spin forever.

---

## P1 — appearance

The current look is already a point of view: black, brass `#c4a46a`, cream `#f3ead7`, hairline gold rules, a spinning “core” for health. Do not replace it with a generic indigo dashboard. Tighten it into an instrument, not a website.

### Tokens

Replace the scattered hex in `index.html` (`#c4a46a` is repeated in rules that ignore the theme variables, including `.modes .ghost.on` and `#dock .btn`) with these tokens only:

| Token | Dark | Light |
|---|---|---|
| `--bg` | `#0c0b09` | `#f4efe6` |
| `--fg` | `#f6f1e7` | `#1c1612` |
| `--muted` | `#b7aa98` | `#5e5144` |
| `--accent` | `#e0b15a` | `#8a5a12` |
| `--danger` | `#ff6b6b` | `#9f1239` |
| `--ok` | `#9dce7a` | `#3f6b2a` |
| `--plate` | `#16130f` | `#fffaf3` |
| `--line` | `rgba(224,177,90,.28)` | `rgba(90,60,20,.22)` |
| `--font` | `"Literata", "Iowan Old Style", Palatino, serif` for titles | |
| `--mono` | `"IBM Plex Mono", ui-monospace, monospace` for commands and figures | |
| space | 4 / 8 / 12 / 16 / 24 only | |
| radius | 12 for controls, 20 for the bottom bar | |

Muted `#a89884` on `#000` is acceptable for large labels and tight for 10px “Heat / Voltage” captions. Bump caption color to `#b7aa98` and caption size to 11px. Accent buttons that hardcode `#c4a46a` on cream fail in light mode because the light theme never overrides them. Every filled button should use `background: var(--accent); color: var(--btnfg)`.

Type scale: 11 captions, 13 body, 16 section, 28 the battery percent, 13 tabular figures. `font-variant-numeric: tabular-nums` is already on the leads; keep it on every figure that ticks.

### Navigation

Nine tabs (Dashboard, Hardware, System, Battery, Build, Plan, Key, APIs, Termux) sit in a horizontally scrolling bar. Labels are `font-size: 8px`. Header ghost buttons are 26px tall. Both fail a 44px target and WCAG text size.

Collapse to five:

1. **Now** — dashboard leads, sparks, health, network.
2. **Phone** — hardware + system + battery as three segments inside one screen, not three tabs.
3. **Work** — Build and Plan, with Key and APIs as a single “Model” sheet reached from Plan when no key is saved.
4. **Termux** — Files, Console, Setup, Doctor, Tweaks, Commands.
5. **You** — theme, connection, support, about.

```css
nav button { flex: 1 0 0; min-height: 48px; font-size: 11px; }
header .ghost { min-height: 36px; }
```

Put the active tab indicator on a 2px brass underline, not only a color change, so the state survives color-blindness and grayscale.

### Motion

Keep the core orb. Gate it:

```css
@media (prefers-reduced-motion: reduce) {
  .osx b, .orb, .logo i, .logo s, .blink { animation: none; }
}
```

The existing reduced-motion block only kills `.winbody > *` and press transitions. The orb, logo, and caret keep spinning.

Add motion only at these points:

- Tab change: the `.winbody` already has `rise` (8px, 220ms). Keep it. Do not fade the whole WebView.
- Bond line: when `bond` flips to `termux`, a 200ms brass underline grows from the left (`scaleX` from 0 to 1). No confetti.
- Run: the dock button swaps its label to “Running” and sets `aria-busy="true"` until `jobStatus.done`.
- Sparks: they already sample over time. Do not animate the SVG with CSS on top.
- Press: `scale: 0.96` is already there. Leave it.

Drop `gridshift` (defined, never applied) and the duplicate `.winbody` padding rule.

### Layout friction

- Home, when `bond !== "termux"`, should be a single column: status, the one-line `allow-external-apps` command, Copy, Open Termux, then the four leads. Move the frequency plate and the orb below. Right now the connect card, four leads, memory pair, device care, two sparks, the orb, and battery/storage/network compete on first paint.
- Command sheets: title, one sentence (`whyFor`), the textarea, Run. The Bands grid of 80+ commands is a catalog, not a home. Add a text filter at the top of Commands and Build (`input` that hides `.ico` whose `em` does not contain the query). That is the highest-leverage layout change in the app.
- Console dock is `position: fixed` and `#main` grows padding when `body.term`. After the nav collapse, measure the dock instead of hardcoding `148px`.
- `pre` uses `white-space: pre` and forces horizontal scroll for the bond line. Use `pre-wrap` for setup snippets and `pre` only inside `.term`.

### Assets

- Rebuild `ic_launcher.png` as an adaptive icon: brass ring on `#14110e`, no tiny text. Ship `mipmap-anydpi-v26` XML plus foreground/background vectors.
- Empty states need one line and one action, not a second illustration system. Files already says “Empty.” Add “New file” next to it.
- Do not add stock 3D phone mockups inside the app. The orb is the mascot.

---

## P2 — features worth building

Five, in the order they earn their keep. Each one sits on a bridge that already exists.

### 1. Command receipt

After every `exec`, persist `{ title, cmd, exit, at }` in `localStorage` (cap 100) and show it under Termux → Recent. One tap fills the dock. This is the feature people return for, and it makes Pro tangible: free keeps 10, Pro keeps 100 and exports a `.txt` into Termux home via `writeFile`.

### 2. “What this will change” on every Run

`whyFor()` already returns a sentence, and Doctor sheets already show it. Extend `whyFor` with a second line from a table: `network`, `installs packages`, `starts a server`, `deletes packages`. Render that line in `--danger` when it is not `reads only`. This is also the safety fix in P0 item 10, surfaced as product.

### 3. Job that survives the screen

`prepare()` already backgrounds the Termux service. The UI forgets the job when the WebView reloads, because `sessions` is memory. On `startJob`, write `~/.meridian/job.json` (`gen`, first line of the command, start time). On launch, if `~/.meridian/code` is missing and `live.log` grows, reattach the console to that log. Users will pay for “I started llama and the phone slept.” Pair it with the existing Wake lock tweak, and show a notification while `jobLive` is true (`NotificationChannel` `"jobs"`, low importance, ongoing).

### 4. Model sheet that matches the provider

Key defaults to `https://api.x.ai/v1` and `grok-3`, which is a paid API, while APIs lists Groq, Gemini, and OpenRouter as the free path. First open of Plan with no key should land on APIs, not on an empty error after “Write the steps”. Store the provider name next to `m-base` / `m-model` / `m-key`. Add “Test key” that sends `goal: "Reply with the single word pong"` and shows the HTTP error from `apiError` without creating steps.

Relay customers skip this sheet. Own-key customers need it to work the first time, or they blame the app and never subscribe.

### 5. Paired read-only status on the LAN

A checkbox in You: “Share readings on this Wi-Fi.” Bind `127.0.0.1` only unless the box is on, then bind the Wi-Fi address, one JSON snapshot from `status()` + `probe()`, no shell, no key, token in the path (`/s/<16 random bytes>`). The laptop opens it. This is the cross-device story that does not pretend Meridian is an iOS app. Pro feature. Off by default. The current `ServerSocket` in `openLive()` is the pattern; do not reuse that port for the public snapshot.

Skip, on purpose: a social feed, accounts, cloud sync of shell history, a second theme pack store, RAM booster marketing. They fight the “no account, runs on this phone” sentence the about page already uses. That sentence is the brand. Bend money around it, do not delete it.

---

## P2b — settings that are actually missing

| Setting | Default | Why |
|---|---|---|
| Theme | dark, already persisted as `m-theme` | Keep. Add “Use phone setting” that follows `prefers-color-scheme` |
| Text size | 16 | A slider that sets `--font-size` on `html`. The console is 12px and dense |
| Confirm before Run | on for install/uninstall/sshd | P0 item 10 |
| Keep screen on during a job | off | Calls the existing `termux-wake-lock` / `termux-wake-unlock` pair around `exec` |
| Reduced motion | follow system | You already have a partial media query |
| Clear command log | button | Sessions are not persisted today; once they are, this is required |
| Export readings | button | One JSON from the last `probe()` via the share sheet |

There is already a dark/light toggle. Do not list “add dark mode” as new work.

Keyboard, because Bluetooth keyboards are common with Termux:

| Key | Action |
|---|---|
| Enter in the dock | submit, already true |
| Up / Down in the dock | walk the receipt stack |
| Ctrl+L | clear the visible transcript, not the receipt |
| Esc | shelf back / more back |
| 1–5 | the five tabs |

Implement with one `keydown` listener on `document`, ignore it when the target is a textarea other than the dock.

---

## P3 — availability, speed, access

### Android, which is the product

- `targetSdkVersion` is 34. Move to 35 and opt into edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`). The page already uses `viewport-fit=cover` and `safe-area-inset-bottom`.
- `configChanges` includes orientation, so the WebView is not recreated. Good. Persist `view`, `cwd`, and `sessions` anyway, because the process still dies.
- `onRequestPermissionsResult` is missing. After the user answers `RUN_COMMAND`, nothing calls `bond()` again until they press Connect. Override it and `render()` from JS via `web.evaluateJavascript`.
- Notification permission is not required for a low channel on 26+, but a foreground service is the right way to keep a long `pkg upgrade` alive. Termux already runs the command; Meridian only needs the ongoing notification so Android does not freeze the WebView timer.
- Ship per-ABI or a universal APK plus a SHA-256 checksum with each GitHub release.
- `cpuCores()` stops at 16. Read `/sys/devices/system/cpu/present` instead of `for (i < 16)`.

### Not a desktop app, not an iOS app

The bridge is `com.termux.permission.RUN_COMMAND` and `/data/data/com.termux/files/home`. That does not exist on iOS or on a laptop. A responsive wrap of `index.html` in a browser shows an empty dashboard, because `MeridianPhone` is absent (`phone()` swallows the exception and returns `{}`).

Do this instead:

- **Laptop:** the LAN snapshot in P2 item 5, plus the Remote shell recipe that already installs `sshd`. Document the port and that `sshd` with no password is a hole; the recipe should print “set a password or a key before you leave this screen” and refuse to start `sshd` until `passwd` has been run or an authorized key exists.
- **iOS:** do not promise it. If you ever do a cousin app, it is a different shell (a-Shell or libssh to the Android phone), not a port of this Activity.

### Speed

The UI jank source is `setInterval(paintGraph, 80)` calling `live()` → `MeridianPhone.probe()`. Java refreshes the heavy probe every 80ms (`probeAt`), and `cpuCores()` plus `meminfo()` plus sysfs GPU files run on that cadence even when the numbers are for a spark line. That work heats the phone the orb is judging.

- Probe static props (kernel, GL, fingerprint, SoC) once per process. They already go through `props()` with `cachedProps`; stop merging them into the 80ms object.
- Sample CPU% and net rate every 500ms. Sample thermals every 2s.
- Change `paintGraph` to 500ms. A spark of 60 points is then 30s of history, which is what the chart is for. Delete the 80ms loop.
- `homePage()` rebuilds the world on every `render()`, which `loadSense` triggers. `paintMeters` already patches `[data-k]`. Do not call `render()` from the 500ms timer.
- `exec` polling at 500ms is fine.

There is no database. Do not add one for readings. A ring buffer of 120 samples in a `float[]` inside the Activity is enough, and it lets the page ask for “last minute” without re-reading sysfs 12 times a second.

Uptime for a sideloaded APK is a phone fact, not a server SLO. The 99.9% line applies only to Relay and the download site: one region, health check on `GET /health`, static APK on a host that can serve a file if the app server is down. Put the APK on two URLs and print both on the site.

### Access

- Nav labels 8px → 11px, targets 48px (P1).
- Every icon-only control needs an accessible name: `#winlogo` (“Home”), `#theme`, `#link`, the orb. `aria-label` on the button. The status text is a CSS `::before` (`SYSTEM OPTIMAL` / `SYSTEM FAULT`), which many screen readers skip. Put the words in a real element:

```html
<p id="healthtext" role="status">System optimal</p>
```

and set `healthtext.textContent` in `paintMeters`.

- Do not use color alone for Connected. The bond line already has words. Keep them, and add `aria-live="polite"` on `#bondline`.
- Focus: `button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }`. Nothing in the stylesheet sets `:focus`.
- Contrast: light-theme accent `#8a6a3e` on `#fffaf3` is a border and a button fill. As 13px text on cream it is close to the 4.5:1 line; use `#6b4e24` for text and keep `#8a6a3e` for fills with cream text (`#fffaf3` on `#8a6a3e` passes).
- `esc()` is used for HTML interpolations of command output and file names. Keep that. Do not switch the terminal to `innerHTML` of raw `out`.
- `lang="en"` is set. If you add a second language, do it as full strings in one object, not scattered edits. The audience is Termux, English-first is fine for the next build.

---

## Suggested build order

1. **Trust.** Receiver `exported=false`. Free Setup and System check. Checksums on GitHub releases.
2. **Stop the dead ends.** `WebChromeClient` prompts, sticky `sick()`, persisted disconnect, recipe confirmations, heredoc/base64 runner, `startJob` overlap and timeout.
3. **Five tabs and a command filter.** Same brass palette, fixed tokens, reduced motion that includes the orb.
4. **Receipts, reattach, Test key.** Then Relay if own-key usage shows people actually press Plan.
5. **LAN snapshot and the sshd warning.** Not an iOS port.

The 1.8.6 tree was removed in 2.3.0. The current app is at the repo root. This document is the older 1.8.6-only list. Do not start with a new theme.
