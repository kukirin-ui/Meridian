# VERIFY: full build (124 skills)

Source: the PDF *Three Skill Trees and a Revenue Layer for Self-Learning LLM Agents* (55 pages). Checked against two separate pdftotext extractions (layout mode and raw mode). Date: 2026-10-11.

## Counts

| Tree | Folder | Skill folders | Groups | Bibliography entries |
|---|---|---|---|---|
| Code Master (left) | `agents/1-code/` | 36 (L-01 to L-36) | 5 tiers | 96 |
| Graphics Master (right) | `agents/2-graphics/` | 36 (G-01 to G-36) | core + 6 rings | 79 |
| Understanding Master (bridge) | `agents/3-understanding/` | 36 (U-01 to U-36) | 7 rungs (A to G) | 121 |
| Revenue Layer (shared) | `revenue/` | 16 (R-01 to R-16) | 5 stages | 44 |
| **Total** | | **124** | | |

Nothing was deferred. Revenue is included.

Each skill folder holds exactly `CONTEXT.md`, `references/<ID>.md` (one card) and `output/` (empty, `.gitkeep`). Each tree has one routing `CONTEXT.md` and one `bibliography.md`. `THREE_TREES.md` is the overview.

## Checks run (all pass)

1. Every card has 6 fields, with the field names used by its PDF part: 124/124.
2. All card text matches the raw-mode extraction, compared in 8-word chunks: 0 mismatches.
3. Completeness: the words on each card match the words of that card in the PDF. The only extra PDF words were section headings and part intros. No text is missing and none was added: 124/124.
4. Title and evidence grade match the PDF card header: 124/124.
5. Each card sits in the tier, ring, rung or stage given by the PDF tree tables and section headings: 124/124.
6. Every [n] on a card is in that tree's `bibliography.md`, and that entry's "Cited in" names the card: 0 failures. No tree bibliography lists an L-, G-, U- or R- source that its cards do not cite.
7. Bibliography entries match the PDF in 6-word chunks: 0 mismatches.
8. Size: the longest card is 33 lines (limit about 200). The longest skill `CONTEXT.md` is 20 lines (limit about 40). Tree routing files are 48 to 72 lines.
9. No PDF page headers, footers or page numbers are left in any `.md` file.

## Corrections made in this pass

- Bibliography entries [41], [91], [134], [181], [224], [265] and [308] had a PDF page header joined onto the end of the entry (form-feed artefact). Fixed. The earlier Ideas-tranche bibliography had this artefact in the L-tree entries, and the rebuilt file no longer does.
- L-01 to L-16 were regenerated in the same format as the rest, with Sources as a list. Their field text is unchanged and was checked against the old files.
- The old `VERIFY.md` (Ideas tranche only) was retired. This file replaces it.

## Formatting only (wording not changed)

- In Understanding cards, "Logic half:" and "Intuition half:" are bold and start separate paragraphs.
- In Graphics and Revenue cards, "Failure mode:" and "Guardrail:" are bold and start their own paragraph.
- Sources are listed as short [n] citations on the card. Full entries are kept only in the tree's `bibliography.md`.

## Notes

- [39] reads "Beyer et al.." with a double full stop in the PDF. It was kept as printed.
- [106], [188], [189] and [190] are the "Sources for the control rules" in Part III. They are in the Understanding bibliography and are named in its routing file.
- The left / right / bridge labels in `THREE_TREES.md` and the routing headers are a framing. They do not add or change skills.
- `THREE_TREES.md` lists Part 0 sources [1] to [26], which back the learning modes table.
- The step-2 wording in each skill's `CONTEXT.md` comes from the PDF: Part I for the checker, Appendix B for the judge or panel, and the Part IV / R-16 approval rule for Revenue.
- Appendix B also suggests `revenue/approvals.md` and a `stages/` loop folder. These were not created, because the same facts already have one home in R-16 and in each `CONTEXT.md`.
- The PDF says most source identifiers were not opened during its preparation. This build copied them from the PDF and did not check the links online.
