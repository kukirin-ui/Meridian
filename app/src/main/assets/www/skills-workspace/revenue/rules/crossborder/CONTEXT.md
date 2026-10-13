# CONTEXT — US ↔ EU/HR cross-border pack

## Inputs
- Tax residence of payer and payee; related-party?; payment type (royalty, dividend, service, goods)

## Process
1. Load when the fact pattern spans US and EU/HR.
2. Use `references/rules_crossborder.md` (reporting → treaty docs → TP → Pillar Two gate).
3. Pull US-only or EU-only details from sibling packs — do not duplicate.
4. R-16 before withholding remittance or foreign-entity filing.

## Outputs
- Cross-border checklist; explicit Pillar Two N/A vs escalate
