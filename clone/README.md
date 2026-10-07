# Meridian (clone, 2.0.0-clone)

A dashboard for an Android phone, bonded to Termux. This is the enhanced clone of the
1.8.6 source in `../original/`. Both trees live side by side so you can pick either one.

Readings still come from the device; commands still run in Termux on that phone. There is
still no account and no server. What changed is everything that made the original fragile,
slow, or hard to charge money for. The full list, with the reasoning, is in `../AUDIT.md`.

## Build

The clone is a normal Gradle project (AGP 8.7, compileSdk 35, minSdk 26, Java 17).

```bash
cd clone
gradle wrapper --gradle-version 8.9      # once, if you do not have a wrapper yet
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                # needs a signing config; see below
```

Release builds are minified with R8. `app/proguard-rules.pro` keeps the JavaScript bridge
(`MainActivity$Deck`) and the Termux receiver intact.

To sign a release, add to `app/build.gradle.kts`:

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

Every install gets a 7-day trial of Pro (`TRIAL_DAYS` in `MainActivity.java`). After that the
Pro tabs show a `PRO` badge and route to the purchase page.

## Turning on payments

1. **Generate the licence keypair** (once, on your own machine):

   ```bash
   cd clone/tools/license
   node keygen.mjs            # writes keys/private.pem (secret) and keys/public.b64
   ```

   Paste the printed line into `MainActivity.java` as `LICENSE_PUBKEY`. The private key is
   git-ignored; back it up offline.

2. **Point `PAY_URL` at your checkout.** Use a merchant of record (Lemon Squeezy, Paddle or
   Gumroad) so VAT/sales tax, invoices and refunds are handled for you. The app opens the URL
   in the system browser; nothing payment-related runs inside the WebView.

3. **Issue keys after each sale:**

   ```bash
   node sign.mjs --ref ORDER-1234        # prints MERIDIAN-xxxx.yyyy
   node verify.mjs MERIDIAN-xxxx.yyyy    # checks it exactly like the app does
   ```

   Lemon Squeezy and Paddle can run this from a webhook so the key is emailed automatically.
   Until then, issue by hand from the order e-mail.

4. The customer pastes the key in **Support → Enter licence key**. Verification is offline
   (P-256 ECDSA over the key payload), so there is no licence server to keep up.

If you ship on Google Play instead of sideloading, replace steps 2–4 with Play Billing; the
`activate()` / `unlocked()` bridge methods are the only touch points.

## Layout

```
clone/
├── app/src/main/AndroidManifest.xml      receiver not exported, backup rules, no cleartext
├── app/src/main/java/com/meridian/shell/
│   ├── MainActivity.java                 WebView host + JavaScript bridge (class Deck)
│   └── TermuxResult.java                 result receiver for com.termux.RUN_COMMAND
├── app/src/main/assets/www/index.html    the whole UI (vanilla JS, no build step)
├── app/src/main/res/                     icon, strings, backup/extraction rules
├── app/proguard-rules.pro
└── tools/license/                        keygen / sign / verify for Pro keys
```

MIT licence, same as the original. The signing key is not included.
