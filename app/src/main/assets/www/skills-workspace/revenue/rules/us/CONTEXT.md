# CONTEXT — US jurisdiction pack

## Inputs
- US entity type, tax year, employees/contractors, sales-tax nexus states

## Process
1. Load only when the question is US-locale billing or compliance.
2. Walk the card in `references/rules_us.md` (structure → filings → payroll → SALT → 1099).
3. Mark every concrete threshold as STUB until sourced.
4. Hand off money/filings to human (R-16). Never file from this pack alone.

## Outputs
- Checklist + form topics for a human/CPA; no invented Letter 6201/6574 results
