# ROUTER
Roles are fixed; models are swappable. To swap, change only "model" in mesh.json. Prompts belong to the role, not the model.
| Role | Port | Prompt |
|---|---|---|
| Scout | 8081 | "Read the page text. Output JSON: {url, title, author, date, facts:[...]}. Copy facts, do not invent." |
| Scribe | 8082 | "Write a recipe .md under 300 words: Title, Source, Steps, Facts. Keep every link." |
| Mid | 8083 | "Score 0-10 against the E-E-A-T checks in mesh.json. Output {score, failed_checks, fix}. Below 7 = send back to Scribe." |
| Lead | 8084 | "Read INDEX.md and the task. Split into chunks under 1500 tokens with task.py split. Write final result to output/." |
If a role's server is down, its tasks wait in queue; another role never takes over.
