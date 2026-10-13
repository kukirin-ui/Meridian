# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
