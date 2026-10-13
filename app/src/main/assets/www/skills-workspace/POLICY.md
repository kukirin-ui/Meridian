# ICM policy

ICM is mandatory for every write, edit, add, improve, delete, skill card, agent output, and packaged zip. Jake Van Clief's Interpretable Context Methodology (2026) is the layout. The folder shape is the MDBOTS three trees in `THREE_TREES.md`.

## Layout

One skill folder:

- `CONTEXT.md` with the headings Inputs, Process, and Outputs
- `references/<ID>.md`, the one card
- `output/` for practice results and logged lessons

One tree, once:

- one routing file, `CONTEXT.md` at the tree root
- one `bibliography.md`

Trees:

- Code Master: `agents/1-code/` (L-01 to L-36)
- Graphics Master: `agents/2-graphics/` (G-01 to G-36)
- Understanding Master: `agents/3-understanding/` (U-01 to U-36)
- Revenue layer: `revenue/` (R-01 to R-16). This is the shared pipeline, not a fourth tree.

## Six fields together

Keep all six on the one card. Do not split them. Heading names follow the tree:

- Code: Why it exists; How it is made; What you can change; How it is learned; Proof of mastery; Sources
- Graphics: Why it matters; How it works; Ways to vary it; How it is learned; Signs of mastery; Sources
- Understanding: Why it exists; How it is made (both halves); What you can change; How it is learned; Bridge test; Sources
- Revenue: Why it matters; How it works; What you can change; How it is learned; Proof it works; Sources

## Facts

Do not invent a fact, a file, a path, or a source. Label the unknown. Sources are the numbered entries already in that tree's `bibliography.md`. Confirm a source before adding it. Unchecked output is a draft.

## Roster

Set A, in `~/models`, is two Qwen 0.5B files (`cpu_scout_0b5.gguf`, `cpu_scribe_0b5.gguf`), one Qwen 3B (`mid_3b.gguf`), and one Qwen 7B (`gpu_lead_7B.gguf`). Set B, in `~/models2`, is `qwen2.5-0.5b-instruct-q4_k_m.gguf` for Scout and Scribe, `microsoft_Phi-4-mini-instruct-Q4_K_M.gguf` for Mid, and `Qwen3-4B-Q4_K_M.gguf` for Lead. The healer keeps both sets. Thermal policy stays pause-69-resume-49.

## Personas

Scout, Scribe, Mid, and Lead obey `agent.md`.

## Home folder

An agent may work in the Termux home folder and workspace, including outside `~/meridian-sandbox`. A new file is written immediately. Deleting or replacing an existing file outside the sandbox shows the change and waits for a fingerprint. install apk shows the file and waits for a fingerprint, then Shizuku installs it.

## Healer

If `CONTEXT.md` is missing, a skill sits in the wrong tree, `mesh.json` paths are broken, or a prompt is missing, the healer restores this layout and both mesh sets without asking. Agents may run it with `TOOL HEAL`.
