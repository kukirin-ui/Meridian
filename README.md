# Meridian

A dashboard for an Android phone, bonded to Termux. Version **2.0.0**.

Readings come from the device. Commands run in Termux on that phone. There is no account and no server.

The public page at [meridian0.grok.me](https://meridian0.grok.me) still hands out the 1.8.6 APK until you upload a 2.0 build. **This repository is 2.0.** The UI, the Gradle project, and the licence-key paywall live at the root. The unchanged 1.8.6 source is kept in `original/` for comparison.

| Path | Role |
|---|---|
| `app/` | The 2.0 app: WebView host, Termux bridge, UI (`app/src/main/assets/www/index.html`) |
| `tools/license/` | Generate / sign / verify Pro licence keys |
| `original/` | Shipped 1.8.6 source, extracted verbatim from `meridian-source.zip` |
| `AUDIT.md` | Full A-to-Z audit of 1.8.6, what 2.0 already fixed, and the backlog |
| `docs/ROADMAP.md` | Earlier 1.8.6-only roadmap. Prefer `AUDIT.md` |
| `meridian-source.zip` | Untouched 1.8.6 archive |

## See the app

There is no hosted web preview: Meridian is an Android + Termux app. The source you open on GitHub **is** the changed app:

- UI: [`app/src/main/assets/www/index.html`](app/src/main/assets/www/index.html)
- Bridge: [`app/src/main/java/com/meridian/shell/MainActivity.java`](app/src/main/java/com/meridian/shell/MainActivity.java)

To run it on a phone:

```bash
gradle wrapper --gradle-version 8.9      # once, if you do not have a wrapper yet
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
```

Install that APK on a device with Termux and `allow-external-apps=true` in `~/.termux/termux.properties`. Then open Meridian and run Setup / Doctor.

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

## Free vs Pro

| Free                                                     | Pro (one-time purchase)                       |
|----------------------------------------------------------|-----------------------------------------------|
| Dashboard, Hardware, System, Battery                     | Build recipes (Workstation, Machine, Library) |
| One console session, Setup, Doctor, Commands, Links      | Plan (AI goal → command plan, bring your key) |
| Files (read-only browse and view)                        | Tweaks, extra console sessions                |
|                                                          | File create / edit / rename / delete          |

Every install gets a 7-day trial of Pro (`TRIAL_DAYS` in `MainActivity.java`). After that the Pro tabs show a `PRO` badge and route to the purchase page.

## Turning on payments

1. Generate the licence keypair (once, on your own machine):

   ```bash
   cd tools/license
   node keygen.mjs            # writes keys/private.pem (secret) and keys/public.b64
   ```

   Paste the printed line into `MainActivity.java` as `LICENSE_PUBKEY`. The private key is git-ignored; back it up offline.

2. Point `PAY_URL` at your checkout (Lemon Squeezy, Paddle, or Gumroad). The app opens it in the system browser.

3. Issue keys after each sale:

   ```bash
   node sign.mjs --ref ORDER-1234
   node verify.mjs MERIDIAN-xxxx.yyyy
   ```

4. The customer pastes the key in **Support → Enter licence key**.

Then rebuild, upload the APK (and its SHA-256) to [meridian0.grok.me](https://meridian0.grok.me) so the download page matches this repo.

MIT licence; see `LICENSE`. The signing key is not included.
