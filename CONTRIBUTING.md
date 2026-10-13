# Contributing to Meridian

Thanks for your interest in Meridian!

## Ways to help

- Report bugs and request features in [GitHub Issues](https://github.com/kukirin-ui/Meridian/issues) using the templates.
- Improve docs, translations, recipes, and skill cards.
- Send pull requests for fixes and features.

## Development

1. Fork the repo and create a branch from `main` (`fix/...`, `feat/...`).
2. Build with `./gradlew assembleDebug` (JDK 17 + Android SDK).
3. Most UI work happens in `app/src/main/assets/www/index.html`; the Java bridge is `app/src/main/java/com/meridian/shell/MainActivity.java`.
4. Test on a real phone with Termux installed, and check the browser console for errors.
5. Keep commits focused and describe *why* in the message.

## Pull requests

- Explain what changed and how you tested it.
- Do not commit secrets, keystores, API keys, or `.env` files.
- Commands that change the phone must stay visible to the user before they run.
- By contributing you agree your work is released under the [MIT License](LICENSE).

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md).
