# Security Policy

## Supported versions

Only the latest release on `main` receives security fixes.

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Use GitHub's private vulnerability reporting (repository **Security → Report a vulnerability**) or email the maintainer at Pozun.alen96@gmail.com.

Include steps to reproduce, the affected version, and the impact. You should get a reply within 7 days.

## Scope notes

- API keys are stored encrypted with the Android Keystore on the device.
- Meridian runs commands in Termux on the same phone; there is no Meridian server.
- Never commit keystores, tokens, or passwords to this repository.
