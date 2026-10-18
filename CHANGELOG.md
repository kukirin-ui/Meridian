# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Battery screen: a live battery icon on the right, drawn as transparent PNG layers with no panel behind it. Five stacked bars fill with the real charge level (the top lit bar can be partial), the bolt shows and a dashed ring turns slowly while the phone is charging, and the colours move to amber below 20% and red below 10%. The ring stays still when Motion is off or the system asks for reduced motion.
- `tools/battery-icon/render.py` renders the battery artwork from its SVG source to transparent PNGs at 512 px and 1024 px (2x) in `assets/www/img/battery/`.

### Changed
- Battery screen: charge level, state, and temperature sit in a left column next to the new graphic, and the state label now updates live with the other readings.

### Removed
- The `>_` mark in the Battery window title bar. Other screens keep it.
- The old battery orb with the striped progress bar under the Battery readings, with its styles. The readings rows now sit right under the new icon.

## [2.3.29-oss] - 2026-10-13

### Changed
- **Meridian is now open source under the MIT License.** The repository was private during development and is public again now that v1 is finished.
- All features are available to everyone: no licence key, trial, or paywall. Pro and Premium are the same feature level; Premium is optional support aimed at businesses.
- "Plans" is now **Support & Plans**, with a **Donate** button (same checkout link as before, now optional) and **Get support** (email) and **Source code** buttons.
- About screen and Terms now show the MIT licence.
- Removed all paywall, trial, preview, lock, and licence-key code paths (JS and Java bridge), including the imagined-preview mode.

### Added
- Mesh v2 bundle (`mesh-v2/`: worker, router, Add links, `my-knowledge/`), sandbox sets, and the `setTimeout(meshBoot, 0)` startup fix from the v4 build.
- LICENSE (MIT), CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, CHANGELOG, and GitHub issue templates.

### Removed
- Licence-key verification, signing tools (`tools/license/`), price list (`PRICES.md`), the old proprietary audit (`AUDIT.md`), and the stale `meridian.zip` source archive.
