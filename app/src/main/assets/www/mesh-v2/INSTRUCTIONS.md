# Instructions
Folder on the phone: ~/meridian-mesh-v2 (Meridian writes it when you switch to Set B or press Start mesh; it only adds missing files and never overwrites yours).

## Where to edit
- RULES.md: the one rule file. Every worker reads it before every prompt. Edit it to change how all workers behave.
- my-knowledge/: drop your own .md/.txt notes here. Workers add the notes that match the task to every prompt.
- mesh.json: ports, models, context, thermal (pause 69C / resume 49C) and the minimum Mid score (eeat.min_score, default 7).
- start-mesh.sh: the MODEL line picks the .gguf file per role (keep it in sync with mesh.json).
- ROUTER.md: role prompts and fixed roles.

## Run
1. Models go in ~/models2 (Qwen2.5 0.5B, Phi-4-mini, Qwen3 4B).
2. In Meridian: Start mesh (servers on 8081-8084 + worker.py). Stop mesh stops everything.
   By hand: `termux-wake-lock; bash start-mesh.sh &` then `python worker.py &`.
3. Add work: Meridian "Add links", or `python task.py new lead "Make recipes from https://a.com https://b.com"`.
4. Flow: Scout fetches each link -> Scribe writes references/*.md -> Mid scores 1-10 (below 7 goes back to Scribe, max 2 retries) -> Lead writes output/<id>.md and adds one line to INDEX.md.
5. See results: Meridian "Show learned" (prints INDEX.md), or `cat INDEX.md`. Logs: logs/worker.out, logs/mesh.out, logs/<role>.log.
6. Status: `python task.py list`. Optional: `pip install requests` (worker falls back to urllib without it).
