#!/usr/bin/env python3
"""Mesh v2 worker. Loops the queue: Lead task -> Scout (fetch URL) -> Scribe (recipe in references/)
-> Mid (score 1-10, below min goes back to Scribe, max 2 retries) -> Lead (output/ + one INDEX.md line).
Every prompt starts with RULES.md and the matching notes from my-knowledge/. Thermal pause/resume from mesh.json."""
import glob, html, json, os, re, sys, time, urllib.request
D = os.path.dirname(os.path.abspath(__file__)); os.chdir(D)
sys.path.insert(0, D); import task as T
CFG = json.load(open("mesh.json"))
ROLES = CFG["roles"]; TH = CFG.get("thermal", {})
PAUSE, RESUME, POLL = TH.get("pause_c", 69), TH.get("resume_c", 49), TH.get("poll_s", 10)
MIN = CFG.get("eeat", {}).get("min_score", 7); RETRIES = 2
HOST = os.environ.get("MESH_HOST", "127.0.0.1")
for d in ("references", "output", "my-knowledge", "logs"): os.makedirs(d, exist_ok=True)

def log(*a): print(time.strftime("%H:%M:%S"), *a, flush=True)
def temp():
    m = 0
    for z in glob.glob("/sys/class/thermal/thermal_zone*/temp"):
        try: v = int(open(z).read().strip()); m = max(m, v // 1000 if v > 1000 else v)
        except Exception: pass
    return m
def thermal():
    t = temp()
    if t >= PAUSE:
        log(f"hot {t}C: pause")
        while temp() > RESUME: time.sleep(POLL)
        log(f"cool {temp()}C: resume")
def words(s): return set(w for w in re.findall(r"[a-z0-9]{4,}", s.lower()))
def knowledge(text, limit):
    try: rules = open("RULES.md").read()
    except Exception: rules = ""
    want = words(text); notes = []
    for f in sorted(glob.glob("my-knowledge/*.md") + glob.glob("my-knowledge/*.txt")):
        if os.path.basename(f) == "README.md": continue
        try: body = open(f, errors="ignore").read()
        except Exception: continue
        score = len(want & words(body + " " + os.path.basename(f)))
        if score: notes.append((score, f, body))
    notes.sort(key=lambda n: -n[0]); out = "## RULES.md\n" + rules + "\n"
    for _, f, body in notes:
        chunk = f"## {f}\n{body}\n"
        if len(out) + len(chunk) > limit: chunk = chunk[: max(0, limit - len(out))]
        if not chunk: break
        out += chunk
    return out
def ask(role, prompt):
    r = ROLES[role]; budget = int(r.get("ctx", 2048) * 3 * 0.8)  # rough chars
    pre = knowledge(prompt, budget // 3)
    full = (pre + "\n## TASK\n" + prompt)[:budget]
    body = json.dumps({"messages": [{"role": "user", "content": full}], "temperature": 0.2, "max_tokens": 600}).encode()
    for i in range(3):
        thermal()
        try:
            q = urllib.request.Request(f"http://{HOST}:{r['port']}/v1/chat/completions", body, {"Content-Type": "application/json"})
            j = json.load(urllib.request.urlopen(q, timeout=600))
            txt = j["choices"][0]["message"]["content"]
            return re.sub(r"<think>.*?</think>", "", txt, flags=re.S).strip()
        except Exception as e:
            log(f"{role} port {r['port']} not ready ({e}); retry"); time.sleep(10)
    raise RuntimeError(f"{role} server down")
def fetch(url):
    try:
        import requests
        t = requests.get(url, timeout=30, headers={"User-Agent": "Mozilla/5.0 meridian-mesh"}).text
    except ImportError:
        t = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 meridian-mesh"}), timeout=30).read().decode("utf-8", "ignore")
    t = re.sub(r"(?is)<(script|style|noscript|svg).*?</\1>", " ", t)
    t = html.unescape(re.sub(r"<[^>]+>", " ", t))
    return re.sub(r"\s+", " ", t).strip()[:4000]
def slug(s): return (re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")[:60] or "recipe")
def score_of(txt):
    m = re.search(r"SCORE\s*[:=]\s*(\d+)", txt, re.I) or re.search(r"\b(10|[1-9])\s*/\s*10\b", txt) or re.search(r"\b(10|[1-9])\b", txt)
    return int(m.group(1)) if m else 0
def claim(role):
    for f in sorted(glob.glob(f"{T.Q}/todo/*.json")):
        t = json.load(open(f))
        if t["role"] == role:
            os.rename(f, f"{T.Q}/doing/{t['id']}.json"); return t
def done(t, result):
    os.remove(f"{T.Q}/doing/{t['id']}.json"); t["result"] = result; T.save("done", t)

def do_url(url, parent):
    log("scout", url)
    try: page = fetch(url)
    except Exception as e: log("fetch failed", url, e); return None
    facts = ask("scout", f"Source URL: {url}\nPage text:\n{page}\n\nOutput JSON: {{url, title, author, date, facts:[...]}}. Copy facts, do not invent.")
    note = ""; recipe = ""; sc = 0
    for attempt in range(RETRIES + 1):
        log("scribe", url, "try", attempt + 1)
        recipe = ask("scribe", f"Source URL: {url}\nFacts:\n{facts}\n{note}\nWrite a recipe .md under 300 words: Title, Problem, Solution, Commands, Source ({url}). Keep every link.")
        verdict = ask("mid", f"Recipe:\n{recipe}\n\nFacts from {url}:\n{facts}\n\nScore 1-10 against: " + ", ".join(CFG.get("eeat", {}).get("checks", [])) + ". First line: SCORE: N. Then the failed checks and the fix.")
        sc = score_of(verdict); log("mid score", sc)
        if sc >= MIN: break
        note = f"Mid scored the last draft {sc}/10. Fix this:\n{verdict}"
    title = (re.search(r"^#*\s*(?:Title:)?\s*(.+)$", recipe, re.M) or [None, url])[1]
    path = f"references/{slug(title)}-{int(time.time())}.md"
    open(path, "w").write(recipe.strip() + f"\n\nSource: {url}\nScore: {sc}/10\n")
    return path, title.strip()[:80], sc
def do_lead(t):
    urls = re.findall(r"https?://[^\s,|<>\"']+", t["text"])
    log("lead task", t["id"], len(urls), "links")
    made = [r for r in (do_url(u, t["id"]) for u in urls) if r]
    if not made: done(t, "no recipe"); return
    summary = ask("lead", "Task: " + t["text"] + "\nRecipes:\n" + "\n".join(f"- {p} ({s}/10): {open(p).read()[:600]}" for p, _, s in made) + "\nWrite a short plan: what was learned and the next step.")
    out = f"output/{t['id']}.md"
    open(out, "w").write(f"# {t['text']}\n\n{summary}\n\n## Recipes\n" + "\n".join(f"- {p} | {s}/10" for p, _, s in made) + "\n")
    with open("INDEX.md", "a") as f:
        f.write(f"{out} | {made[0][1]} (+{len(made)-1} more) | {max(s for _,_,s in made)}\n" if len(made) > 1 else f"{made[0][0]} | {made[0][1]} | {made[0][2]}\n")
    done(t, out); log("lead wrote", out)

def main():
    once = "--once" in sys.argv
    log("worker up; rules RULES.md; notes my-knowledge/")
    while True:
        t = claim("lead")
        if t:
            try: do_lead(t)
            except Exception as e:
                log("task failed, back to todo:", e)
                os.rename(f"{T.Q}/doing/{t['id']}.json", f"{T.Q}/todo/{t['id']}.json"); time.sleep(30)
        elif once: return
        else: time.sleep(5)
if __name__ == "__main__": main()
