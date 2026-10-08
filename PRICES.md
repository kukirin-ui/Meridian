# Meridian prices

The consumer price is **Pro, €15 once**. Each purchase includes **two Pro keys**: one for the buyer, and one they can give to a friend.

| Tier | Price | What it keeps |
|---|---|---|
| Free | €0 | Dashboard, Hardware, System, Battery, Network Security, Plan with one saved API platform, one console, Setup, Doctor, Commands, Links, and read-only Files. |
| Pro | €15 once, two keys | Every listed AI platform (eight saved), custom themes, advanced readings, build recipes, tweaks, extra consoles, file write, and Linux PC. |
| Premium | €15 and up | A separate business key. UFS storage class, up to 32 API platforms, batch scripts, the scheduler, and the desktop monitor. |

An earlier draft showed a pay-what-you-want span of €1–€1,000,000 for Pro and €5–€5,000,000 for Premium. That is not the price.

Checkout is the existing `PAY_URL` and it sells **Pro only**. The button is **Buy Pro · €15**. Premium is not a second button on that URL. A Premium licence is an offline key with `"sku":"premium"`.

```bash
node tools/license/sign.mjs --sku pro --ref ORDER --copies 2
node tools/license/sign.mjs --sku premium --ref ORDER
```

The Pro command prints two different keys (refs `ORDER-1` and `ORDER-2`). Default `--copies` is 1.

The words `paid`, `full`, and `unlocked` are not keys and do not unlock the app.
