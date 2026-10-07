# Meridian

A dashboard for an Android phone, bonded to Termux. Readings come from the device, commands
run in Termux on that phone, there is no account and no server. The public page at
[meridian0.grok.me](https://meridian0.grok.me) only hands out the APK.

This repository holds the 1.8.6 source in several places (earlier extracts plus this branch)
and one enhanced 2.0 clone, so you can choose later.

| Path | Role |
|---|---|
| `original/` | 1.8.6 source extracted verbatim from `meridian-source.zip` (this branch). |
| `clone/` | Enhanced 2.0.0 clone: hardened Java bridge, Gradle project, new UI, licence-key paywall with a 7-day trial. Start here if you want the fixed app. See `clone/README.md` for build and payment setup. |
| `AUDIT.md` | Full A-to-Z audit of 1.8.6: every finding with the exact fix, 4-phase roadmap, and how to make money with it. |
| `docs/ROADMAP.md` | Earlier audit/roadmap from main. Prefer `AUDIT.md` for the clone that actually implements the fixes. |
| `AndroidManifest.xml`, `src/`, `assets/`, `res/` | 1.8.6 source at the repo root (from main). Same generation as `original/`. |
| `meridian-clone/` | Earlier 1.8.6 copy from main, still the original code. |
| `meridian-github/` | Earlier 1.8.6 copy from main, still the original code. |
| `meridian-source.zip` | Untouched archive the extracts came from. |

MIT licence; see `LICENSE`. The signing key is not included.

People may study it, share it, change it, and fork it. A local “thank you” scrape is not a
business — `AUDIT.md` §5 is the chargeable model (merchant-of-record checkout + offline
signed keys).
