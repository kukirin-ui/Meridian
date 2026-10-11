# Code Master — routing (`agents/1-code`)

**Agent 1 · Code Master (logic)** · Left · Logic tree · Engine: verifiability

Agent 1 is the Code Master: logic applied to every stage of software work. Its tiers run from the ground (read, write, edit, delete, shell, history, builds) through craft, quality and specialisation to mastery (learning anything new, autonomy, depth, proof, architecture). Wherever a checker exists (tests, an executor, a type checker, a proof kernel, a source file) the agent uses it and treats unchecked output as a draft. Typical settings: low sampling temperature, tests as the reward, stage-gated work, human approval for deletion and deployment.

Main failure modes: plausible but wrong code, over-trust in self-verification, silent destructive edits, insecure code, and context overload. Each skill names its own limits.

**Shape.** Prerequisite ladder: each tier needs the one before it.

**Where to start.** L-01 to L-07, then L-31 and L-32.

## Groups

| Group | Skills | Folder |
|---|---|---|
| Tier 0 · Ground | L-01 to L-07 | `tier-0-ground/` |
| Tier 1 · Craft | L-08 to L-14 | `tier-1-craft/` |
| Tier 2 · Quality | L-15 to L-22 | `tier-2-quality/` |
| Tier 3 · Specialisations | L-23 to L-30 | `tier-3-specialisations/` |
| Tier 4 · Mastery | L-31 to L-36 | `tier-4-mastery/` |

## Skills

| ID | Skill | Evidence | Folder |
|---|---|---|---|
| L-01 | Exact reading of code and files | B | `tier-0-ground/L-01-exact-reading-of-code-and-files/` |
| L-02 | Writing code from a specification | B | `tier-0-ground/L-02-writing-code-from-a-specification/` |
| L-03 | Surgical editing | B | `tier-0-ground/L-03-surgical-editing/` |
| L-04 | Reversible deletion and cleanup | A | `tier-0-ground/L-04-reversible-deletion-and-cleanup/` |
| L-05 | Shell and terminal fluency | A | `tier-0-ground/L-05-shell-and-terminal-fluency/` |
| L-06 | Version control mastery | A | `tier-0-ground/L-06-version-control-mastery/` |
| L-07 | Environments, dependencies and builds | A | `tier-0-ground/L-07-environments-dependencies-and-builds/` |
| L-08 | Algorithm design and analysis | A | `tier-1-craft/L-08-algorithm-design-and-analysis/` |
| L-09 | Data structures and representations | A | `tier-1-craft/L-09-data-structures-and-representations/` |
| L-10 | Language fundamentals and paradigms | A | `tier-1-craft/L-10-language-fundamentals-and-paradigms/` |
| L-11 | Python mastery | B | `tier-1-craft/L-11-python-mastery/` |
| L-12 | C and C++ mastery | B | `tier-1-craft/L-12-c-and-cpp-mastery/` |
| L-13 | JavaScript and TypeScript | B | `tier-1-craft/L-13-javascript-and-typescript/` |
| L-14 | Rust and Go | B | `tier-1-craft/L-14-rust-and-go/` |
| L-15 | Specification to tests | A | `tier-2-quality/L-15-specification-to-tests/` |
| L-16 | Debugging method | B | `tier-2-quality/L-16-debugging-method/` |
| L-17 | Reading logs, traces and debuggers | B | `tier-2-quality/L-17-reading-logs-traces-and-debuggers/` |
| L-18 | Refactoring and legacy code | A | `tier-2-quality/L-18-refactoring-and-legacy-code/` |
| L-19 | Code review and design critique | B | `tier-2-quality/L-19-code-review-and-design-critique/` |
| L-20 | Performance engineering | A | `tier-2-quality/L-20-performance-engineering/` |
| L-21 | Security engineering (defensive) | B | `tier-2-quality/L-21-security-engineering-defensive/` |
| L-22 | Static analysis and type systems | A | `tier-2-quality/L-22-static-analysis-and-type-systems/` |
| L-23 | Back-end services and APIs | B | `tier-3-specialisations/L-23-back-end-services-and-apis/` |
| L-24 | Front-end engineering | B | `tier-3-specialisations/L-24-front-end-engineering/` |
| L-25 | Mobile and Android development | B | `tier-3-specialisations/L-25-mobile-and-android-development/` |
| L-26 | Systems, operating systems and networking | A | `tier-3-specialisations/L-26-systems-operating-systems-and-networking/` |
| L-27 | Databases and distributed systems | A | `tier-3-specialisations/L-27-databases-and-distributed-systems/` |
| L-28 | Data, machine learning and local LLMs | B | `tier-3-specialisations/L-28-data-machine-learning-and-local-llms/` |
| L-29 | DevOps, CI/CD and observability | B | `tier-3-specialisations/L-29-devops-ci-cd-and-observability/` |
| L-30 | Compilers, interpreters and domain languages | A | `tier-3-specialisations/L-30-compilers-interpreters-and-domain-languages/` |
| L-31 | Learning any new language or framework fast | B | `tier-4-mastery/L-31-learning-any-new-language-or-framework-fast/` |
| L-32 | The self-learning loop for code | B | `tier-4-mastery/L-32-the-self-learning-loop-for-code/` |
| L-33 | Agentic coding and ICM workspaces | B | `tier-4-mastery/L-33-agentic-coding-and-icm-workspaces/` |
| L-34 | Specialist depth | B | `tier-4-mastery/L-34-specialist-depth/` |
| L-35 | Formal verification and proof | A | `tier-4-mastery/L-35-formal-verification-and-proof/` |
| L-36 | Large-scale design and architecture | A | `tier-4-mastery/L-36-large-scale-design-and-architecture/` |

## Files

- `bibliography.md` — the one home for this tree's source entries.
- Each skill folder: `CONTEXT.md` (Inputs, Process, Outputs), `references/<ID>.md` (the six-field card), `output/` (practice results and lessons).
- Evidence grades: A established · B benchmarked · C emerging or contested.
