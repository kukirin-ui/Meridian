# US Business Structure, Federal Income, Payroll & Sales Tax

**Evidence:** STUB / UNVERIFIED (outline — not yet source-backed)  
**Tree:** Revenue Layer · jurisdiction pack (not a skill ID)  
**Load when:** US billing / compliance questions  
**Pairs with:** R-12 · R-13 · R-16

## Why it exists / matters
US tax and payroll rules are form- and nexus-specific. Loading them into every prompt wastes context and invites invented rates. A US-only pack keeps the small model fast and honest.

## How it is made / works
**Logic half:** entity class → return form → accounting method → withholdings → information returns.  
**Imagination half:** N/A for rates — do not “improvise” thresholds. Use checklists and stop for humans.

### A. Structure & federal income (TODO · source)
| Path | Outline claim | Verify |
|---|---|---|
| Pass-through | Sole prop, partnership, LLC (default), S-corp — one layer of tax | IRS entity classification |
| C-corp | Entity tax + shareholder tax (double taxation) | IRC Subchapter C |
| Filings | 1120 / 1120S (corp); 1065 (partnership info); Schedule C on 1040 (sole prop) | Current year instructions |
| Accounting | Cash vs accrual; outline claim ~$1M–$10M gross-receipts band may allow cash | Current IRC / Rev. Proc. |
| Status letters | 6201 (individual/sole prop); 6574 (business) | Only after a real letter exists |

### B. Employment, payroll & sales tax (TODO · source)
- Withholdings: W-4 federal income; FICA (SS + Medicare); FUTA (Form 940).
- SALT: SUTA; sales tax — outline claim 45 states + DC levy, each with rates/nexus.
- Information returns: 1099 family for payments to others (confirm series + thresholds for the year).

## What you can change
- Which US states are in scope; employee vs contractor classification; accrual election — each changes filings.
- Never change: human approval gate (R-16).

## How it is learned
Modes M5 M6: practise on public IRS worksheets; every claim needs a dated primary link before leaving STUB.

## Proof / signs
- Every rate/form/threshold has a bibliography entry; agent refuses to invent Letter outcomes; R-16 logged before any filing.

## Sources
- TBD — IRS, SSA, state DOR only. No invented citations.
