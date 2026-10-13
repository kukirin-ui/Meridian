# Three Skill Trees and a Revenue Layer

Source: the PDF *Three Skill Trees and a Revenue Layer for Self-Learning LLM Agents* (prepared October 2026). 124 evidence-graded skill cards: Code 36 · Graphics 36 · Understanding 36 · Revenue 16.

## The shape: left, right, bridge

Two opposite trees run on the same mechanism (one card, six fields, the same learning loop). The third tree joins both halves.

| Side | Tree | Folder | PDF shape and engine | How mastery is judged (PDF) |
|---|---|---|---|---|
| **Left** | Code Master (logic) | `agents/1-code/` | Ladder of 5 tiers. Engine: verifiability. | Proof of mastery: tests, executors, proofs, sources |
| **Right** | Graphics Master (imagination) | `agents/2-graphics/` | Core plus 6 rings. Engine: imagination, taste, craft. | Signs of mastery: blind readers, panels, audits, reproducible settings |
| **Bridge** | Understanding Master (logic and imagination) | `agents/3-understanding/` | Helix of 7 rungs. Engine: logic and imagination together. | Bridge test: the logic half and the imagination half agree |
| Shared | Revenue Layer | `revenue/` | Pipeline of 5 stages. Engine: honest value, checked delivery. | Proof it works: measured results, with guardrails |

- **Left: logic.** Wherever a checker exists (tests, an executor, a type checker, a proof kernel, a source file), the agent uses it and treats unchecked output as a draft.
- **Right: imagination.** Mastery is judged by readers, panels and technical checks, so each card lists signs of mastery with its failure mode.
- **Bridge: both.** Every Understanding card states a logic half and an imagination (intuition) half and ends with a bridge test that shows the two agree.
- **Revenue: shared pipeline.** It is not a fourth tree. All three agents use the same five stages (Find, Win, Deliver, Get paid, Grow and protect). R-15 and R-16 guard every stage.

The left / right / bridge labels are a framing on top of the PDF. Every skill card, grade and source comes from the PDF unchanged.

## What each part contains

| Part | Tiers / rings / rungs / stages | Skills |
|---|---|---|
| Code Master | Tier 0 Ground (L-01–L-07) · Tier 1 Craft (L-08–L-14) · Tier 2 Quality (L-15–L-22) · Tier 3 Specialisations (L-23–L-30) · Tier 4 Mastery (L-31–L-36) | 36 |
| Graphics Master | Core (G-01–G-03) · Ring 1 Pixel and light (G-04–G-09) · Ring 2 Mark and type (G-10–G-14) · Ring 3 Image and composition (G-15–G-20) · Ring 4 System and interface (G-21–G-25) · Ring 5 Motion and world (G-26–G-32) · Ring 6 Machine and unknown (G-33–G-36) | 36 |
| Understanding Master | A Foundations (U-01–U-06) · B Physics (U-07–U-12) · C Cosmos (U-13–U-17) · D Life (U-18–U-24) · E Mind and society (U-25–U-28) · F Communication and learning (U-29–U-34) · G Frontier and control (U-35–U-36) | 36 |
| Revenue Layer | 1 Find (R-01–R-04) · 2 Win (R-05–R-08) · 3 Deliver (R-09–R-11) · 4 Get paid (R-12–R-13) · 5 Grow and protect (R-14–R-16) | 16 |

## Where to start

- **Agent 1:** L-01 to L-07, then L-31 and L-32.
- **Agent 2:** G-01 to G-03, then Ring 1.
- **Agent 3:** U-03, U-35 and U-36 first, then the rung you need.
- **Revenue layer:** R-16 before any other skill is used with real money.

## Shared rules (ICM layout)

