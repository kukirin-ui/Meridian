# Meridian prices

Pay what you want, once, inside the range. The app does not charge the top of the range by itself. Checkout is the same merchant for Pro and Premium. The licence is an offline key that starts with `MERIDIAN-`.

| Tier | Price shown | What you get |
|---|---|---|
| Free | €0 | Dashboard, Hardware, System, Battery, one console, Setup, and Doctor. |
| Pro | choose €1 – €1,000,000 once | Build recipes, Plan (you run each step), Tweaks, extra console sessions, file write, and Linux PC install/open. |
| Premium | choose €5 – €5,000,000 once | Everything in Pro, plus Run the plan, a job scheduler, and a hardware share card with no “Made with Meridian” footer (and copy as Markdown). |

Premium uses SKU `premium`. Pro uses SKU `pro`. A Premium key includes every Pro feature.

Issue a key after the order:

```bash
node tools/license/sign.mjs --sku pro --ref ORDER
node tools/license/sign.mjs --sku premium --ref ORDER
```

The words `paid`, `full`, and `unlocked` are not keys and do not unlock the app.
