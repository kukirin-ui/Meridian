# Mesh sets

One sandbox. Two sets. `sets/active` names the set that is on.

- Set A (`sets/set-a/`): two Qwen 2.5 0.5B files for Scout and Scribe, Qwen 2.5 3B for Mid, Qwen 2.5 7B for Lead. Models in ~/models. Context 1024, 1024, 2048, 2048.
- Set B (`sets/set-b/`): `qwen2.5-0.5b-instruct-q4_k_m.gguf` for Scout and Scribe (context 2048), `microsoft_Phi-4-mini-instruct-Q4_K_M.gguf` for Mid (context 2048), `Qwen3-4B-Q4_K_M.gguf` for Lead (context 4096). Models in ~/models2.

Set B pipeline: Scout extract, then Scribe recipe, then Mid E-E-A-T verify. A score under 7 goes back to Scribe once. Then Lead writes the plan.

Each set has `mesh.json`, `roster.env`, `task-template.txt`, and `queue.txt`. Switch set (A/B) swaps the roster, `mesh.json`, the task template pointer, and the queue pointer together. Thermal policy stays pause-69-resume-49.
