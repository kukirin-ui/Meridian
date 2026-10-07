# Meridian prices

The price for sale is **Pro, €15 once**.

Thirty sales are €450. The target date is 19 October 2026.

| Tier | Price | For sale |
|---|---|---|
| Free | €0 | Yes. Dashboard, Hardware, System, Battery, one console, Setup, Doctor, Commands, Links, and read-only Files. |
| Pro | €15 once | Yes. Build recipes, Plan (including a local model; you run each step), Tweaks, extra console sessions, file write, and Linux PC. |
| Premium | — | **Not for sale yet.** Designed, and switched off in the app until there are paying Pro users. |

An earlier draft showed a pay-what-you-want span of €1–€1,000,000 for Pro and €5–€5,000,000 for Premium. That is not the price.

Checkout is the existing `PAY_URL`. The licence is an offline key that starts with `MERIDIAN-` and has `"sku":"pro"`.

```bash
node tools/license/sign.mjs --sku pro --ref ORDER
```

The words `paid`, `full`, and `unlocked` are not keys and do not unlock the app.
