#!/bin/bash
# Stage a Termux-home tree. GitHub Actions uploads dist/termux-home, and
# the artifact zip extracts meridian-sandbox and meridian_sandbox into $HOME.
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
out="$root/dist/termux-home"
config="$root/app/src/main/assets/www/config.json"
page="$root/meridian_sandbox/www/index.html"
agent="$root/tools/qa/sandbox-agent.sh"
trees="$root/docs/skill-trees.md"
cards="$root/app/src/main/assets/www/skill-trees.js"
gemma="gemma-2-9b-it-Q4_K_M.gguf"

for need in "$config" "$page" "$agent" "$trees" "$cards"; do
  if [ ! -f "$need" ]; then
    echo "missing $need" >&2
    exit 1
  fi
done

if ! grep -q "$gemma" "$config"; then
  echo "config.json is missing the Gemma model path" >&2
  exit 1
fi

rm -rf -- "$out"
mkdir -p -- "$out"

fill() {
  dest=$1
  mkdir -p -- "$dest/www" "$dest/skills-workspace/_config"
  cp -- "$config" "$dest/www/config.json"
  cp -- "$page" "$dest/www/index.html"
  cp -- "$agent" "$dest/agent.sh"
  cp -- "$agent" "$dest/.agent.sh"
  chmod 755 "$dest/agent.sh" "$dest/.agent.sh"
  cp -- "$trees" "$dest/skills-workspace/skill-trees.md"
  cp -- "$cards" "$dest/skills-workspace/skill-trees.js"
  : > "$dest/skills-workspace/_config/.gitkeep"
}

fill "$out/meridian-sandbox"
fill "$out/meridian_sandbox"

cat > "$out/README.txt" << 'EOF'
Meridian sandbox for the Termux home folder.

This download is already a zip. In Termux:

  cd ~
  unzip ~/storage/downloads/Meridian-sandbox-home.zip

One unzip creates two folders:

  ~/meridian-sandbox
  ~/meridian_sandbox

Mesh uses ~/meridian-sandbox. That is where the model reads, writes, edits,
deletes, and searches. ~/meridian_sandbox is the same tree under the name
from the setup notes, including www/config.json.

www/config.json points at Gemma 2 9B:

  /data/data/com.termux/files/home/models/gemma-2-9b-it-Q4_K_M.gguf

The Meridian app reads that same config from inside the APK. This zip only
seeds the home folders. Do not copy any Java file over the Meridian app.

agent.sh and .agent.sh are the sandbox agent. Mesh writes .agent.sh again
the first time you ask it a question. skills-workspace holds the skill trees.
EOF

if [ -e "$out/meridian-sandbox/src" ] || [ -e "$out/meridian_sandbox/src" ]; then
  echo "sandbox zip must not contain Java sources" >&2
  exit 1
fi

python3 - "$out" "$gemma" << 'PY'
import json, sys
from pathlib import Path
out = Path(sys.argv[1])
gemma = sys.argv[2]
for name in ("meridian-sandbox", "meridian_sandbox"):
    box = out / name
    cfg = json.loads((box / "www" / "config.json").read_text())
    if cfg.get("active_tier") != "custom":
        raise SystemExit(name + " active_tier")
    path = cfg.get("custom_model_path") or ""
    if not path.endswith("/models/" + gemma):
        raise SystemExit(name + " model path")
    for rel in (
        "www/index.html",
        "agent.sh",
        ".agent.sh",
        "skills-workspace/skill-trees.md",
        "skills-workspace/skill-trees.js",
    ):
        if not (box / rel).is_file():
            raise SystemExit("missing " + name + "/" + rel)
print("sandbox home tree ok")
PY
