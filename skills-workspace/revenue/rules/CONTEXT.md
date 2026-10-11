# Revenue · jurisdiction rule packs (routing)

Same ICM mechanism as skill folders: each pack has `CONTEXT.md`, one card in `references/`, and `output/`.  
These are **not** L/G/U/R skill IDs — they are load-on-demand Revenue guardrails.

| Pack | Opposite / role | Path | Load when |
|---|---|---|---|
| **US** | Logic-heavy forms & withholdings | `us/` | US-locale billing/compliance |
| **EU / HR** | Logic-heavy VAT schemes + Croatia home | `eu-hr/` | EU or Croatia locale |
| **Cross-border** | **Bridge** — uses US + EU packs, adds treaties/reporting | `crossborder/` | Fact pattern spans US ↔ EU/HR |

**Wiring to the three skill trees**
- **Tree 1 (Logic / Code):** may *implement* invoicing/metering code; it does **not** own tax facts — read these packs.
- **Tree 2 (Imagination / Graphics):** may produce invoice/PDF UX; it does **not** invent rates.
- **Tree 3 (Understanding / Bridge):** may *explain* a cross-border fact pattern using **both** halves — still cite these packs, no improvised law.
- **Revenue (R-12, R-13, R-16):** owns and gates money; packs live here.

**Shared law:** one home per fact · no hallucinated forms · R-16 before any filing or payment · status STUB until bibliography-backed.
