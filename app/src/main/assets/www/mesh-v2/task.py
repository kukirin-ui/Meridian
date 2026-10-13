#!/usr/bin/env python3
"""Task queue: new/split/claim/done/list. Files in queue/<state>/<id>.json"""
import json, sys, os, time, glob
Q = os.path.join(os.path.dirname(os.path.abspath(__file__)), "queue")
for s in ("todo", "doing", "done"): os.makedirs(f"{Q}/{s}", exist_ok=True)
def save(state, t): json.dump(t, open(f"{Q}/{state}/{t['id']}.json", "w"), indent=1)
def new(role, text, parent=None):
    t = {"id": str(int(time.time()*1000)), "role": role, "text": text, "parent": parent}
    save("todo", t); print(t["id"]); time.sleep(0.002)
def main(a):
    c = a[0] if a else "list"
    if c == "new": new(a[1], " ".join(a[2:]))
    elif c == "split":
        p = json.load(open(f"{Q}/todo/{a[1]}.json")) if os.path.exists(f"{Q}/todo/{a[1]}.json") else {"id": a[1]}
        for part in " ".join(a[3:]).split("|"):
            if part.strip(): new(a[2], part.strip(), p["id"])
    elif c == "claim":
        for f in sorted(glob.glob(f"{Q}/todo/*.json")):
            t = json.load(open(f))
            if t["role"] == a[1]:
                os.rename(f, f"{Q}/doing/{t['id']}.json"); print(json.dumps(t)); return
        print("none")
    elif c == "done":
        f = f"{Q}/doing/{a[1]}.json"; t = json.load(open(f))
        t["result"] = " ".join(a[2:]); os.remove(f); save("done", t); print("ok")
    else:
        for s in ("todo", "doing", "done"): print(s, len(glob.glob(f"{Q}/{s}/*.json")))
if __name__ == "__main__": main(sys.argv[1:])
