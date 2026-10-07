# Meridian

A dashboard for an Android phone, bonded to Termux. Current release: **2.2.0** (`versionCode` 39).

The version number lives in `app/build.gradle.kts`. History lives in `CHANGELOG.md`. `original/` is the archived 1.8.6 source and is not the app to edit.

Readings come from the device. Commands run in Termux on that phone. There is no account and no server.

The public page at [meridian0.grok.me](https://meridian0.grok.me) still hands out the 1.8.6 APK until a 2.2.0 build is uploaded there. The app in this repository is `app/`.

| Path | Role |
|---|---|
| `app/` | The current app: WebView host, Termux bridge, UI (`app/src/main/assets/www/index.html`) |
| `CHANGELOG.md` | Which version is current, and what each release changed |
| `tools/license/` | Generate / sign / verify Pro licence keys |
| `original/` | Shipped 1.8.6 source, extracted verbatim from `meridian-source.zip` |
| `AUDIT.md` | Full A-to-Z audit of 1.8.6, what 2.0 already fixed, and the backlog |
| `docs/ROADMAP.md` | Earlier 1.8.6-only roadmap. Prefer `AUDIT.md` |
| `meridian-source.zip` | Untouched 1.8.6 archive |

## Install the app (APK)

A GitHub zip of the source **does not contain an `.apk`**. That file is built, not committed.

**Ready-made (1.8.6, current public build):**
[Meridian.apk on the v1.8.6 release](https://github.com/kukirin-ui/Meridian/releases/download/v1.8.6/Meridian.apk)
or [meridian0.grok.me](https://meridian0.grok.me). That is the old app.

**2.2.0 debug APK (this branch):** GitHub Actions builds it on every push.
1. Open the [Build APK workflow](https://github.com/kukirin-ui/Meridian/actions/workflows/build-apk.yml) (Actions → **Build APK**).
2. Open the latest green run for this branch.
3. Download the artifact **Meridian-2.2.0-debug** (a zip with `app-debug.apk` inside).
4. Copy that APK to the phone, open it, allow install from this source.

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

## Free vs paid

The paid version is everything. One payment, any whole euro from **€1** to **€1,000,000**, unlocks the same app.

| Free                                                     | Paid (one amount, €1–€1,000,000)              |
|----------------------------------------------------------|-----------------------------------------------|
| Dashboard, Hardware, System, Battery                     | Build packs: Workstation, Media box, On-device AI |
| One console session, Setup, Doctor, Commands, Links      | Plan (AI goal → command plan, bring your key) |
| Files (read-only browse and view)                        | Tweaks, four named console sessions           |
| Hardware report share, OLED / accent / Settings          | File create / edit / rename / delete / zip    |

Every install gets a 7-day trial of the paid version (`TRIAL_DAYS` in `MainActivity.java`). After that the paid tabs show a `PRO` badge and route to the purchase page.

## Turning on payments

1. Generate the licence keypair (once, on your own machine):

   ```bash
   cd tools/license
   node keygen.mjs            # writes keys/private.pem (secret) and keys/public.b64
   ```

   Paste the printed line into `MainActivity.java` as `LICENSE_PUBKEY`. The private key is git-ignored; back it up offline.

2. Keep the one Revolut link in `PAY_URL`. In the Revolut dashboard, turn on **Customer sets price** for that link, currency EUR. Do not make a separate product per amount. The app sends every amount from €1 to €1,000,000 to that same link, and copies the number so it can be pasted if Revolut asks.

3. Issue one key after each sale. The euros are optional and do not change what unlocks:

   ```bash
   node sign.mjs --ref ORDER-1234 --euros 10
   node verify.mjs MERIDIAN-xxxx.yyyy
   ```

4. The customer pastes the key in **Support → Enter licence key**.

If you already paid in **1.8.6**, there was never a pasteable key. Unlock was a hidden file `full.key` on the phone containing the word `paid` (written after Revolut). A new APK with a different signing cert looks like a different app, so that file is gone. Paste `paid` on the licence screen, or paste a `MERIDIAN-…` key issued with `tools/license/sign.mjs`. Put `full.key` in `Download/` and the current app will pick it up on launch.

Then rebuild, upload the APK (and its SHA-256) to [meridian0.grok.me](https://meridian0.grok.me) so the download page matches this repo.

MIT licence; see `LICENSE`. The signing key is not included.
