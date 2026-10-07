# Meridian

A dashboard for an Android phone, bonded to Termux. Cloned here from version **1.8.6** (`versionCode` 36) so the source can be read, forked, and chosen from later.

Readings come from the device. Commands run in Termux on that phone. The shipping app has no account and no server. The public page at [meridian0.grok.me](https://meridian0.grok.me) only hands out the APK.

## Tree

| Path | Role |
|---|---|
| `AndroidManifest.xml` | Package `com.meridian.shell`, permissions, Termux result receiver |
| `src/com/meridian/shell/MainActivity.java` | WebView shell, Termux runner, sensors, paywall, model calls |
| `src/com/meridian/shell/TermuxResult.java` | Broadcast receiver for command results |
| `assets/www/index.html` | Entire UI: dashboard, hardware, system, battery, build, plan, key, APIs, Termux tools |
| `res/drawable/ic_launcher.png` | Launcher icon |
| `meridian-source.zip` | Untouched archive the clone was extracted from |
| `docs/ROADMAP.md` | Functional audit, UI plan, features, and how this can make money |

The signing key is not included. The app is MIT; see `LICENSE`.

People may study it, share it, change it, and fork it. Because the unlock check ships in this source, a local paywall is not a business. `docs/ROADMAP.md` says what to charge for instead.
