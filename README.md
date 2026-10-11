# Meridian

A dashboard for an Android phone, bonded to Termux. Version **2.3.28**.

Readings come from the device. Commands run in Termux on that phone. There is no account and no server.

The public page at [meridian0.grok.me](https://meridian0.grok.me) still hands out the 1.8.6 APK until you upload a 2.3.0 build. **This repository is the current app.** The UI, the Gradle project, and the licence-key paywall live at the root. The old 1.8.6 tree (`original/` and `meridian-source.zip`) was removed so the repo is not carrying two copies of the app.

Prices are in [PRICES.md](PRICES.md).

| Path | Role |
|---|---|
| `app/` | The app: WebView host, Termux bridge, UI (`app/src/main/assets/www/index.html`) |
| `desktop/` | Local desktop companion: readings, a loopback metrics page, and a stdio shell |
| `tools/license/` | Generate / sign / verify Pro and Premium licence keys |
| `tools/qa/` | Static UI audit (symmetry, duplicates, graph updates) |
| `AUDIT.md` | Historical audit of 1.8.6. The `original/` tree it cites is gone |
| `docs/ROADMAP.md` | Earlier 1.8.6-only roadmap. Prefer the app in `app/` |
| `PRICES.md` | Free, Pro, and Premium |

## Install the app (APK)

A GitHub zip of the source **does not contain an `.apk`**. That file is built, not committed.

**Ready-made (1.8.6, current public build):**
[Meridian.apk on the v1.8.6 release](https://github.com/kukirin-ui/Meridian/releases/download/v1.8.6/Meridian.apk)
or [meridian0.grok.me](https://meridian0.grok.me). That is the old app.

**2.3.28 debug APK (this branch):** GitHub Actions builds it on every push.
1. Open the [Build APK workflow](https://github.com/kukirin-ui/Meridian/actions/workflows/build-apk.yml) (Actions → **Build APK**).
2. Open the latest green run for this branch.
3. Download the artifact **Meridian-2.3.28-debug** (a zip with `app-debug.apk` inside).
4. Copy that APK to the phone, open it, allow install from this source. The version in About is **2.3.28** (versionCode 71). About also shows **Downloads**, the public total of `.apk` files on the GitHub releases page. The phone keeps the last number. A failed request shows that number, or **—** if it has never loaded.

The same Actions run also has **Meridian-sandbox-home**. That download is one zip. In Termux, `cd ~` and unzip it. The zip creates `~/meridian-sandbox` and `~/meridian_sandbox`. Mesh uses `~/meridian-sandbox`. `~/meridian_sandbox/www/config.json` is the Gemma path from the setup notes. The app still reads the config shipped inside the APK. Leave the Meridian app as the APK you installed.

Free keeps a live Dashboard. Other pages are an imagined preview, pulse stays off, and colour does not stick. The gear offers Language, Plans, About, Privacy, and Documentation. Pro is the whole app. Premium adds Desk: run a whole plan, the scheduler, UFS, 32 API platforms, and a Markdown hardware report.

Termux → Mesh saves the name you choose. Speech wakes listening only when that name is spoken. The name alone opens a short command window, then it returns to standby. The Model menu uses Llama 3.2 1B, Qwen 2.5 3B, Llama 3.1 8B, Gemma 2 9B, or Qwen 2.5 32B. `config.json` sets the active model to Gemma 2 9B at `/data/data/com.termux/files/home/models/gemma-2-9b-it-Q4_K_M.gguf`, so Mesh opens on that file. Save identity keeps a different choice. Download model looks in the phone Downloads folder first (and `~/models`) and links a real file into `~/models`. It fetches a new copy only when that file is not already there, and it installs llama.cpp. A question runs that model with one skill card from Code Master, Graphics Master, or Understanding Master (36 skills each, in `docs/skill-trees.md`). The card is chosen from the question, or pinned in Skill card. Hardware lane is CPU, CPU and GPU, or GPU. The model then works for up to 5 steps in `~/meridian-sandbox`: it can read, write, edit, delete, and search there, and it can read a public page. A new file elsewhere in the Termux home folder is written immediately. Deleting or replacing an existing file outside the sandbox prints the change and waits for a fingerprint on this phone. `install apk ~/storage/downloads/app.apk` shows the file and waits for a fingerprint. After that, Shizuku's exported `rish` script runs `pm install`. `logcat` reads the system log through that same script as soon as it is in the Termux home folder. Open Shizuku, start it with Wireless debugging, choose Use Shizuku in terminal apps, and export rish. Termux by itself cannot install an APK or read the system log. `read file ~/notes/a.txt` reads one home file. `fetch https://example.com` reads a public page. The Voice menu uses the voices installed on the phone. Revenue layer R-16 still asks for a fingerprint before money, contracts, or public posts, and that signature does not run a shell command. `sudo`, `kill`, and `restart` do the same. The microphone listens only while that screen is open.

A curl of `https://huggingface.co` saves the website, a few kilobytes. Build → Local model downloads Gemma 2 9B Q4 (about 5.4 GB) from the file address. The same command with that homepage and the file name `gemma-2-9b-it-Q4_K_M.gguf` uses the file address.

Console text sent to the page stays at the newest 24,000 characters, and Termux itself only returns the tail of a huge log, so a long listing cannot freeze the screen. The command you type is written as one quoted script: quotes, pipes, newlines, and emoji stay inside that command. An unsent console line and the Plan text are kept on the phone if the app process is closed. Turning the phone does not reload the page.

The words paid, full, and unlocked are not keys. A plan opens only with a signed `MERIDIAN-` key. A debug build also accepts `MERIDIAN-DEV` in that field and opens Premium on this phone. A release build ignores it. Plans → Free clears it.

Plans can store two verified keys at once: **Pro / Personal** and **Premium Enterprise**. Switching runs the same signature check as a new key.

### Language, carrier, Linux PC, terminal mark

- **Language:** Settings → Language only. English, Hrvatski, Deutsch, 日本語, हिन्दी, Español, Français. Stored as `m-lang`. Recipes stay in English (they are shell). About does not have a language picker.
- **Settings:** the gear opens Plans, About, Documentation, Privacy, Device settings, Developer options, or App preferences. App preferences is the only in-app settings page.
- **Carrier:** the clock and the carrier sit beside the logo, with a signal glyph for this phone's Wi-Fi or cellular strength. The glyph updates from the active network while the app is open. The Android status bar and the Android navigation bar stay hidden so they do not cover the header or the bottom inventory. A swipe from the top or bottom edge shows them, then they hide. No location permission.
- **Window mark:** a small blinking terminal prompt in the window title. The page name stays centered.
- **Dashboard orb:** a terminal prompt (`>_`) with a caret. Settings → Motion **On** blinks the caret. Motion **Off** freezes it. It is not a globe.
- **Linux PC:** Termux shelf → **Linux PC**. This is a Pro feature. Check proot-distro, install Debian/Ubuntu, then **Open Debian** / **Open Ubuntu** for a real TTY in the Termux app. This is a Linux userspace, not a VGA Windows emulator. QEMU is not downloaded by default.
- **API platforms:** keys are encrypted with the Android Keystore (AES-GCM) into `files/api-platforms.bin`. They are not stored in SharedPreferences. An update keeps them only when the signing certificate stays the same. The address must start with `https://`. Free stores one platform, Pro stores eight, Premium stores 32. If Save fails, the line under the button is the reason (plan limit, address, or keystore). Fix that and save again. A reinstall with a different certificate starts empty.
- **Network Security:** this phone's link, DNS, and its own listening sockets. It does not scan other machines and does not capture packets.
- **Desktop:** `node desktop/meridian-desktop.mjs` prints local readings. `--serve` publishes JSON on `127.0.0.1` only. `--tty` opens the local shell on standard input. There is no command endpoint on the HTTP port.

You need Termux installed, and in Termux: `echo "allow-external-apps=true" >> ~/.termux/termux.properties` then `termux-reload-settings`. Then open Meridian and run Setup / Doctor.

To build the APK yourself (Android Studio, or JDK 17 + Android SDK):

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Release builds need a signing config (see below). `app/proguard-rules.pro` keeps the JavaScript bridge and the Termux receiver intact.

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("MERIDIAN_KEYSTORE") ?: "meridian.jks")
        storePassword = System.getenv("MERIDIAN_STORE_PASS")
        keyAlias = System.getenv("MERIDIAN_KEY_ALIAS")
        keyPassword = System.getenv("MERIDIAN_KEY_PASS")
    }
}
buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
```

## Free, Pro, and Premium

The consumer price is **Pro €15 once**. Each purchase includes two Pro keys: one for the buyer, and one they can give to a friend. An earlier draft used a pay-what-you-want span of €1–€1,000,000. That is not the price.

| Free | Pro (€15 once) | Premium (€15 and up) |
|---|---|---|
| Live Dashboard only | The whole app, with no limits | Everything in Pro |
| Other pages are an imagined preview | Live readings, Build, Plan, colour, and pulse | Desk: run a whole plan, the scheduler, UFS |
| Language, Plans, About, Privacy, Documentation | API platforms (eight) and Termux tools | 32 API platforms and a Markdown hardware report |

A fresh install stays Free until a signed key is activated. The first-run clock does not open Pro. `MERIDIAN-DEV` still opens Premium on a debug build.

Plans has one buy button, **Buy Pro · €15**. Premium is described on that page and unlocked by a separate `sku: premium` key. It does not reuse the Pro checkout.

Plans always shows the current plan, Upgrade to Premium, Switch to Pro, and Use the free plan. Upgrade to Premium activates a Premium key pasted in the field, or a Premium key already saved in the Premium Enterprise workspace. The €15 button stays the Pro checkout. There is no second checkout button.

Premium is a business key at €15 and up. The app does not sell it through the Pro button. See [PRICES.md](PRICES.md).

## Turning on payments

1. Generate the licence keypair (once, on your own machine):

   ```bash
   cd tools/license
   node keygen.mjs            # writes keys/private.pem (secret) and keys/public.b64
   ```

   Paste the printed line into `MainActivity.java` as `LICENSE_PUBKEY`. The private key is git-ignored; back it up offline.

2. Point `PAY_URL` at your checkout. The app opens it in the system browser. The button says **Buy Pro · €15**.

3. Issue two Pro keys after each €15 sale (one line each, two refs):

   ```bash
   node tools/license/sign.mjs --sku pro --ref ORDER --copies 2
   node tools/license/verify.mjs MERIDIAN-xxxx.yyyy
   ```

   Omit `--copies` when you only need one key. The default is 1.

   `sign.mjs` accepts `--sku premium` for the business key. Do not point that key at the €15 Pro checkout.

4. The customer pastes the `MERIDIAN-` key on **Plans** while the phone is still locked.

Typing `paid`, `full`, or `unlocked` does not unlock. A file in Downloads that only contains those words is ignored. If this install already has `files/full.key` from a real activation, that unlock stays. A new sideload with a different signing cert does not keep the old file, and the word `paid` will not replace it.

Then rebuild, upload the APK (and its SHA-256) to [meridian0.grok.me](https://meridian0.grok.me) so the download page matches this repo.

Proprietary. All rights reserved. See `LICENSE`. Versions already published under the MIT licence stay under MIT for those copies. The signing key is not included.
