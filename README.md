# Meridian

A dashboard for an Android phone, bonded to Termux. Version **2.2.2**.

Readings come from the device. Commands run in Termux on that phone. There is no account and no server.

The public page at [meridian0.grok.me](https://meridian0.grok.me) still hands out the 1.8.6 APK until you upload a 2.0 build. **This repository is 2.0.** The UI, the Gradle project, and the licence-key paywall live at the root. The unchanged 1.8.6 source is kept in `original/` for comparison.

Prices are in [PRICES.md](PRICES.md).

| Path | Role |
|---|---|
| `app/` | The 2.0 app: WebView host, Termux bridge, UI (`app/src/main/assets/www/index.html`) |
| `tools/license/` | Generate / sign / verify Pro and Premium licence keys |
| `original/` | Shipped 1.8.6 source, extracted verbatim from `meridian-source.zip` |
| `AUDIT.md` | Full A-to-Z audit of 1.8.6, what 2.0 already fixed, and the backlog |
| `docs/ROADMAP.md` | Earlier 1.8.6-only roadmap. Prefer `AUDIT.md` |
| `meridian-source.zip` | Untouched 1.8.6 archive |
| `PRICES.md` | Free, Pro, and Premium prices |

## Install the app (APK)

A GitHub zip of the source **does not contain an `.apk`**. That file is built, not committed.

**Ready-made (1.8.6, current public build):**
[Meridian.apk on the v1.8.6 release](https://github.com/kukirin-ui/Meridian/releases/download/v1.8.6/Meridian.apk)
or [meridian0.grok.me](https://meridian0.grok.me). That is the old app.

**2.2.2 debug APK (this branch):** GitHub Actions builds it on every push.
1. Open the [Build APK workflow](https://github.com/kukirin-ui/Meridian/actions/workflows/build-apk.yml) (Actions → **Build APK**).
2. Open the latest green run for this branch (`cursor/meridian-2-2-logo-premium-ce79`).
3. Download the artifact **Meridian-2.2.2-debug** (a zip with `app-debug.apk` inside).
4. Copy that APK to the phone, open it, allow install from this source. The version in Settings / About is **2.2.2** (versionCode 42). About also shows **Downloads**, the public total of `.apk` files on the GitHub releases page. The phone keeps the last number. A failed request shows that number, or **—** if it has never loaded.

### Language, carrier, Linux PC, terminal mark

- **Language:** Settings → Language only. English, Hrvatski, Deutsch, 日本語, हिन्दी, Español, Français. Stored as `m-lang`. Recipes stay in English (they are shell). About does not have a language picker.
- **Settings:** the gear in the top bar is the only way in. Dashboard and About do not add another Settings button.
- **Carrier:** on the dashboard, next to **Test network speed**. Wi‑Fi or the SIM operator when Android already allows it. Shows **—** if unknown. No extra dangerous permissions.
- **Window mark:** one logo in the window header on every page. It does not spin or change when you change pages. A tap can press it or go home; the glyph stays the same.
- **Dashboard orb:** a terminal prompt (`>_`) with a caret. Settings → Motion **On** blinks the caret. Motion **Off** freezes it. It is not a globe.
- **Linux PC:** Termux shelf → **Linux PC**, or Build → **Linux PC** pack. This is a Pro feature (the 7-day trial includes it). Check proot-distro, install Debian/Ubuntu, then **Open Debian** / **Open Ubuntu** for a real TTY in the Termux app. This is a Linux userspace, not a VGA Windows emulator. QEMU is not downloaded by default.

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

## Free and Pro

The price is **Pro €15 once**. Each purchase includes two Pro keys: one for the buyer, and one they can give to a friend. An earlier draft used a pay-what-you-want span of €1–€1,000,000. That is not the price.

| Free | Pro (€15 once) |
|---|---|
| Dashboard, Hardware, System, Battery | Build recipes, including Linux PC install/open |
| One console session, Setup, Doctor, Commands, Links | Plan (your API key or a local model). You press Run on each step |
| Files (read-only browse and view) | Tweaks, extra console sessions, file create / edit / rename / delete / zip |
| Hardware report with a small “Made with Meridian” footer | |

Every install gets a 7-day trial of Pro (`TRIAL_DAYS` in `MainActivity.java`). After that the Pro tabs show a `PRO` badge and route to the purchase page.

A phone that already has Pro sees a short thank-you on the Full version page. The paste field is only there when the phone is not unlocked.

Premium is designed and **not for sale**. The app has no Premium buy button. See [PRICES.md](PRICES.md).

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

   `sign.mjs` still accepts `--sku premium` for a later release. Do not sell that SKU yet.

4. The customer pastes the `MERIDIAN-` key on **Full version** while the phone is still locked.

Typing `paid`, `full`, or `unlocked` does not unlock. A file in Downloads that only contains those words is ignored. If this install already has `files/full.key` from a real activation, that unlock stays. A new sideload with a different signing cert does not keep the old file, and the word `paid` will not replace it.

Then rebuild, upload the APK (and its SHA-256) to [meridian0.grok.me](https://meridian0.grok.me) so the download page matches this repo.

MIT licence; see `LICENSE`. The signing key is not included.