Each tree lives in an ICM workspace (Jake Van Clief's Interpretable Context Methodology [15]): plain-text folders and short files. Each fact has one home.

1. **One card per skill.** One `.md` per skill, with all six fields on that card.
2. **One folder per skill:**
   - `CONTEXT.md`: the stage contract (Inputs, Process, Outputs)
   - `references/<ID>.md`: the skill card
   - `output/`: practice results and logged lessons
3. **Per tree, once:** one routing file (`CONTEXT.md` at the tree root) and one `bibliography.md`.
4. **Size:** stage files stay short, and references stay under about 200 lines (the ICM checklist). An agent loads one card at a time.
5. **Learning loop for each skill (Appendix B):**
   - practice
   - verify or judge (a checker for logic, a blind judge or panel for imagination)
   - reflect
   - store the lesson in `output/`, and add it to `references/` only after verification

```
MDBOTS/
  THREE_TREES.md            this overview
  agents/
    1-code/                 Left: ladder (tier-0-ground … tier-4-mastery)
    2-graphics/             Right: rings (core, ring-1-pixel-light … ring-6-machine-unknown)
    3-understanding/        Bridge: helix (rung-a-foundations … rung-g-frontier)
  revenue/                  Shared pipeline (1-find … 5-grow-protect)
```

## The six card fields

| Field | Question it answers |
|---|---|
| Why it exists / matters | What problem the skill solves; what breaks without it. |
| How it is made / works | The mechanism: steps, parts and reasoning, including known limits. In Agent 3 it names the logic half and the imagination half. |
| What you can change | The levers: parameters and design choices and what each trades off. |
| How it is learned | The learning modes (M1 to M8) and a concrete practice protocol. |
| Proof / signs / bridge test | A test that shows the skill is real, with the failure mode in Agent 2 and the guardrail in the Revenue Layer. Numeric targets are suggestions, not published standards. |
| Sources | Numbers refer to the tree's `bibliography.md`. |

## The eight learning modes

| Mode | Name | What changes | Training needed | Typical use | Sources |
|---|---|---|---|---|---|
| M1 | Pretraining | Weights | Yes, very large | Broad language, code and world knowledge | [1] [2] [3] |
| M2 | Supervised and instruction tuning | Weights (adapters possible) | Yes, modest | Formats, styles and skills from examples | [4] [5] [6] |
| M3 | Reinforcement with verifiable rewards | Weights | Yes | Code, math, anything with a checker | [7] [8] [9] |
| M4 | Preference learning | Weights | Yes | Helpfulness, style, safety, taste | [10] [4] [11] [12] |
| M5 | In-context learning and skill files | Context only | No | Instant specialisation: instructions, exemplars, CONTEXT files | [13] [14] [15] |
| M6 | Retrieval, tools and memory | Context and external state | No | Current facts, exact computation, long-term notes | [16] [17] [18] [19] |
| M7 | Self-feedback and self-play | Context or weights | Optional | Iterative improvement where a check or judge exists | [20] [21] [22] [8] [23] |
| M8 | Learning from others | Weights or context | Optional | Distillation, debate, mentors | [24] [25] [26] |

## Evidence grades

- **A:** established theory, standard or textbook knowledge.
- **B:** results benchmarked in humans or models (peer reviewed or reproducible).
- **C:** emerging, contested or practitioner-originated.

Grades describe the evidence behind the skill, not how good the agent is at it.

## Rules that apply to every skill

- **Verify before trusting.** Self-generated output without an outside check is not evidence; self-correction alone is unreliable (L-32).
- **Reversible by default.** Deletion follows L-04: dry run, snapshot, confirm, verify.
- **Defensive and lawful.** Security skills are for systems you are authorised to work on; no malware, no deception, no unauthorised access.
- **Education, not advice.** Finance, tax, legal, health and chemistry safety topics are taught as method; decisions stay with qualified humans.
- **Respect originality and rights.** Cite, quote sparingly, keep licence records, check outputs for copies (G-35).
- **Label the unknown.** Mark claims as established, supported, speculative or unknown, with dates (U-35).
- **Approve money.** No payment, contract, new account or public statement without human approval (R-16).

## Four points that are easy to get wrong

1. **'Unlimited' is a direction, not a property.** The trees raise the ceiling and give a method for entering anything new (L-31, G-36, U-30), but no document makes a model able to do everything.
2. **Heavy science needs honesty about the unknown.** Quantum interpretations, dark matter, dark energy and the origin of life are open or contested (U-35).
3. **The revenue layer does not promise income.** Most online ventures earn little or nothing, and platforms ban some automation. Human approval on money (R-16).
4. **The trees are files, not code.** They are meant to live in an ICM workspace: plain-text folders, one skill card per stage, and a human who can edit any stage file.

## Source status (from the PDF)

The PDF's author opened the ICM paper and repository, the Absolute Zero paper and the OMNI paper. The other identifiers are canonical but were not each opened. Confirm any source before citing it in a thesis. Statements about frontier science must be re-checked against current papers. Legal and tax notes use EU sources and name Croatia where relevant; Croatian national rules were not checked in Croatian sources. They are not legal or tax advice.

## Part 0 sources (learning modes)

**[1]** Vaswani et al. 2017. Attention Is All You Need. NeurIPS 2017.  
<https://arxiv.org/abs/1706.03762>  
Cited in: Part 0, L-28

**[2]** Kaplan et al. 2020. Scaling Laws for Neural Language Models. arXiv preprint.  
<https://arxiv.org/abs/2001.08361>  
Cited in: Part 0

**[3]** Hoffmann et al. 2022. Training Compute-Optimal Large Language Models. arXiv preprint.  
<https://arxiv.org/abs/2203.15556>  
Cited in: Part 0

**[4]** Ouyang et al. 2022. Training language models to follow instructions with human feedback. NeurIPS 2022.  
<https://arxiv.org/abs/2203.02155>  
Cited in: Part 0

**[5]** Wang et al. 2023. Self-Instruct: Aligning Language Models with Self-Generated Instructions. ACL 2023.  
<https://arxiv.org/abs/2212.10560>  
Cited in: Part 0

**[6]** Hu et al. 2021. LoRA: Low-Rank Adaptation of Large Language Models. ICLR 2022.  
<https://arxiv.org/abs/2106.09685>  
Cited in: Part 0, L-28

**[7]** DeepSeek-AI 2025. DeepSeek-R1: Incentivizing Reasoning Capability in LLMs via Reinforcement Learning. arXiv preprint.  
<https://arxiv.org/abs/2501.12948>  
Cited in: Part 0

**[8]** Zhao et al. 2025. Absolute Zero: Reinforced Self-play Reasoning with Zero Data. NeurIPS 2025.  
<https://arxiv.org/abs/2505.03335>  
Cited in: Part 0, L-32

**[9]** Lightman et al. 2023. Let's Verify Step by Step. arXiv preprint.  
<https://arxiv.org/abs/2305.20050>  
Cited in: Part 0

**[10]** Christiano et al. 2017. Deep Reinforcement Learning from Human Preferences. NeurIPS 2017.  
<https://arxiv.org/abs/1706.03741>  
Cited in: Part 0

**[11]** Rafailov et al. 2023. Direct Preference Optimization: Your Language Model is Secretly a Reward Model. NeurIPS 2023.  
<https://arxiv.org/abs/2305.18290>  
Cited in: Part 0

**[12]** Bai et al. 2022. Constitutional AI: Harmlessness from AI Feedback. arXiv preprint.  
<https://arxiv.org/abs/2212.08073>  
Cited in: Part 0

**[13]** Brown et al. 2020. Language Models are Few-Shot Learners. NeurIPS 2020.  
<https://arxiv.org/abs/2005.14165>  
Cited in: Part 0

**[14]** Wei et al. 2022. Chain-of-Thought Prompting Elicits Reasoning in Large Language Models. NeurIPS 2022.  
<https://arxiv.org/abs/2201.11903>  
Cited in: Part 0

**[15]** Van Clief 2026. Interpretable Context Methodology: Folder Structure as Agentic Architecture (Model Workspace Protocol). arXiv preprint.  
<https://arxiv.org/abs/2603.16021>  
Cited in: Part 0, L-33, Appendix B

**[16]** Lewis et al. 2020. Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks. NeurIPS 2020.  
<https://arxiv.org/abs/2005.11401>  
Cited in: Part 0

**[17]** Schick et al. 2023. Toolformer: Language Models Can Teach Themselves to Use Tools. NeurIPS 2023.  
<https://arxiv.org/abs/2302.04761>  
Cited in: Part 0

**[18]** Yao et al. 2023. ReAct: Synergizing Reasoning and Acting in Language Models. ICLR 2023.  
<https://arxiv.org/abs/2210.03629>  
Cited in: Part 0, L-33, R-11

**[19]** Packer et al. 2023. MemGPT: Towards LLMs as Operating Systems. arXiv preprint.  
<https://arxiv.org/abs/2310.08560>  
Cited in: Part 0, L-33

**[20]** Zelikman et al. 2022. STaR: Bootstrapping Reasoning With Reasoning. NeurIPS 2022.  
<https://arxiv.org/abs/2203.14465>  
Cited in: Part 0, L-32

**[21]** Madaan et al. 2023. Self-Refine: Iterative Refinement with Self-Feedback. NeurIPS 2023.  
<https://arxiv.org/abs/2303.17651>  
Cited in: Part 0, L-32

**[22]** Shinn et al. 2023. Reflexion: Language Agents with Verbal Reinforcement Learning. NeurIPS 2023.  
<https://arxiv.org/abs/2303.11366>  
Cited in: Part 0, L-32

**[23]** Huang et al. 2024. Large Language Models Cannot Self-Correct Reasoning Yet. ICLR 2024.  
<https://arxiv.org/abs/2310.01798>  
Cited in: Part 0, L-32

**[24]** Hinton et al. 2015. Distilling the Knowledge in a Neural Network. arXiv preprint.  
<https://arxiv.org/abs/1503.02531>  
Cited in: Part 0

**[25]** Mukherjee et al. 2023. Orca: Progressive Learning from Complex Explanation Traces of GPT-4. arXiv preprint.  
<https://arxiv.org/abs/2306.02707>  
Cited in: Part 0

**[26]** Du et al. 2023. Improving Factuality and Reasoning in Language Models through Multiagent Debate. arXiv preprint.  
<https://arxiv.org/abs/2305.14325>  
Cited in: Part 0, U-34

## Jurisdiction rule packs (Revenue only)

Rewired as ICM folders under `revenue/rules/`:

- **US** (`rules/us/`) — left-style verifiable forms and withholdings  
- **EU / HR** (`rules/eu-hr/`) — VAT schemes + Croatia home  
- **Cross-border** (`rules/crossborder/`) — bridge pack (treaties / foreign reporting / Pillar Two gate)

Tree 1 implements; Tree 2 designs UX; Tree 3 explains; none invent tax. Load one pack per question locale.
