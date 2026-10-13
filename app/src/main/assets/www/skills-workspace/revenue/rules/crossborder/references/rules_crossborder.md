# US ↔ EU / Croatia Cross-Border Rules

**Evidence:** STUB / UNVERIFIED  
**Tree:** Revenue Layer · jurisdiction pack  
**Load when:** parties or entities span US and EU/HR  
**Pairs with:** `us/` · `eu-hr/` · R-13 · R-16

## Why it exists / matters
Cross-border mistakes (wrong treaty rate, missed 5471/8865, fake Pillar Two) are expensive. This pack is the bridge: **US logic forms + EU VAT logic**, without stuffing both full packs into every prompt.

## How it is made / works
**Logic half (US):** foreign corp/partnership reporting; withholding defaults; GILTI as specialist flag.  
**Logic half (EU):** OSS/IOSS pointers via `eu-hr/` — not copied here.  
**Bridge rule:** reduced treaty rate only after W-8BEN / tax-residence certificate is on file.

### Foreign reporting (TODO · source)
- Form **5471** — certain interests in foreign corporations.
- Form **8865** — foreign partnerships.
- FATCA / FBAR — confirm who must file and thresholds.

### Withholding & treaties (TODO · source)
- Royalties/dividends to foreign persons: withhold unless treaty reduces.
- Collect **W-8BEN** or certificate of tax residence **before** reduced rates.

### Transfer pricing & minimum tax (TODO · source)
- Related-party cross-border: arm’s-length documentation (jurisdiction-specific).
- OECD **Pillar Two** ~**15%**: outline claim mainly for groups over **€750M** revenue — default for micro agents = **out of scope**; escalate if unsure.
- US **GILTI** (and successors): specialist territory; stub only.

## What you can change
- Related-party vs third party; payment character; treaty country — each changes docs.
- Never apply Pillar Two or GILTI to a micro seller by default.

## How it is learned
M5 M6: three scenarios (US LLC pays EU contractor; US person owns EU GmbH; US SaaS B2C into EU) → checklist only, then human review.

## Proof / signs
- No treaty rate without form on file; Pillar Two never auto-applied to small agents; R-16 logged.

## Sources
- TBD — IRS, OECD, EU Commission primary links only.
