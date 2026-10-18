# Meridian

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build APK](https://github.com/kukirin-ui/Meridian/actions/workflows/build-apk.yml/badge.svg)](https://github.com/kukirin-ui/Meridian/actions/workflows/build-apk.yml)

**Meridian is an open source Android command deck for Termux.** Live phone readings, curated shell recipes, a local AI agent mesh (llama.cpp), and a voice-driven assistant, all running on your own phone. No account, no server, no paywall.

> This repo was private during development; now that v1 is finished it is public again.

## Features

Every feature is available to everyone. There is no licence key, no trial, and no locked page.

- **Dashboard** — live CPU, GPU, RAM, battery, thermals, network, and storage readings from the device.
- **Termux bridge** — commands run in Termux on the same phone through `RunCommandService`. You read a command before it runs.
- **Recipes** — Setup, Doctor, Build, Advanced tuning, and a command library (packages, Python, Node, git, llama.cpp, and more).
- **Plan** — describe a goal; an AI model (your own API key or a local model) drafts the shell steps for you to review.
- **API platforms** — up to 32 OpenAI-compatible providers (Groq, Gemini, OpenRouter, OpenAI, Anthropic, xAI, custom). Keys are encrypted with the Android Keystore and never leave the phone except to the provider you chose.
- **Local AI mesh (mesh v2)** — llama.cpp GGUF models on the phone, a worker (`worker.py`), task queue, knowledge folder (`my-knowledge/`), and **Add links** to feed public pages into the knowledge base.
- **Skill trees** — Code, Graphics, Understanding, and Revenue skill cards in `skills-workspace/` used as context for the agent.
- **Linux PC** — install and open Debian or Ubuntu with `proot-distro`.
- **Desk & scheduler** — run a whole plan, schedule recipes, UFS storage class, Markdown hardware report.
- **Shizuku / rish** — optional: install APKs and read logcat through Shizuku's `rish`, with a fingerprint check for risky actions.
- **Look** — OLED / dark / light themes, accent colours, motion, seven UI languages.
- **Desktop companion** — `node desktop/meridian-desktop.mjs` for local readings on a computer.

## Pro and Premium

Pro and Premium have **the same feature level**, and both are free. Premium is aimed at businesses and teams that want to back the project and get priority help. Paying is optional and never changes what the app can do.

- **Donate / Premium support:** the in-app **Support & Plans** screen has a **Donate** button that opens the project's checkout page.
- **Support:** the **Get support** button emails the developer (Pozun.alen96@gmail.com). Bugs and feature requests are also welcome in [GitHub Issues](https://github.com/kukirin-ui/Meridian/issues).

## Install

### From a build

1. Open the [Build APK workflow](https://github.com/kukirin-ui/Meridian/actions/workflows/build-apk.yml) and download the latest debug APK artifact, or grab an APK from [Releases](https://github.com/kukirin-ui/Meridian/releases).
2. Copy it to the phone, open it, and allow install from that source.

### Termux setup

1. Install [Termux](https://f-droid.org/packages/com.termux/) and [Termux:API](https://f-droid.org/packages/com.termux.api/) from F-Droid (not Play).
2. In Termux:
   ```bash
   echo "allow-external-apps=true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
3. Open Meridian, press **Connect**, allow the Termux permission, then run **Setup / Doctor**.

### Build from source

Android Studio, or JDK 17 + Android SDK:

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Release builds need your own signing config (keystore paths and passwords from environment variables; never commit them).

## Repository layout

| Path | Role |
|---|---|
| `app/` | The Android app: WebView host and Termux bridge (`MainActivity.java`), UI in `app/src/main/assets/www/index.html` |
| `app/src/main/assets/www/mesh-v2/` | Local agent mesh v2 bundle (worker, router, instructions) |
| `app/src/main/assets/www/skills-workspace/` | Skill cards bundled into the app |
| `desktop/` | Desktop companion |
| `docs/` | Skill tree docs and historical roadmap |
| `tools/qa/` | Static UI audit and sandbox checks |
| `meridian_sandbox/` | Minimal sandbox variant of the app |

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Report security issues privately as described in [SECURITY.md](SECURITY.md). Changes are listed in [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE) © 2026 Alen Požun (kukirin-ui)
