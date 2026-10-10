#!/bin/bash
# Path confinement for the Mesh sandbox agent. No llama-cli.
set -u
root=$(cd "$(dirname "$0")/../.." && pwd)
agent="$root/tools/qa/sandbox-agent.sh"
fail=0

say() { printf '%s\n' "$1"; }
ok() { say "ok $1"; }
bad() { say "FAIL $1"; fail=1; }

need() {
  local name="$1" got="$2" want="$3"
  if [ "$got" = "$want" ]; then ok "$name"; else bad "$name expected [$want] got [$got]"; fi
}
has() {
  local name="$1" hay="$2" needle="$3"
  case "$hay" in
    *"$needle"*) ok "$name" ;;
    *) bad "$name missing [$needle]" ;;
  esac
}
lacks() {
  local name="$1" hay="$2" needle="$3"
  case "$hay" in
    *"$needle"*) bad "$name unexpectedly had [$needle]" ;;
    *) ok "$name" ;;
  esac
}

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
export HOME="$work/home"
mkdir -p "$HOME/meridian-sandbox" "$HOME/notes" "$HOME/secret"
outside="$work/outside"
mkdir -p "$outside"
printf 'hidden\n' > "$HOME/secret/note.txt"
printf 'outside\n' > "$outside/x"
printf 'keep\n' > "$HOME/notes/keep.txt"
printf 'hello world\n' > "$HOME/notes/edit.txt"

run_tool() {
  HOME="$HOME" MERIDIAN_AGENT_TEST=1 MERIDIAN_TOOL="$1" bash "$agent"
}

# Source helpers for host checks and last-line parsing.
MERIDIAN_AGENT_SOURCE=1
# shellcheck disable=SC1090
source "$agent"
box="$HOME/meridian-sandbox"

if agent_public_host "example.com"; then ok "public example.com"; else bad "public example.com"; fi
if agent_public_host "172.15.1.1"; then ok "public 172.15"; else bad "public 172.15"; fi
for blocked in localhost printer.local 0.0.0.0 127.0.0.1 10.1.2.3 192.168.1.9 169.254.1.1 172.16.0.1 172.31.255.255 example; do
  if agent_public_host "$blocked"; then bad "blocked $blocked"; else ok "blocked $blocked"; fi
done

last=$(agent_last_tool "$(printf '%s\n' 'prose' 'Read shape: TOOL READ no' 'TOOL READ first' 'TOOL WRITE b :: second')")
need "last tool line" "$last" "TOOL WRITE b :: second"
done_line=$(agent_last_tool "$(printf '%s\n' 'TOOL READ a' 'DONE')")
need "last done" "$done_line" "DONE"

out=$(run_tool "TOOL WRITE meridian-sandbox/a.txt :: hello")
has "write inside" "$out" "Wrote "
need "inside text" "$(cat "$HOME/meridian-sandbox/a.txt")" "hello"
lacks "write inside is not a signature" "$out" "MERIDIAN_SIGN"

out=$(run_tool "TOOL READ meridian-sandbox/a.txt")
has "read inside" "$out" "hello"

out=$(run_tool "$(printf '%s\n' 'TOOL WRITE meridian-sandbox/a.txt :: first' 'TOOL WRITE meridian-sandbox/a.txt :: second')")
need "second step wins" "$(cat "$HOME/meridian-sandbox/a.txt")" "second"

printf 'hello world' > "$HOME/meridian-sandbox/edit.txt"
out=$(run_tool "TOOL EDIT meridian-sandbox/edit.txt :: hello :: goodbye")
need "edit inside" "$(cat "$HOME/meridian-sandbox/edit.txt")" "goodbye world"
lacks "edit inside is not a signature" "$out" "MERIDIAN_SIGN"

mkdir -p "$HOME/meridian-sandbox/dir"
printf 'z\n' > "$HOME/meridian-sandbox/dir/z"
out=$(run_tool "TOOL DELETE meridian-sandbox/dir")
if [ -e "$HOME/meridian-sandbox/dir" ]; then bad "dir still there"; else ok "removed sandbox dir"; fi
lacks "sandbox delete is not a signature" "$out" "MERIDIAN_SIGN"

out=$(run_tool "TOOL DELETE meridian-sandbox")
if [ -d "$HOME/meridian-sandbox" ]; then ok "sandbox root stays"; else bad "sandbox root removed"; fi
has "root refused" "$out" "refused"

out=$(run_tool "TOOL WRITE notes/fresh.txt :: fresh")
need "new file outside" "$(cat "$HOME/notes/fresh.txt")" "fresh"
lacks "new file is not a signature" "$out" "MERIDIAN_SIGN"

out=$(run_tool "TOOL WRITE notes/fresh.txt :: changed")
need "overwrite waits" "$(cat "$HOME/notes/fresh.txt")" "fresh"
has "overwrite signs" "$out" "MERIDIAN_SIGN apply"
has "overwrite src" "$out" "SRC=$HOME/meridian-sandbox/.pending-write"
has "overwrite dest" "$out" "DEST=$HOME/notes/fresh.txt"

out=$(run_tool "TOOL EDIT notes/edit.txt :: hello :: goodbye")
need "edit outside waits" "$(cat "$HOME/notes/edit.txt")" "hello world"
has "edit outside signs" "$out" "MERIDIAN_SIGN apply"
has "pending edit" "$(cat "$HOME/meridian-sandbox/.pending-edit")" "goodbye world"

out=$(run_tool "TOOL DELETE notes/keep.txt")
need "delete outside waits" "$(cat "$HOME/notes/keep.txt")" "keep"
has "delete signs" "$out" "MERIDIAN_SIGN delete"
has "delete path" "$out" "PATH=$HOME/notes/keep.txt"
has "delete preview" "$out" "keep"

printf 'ready\n' > "$HOME/meridian-sandbox/ready.txt"
out=$(run_tool "TOOL APPLY meridian-sandbox/ready.txt notes/ready.txt")
need "apply new file" "$(cat "$HOME/notes/ready.txt")" "ready"
lacks "apply new is not a signature" "$out" "MERIDIAN_SIGN"

printf 'old\n' > "$HOME/notes/ready.txt"
printf 'new\n' > "$HOME/meridian-sandbox/ready.txt"
out=$(run_tool "TOOL APPLY meridian-sandbox/ready.txt notes/ready.txt")
need "apply overwrite waits" "$(cat "$HOME/notes/ready.txt")" "old"
has "apply signs" "$out" "MERIDIAN_SIGN apply"
has "apply src in sandbox" "$out" "SRC=$HOME/meridian-sandbox/ready.txt"

out=$(run_tool "TOOL APPLY notes/ready.txt notes/other.txt")
lacks "apply from outside" "$out" "Copied"
has "apply from outside refused" "$out" "Apply only copies out of the sandbox."
if [ -e "$HOME/notes/other.txt" ]; then bad "outside apply created a file"; else ok "outside apply created nothing"; fi

out=$(run_tool "TOOL READ ../secret/note.txt")
lacks "dotdot read" "$out" "hidden"
has "dotdot refused" "$out" "refused"

out=$(run_tool "TOOL DELETE /etc/passwd")
has "absolute refused" "$out" "refused"

ln -s "$outside" "$HOME/meridian-sandbox/out"
out=$(run_tool "TOOL WRITE meridian-sandbox/out/pwn :: owned")
has "symlink escape refused" "$out" "refused"
if [ -e "$outside/pwn" ]; then bad "wrote through symlink"; else ok "symlink was not followed"; fi

ln -s "$HOME/secret" "$HOME/meridian-sandbox/linked"
out=$(run_tool "TOOL DELETE meridian-sandbox/linked/note.txt")
need "home symlink delete still waits" "$(cat "$HOME/secret/note.txt")" "hidden"
has "home symlink signs" "$out" "MERIDIAN_SIGN delete"

mkdir -p "$HOME/meridian-sandbox-evil"
printf 'evil\n' > "$HOME/meridian-sandbox-evil/a.txt"
out=$(run_tool "TOOL DELETE meridian-sandbox-evil/a.txt")
need "evil name is outside" "$(cat "$HOME/meridian-sandbox-evil/a.txt")" "evil"
has "evil name signs" "$out" "MERIDIAN_SIGN delete"

out=$(run_tool "TOOL SEARCH hidden")
has "search finds home file" "$out" "note.txt"
out=$(run_tool "TOOL SEARCH bad;rm")
has "search charset" "$out" "refused"

curl_log="$work/curl.log"
: > "$curl_log"
cat > "$work/curl" << 'EOF'
#!/bin/bash
log="${CURL_LOG:?}"
out=""
prev=""
url=""
for arg in "$@"; do
  if [ "$prev" = "--output" ]; then out="$arg"; fi
  case "$arg" in
    --) ;;
    -*) ;;
    *) url="$arg" ;;
  esac
  prev="$arg"
done
printf '%s\n' "$url" >> "$log"
printf '<html><script>bad()</script><p>Hello page</p></html>\n' > "$out"
printf '200'
EOF
chmod +x "$work/curl"

out=$(CURL_LOG="$curl_log" MERIDIAN_CURL_BIN="$work/curl" HOME="$HOME" MERIDIAN_AGENT_TEST=1 MERIDIAN_TOOL="TOOL WEB http://127.0.0.1/secret" bash "$agent")
has "private web refused" "$out" "refused"
if [ -s "$curl_log" ]; then bad "private web called curl"; else ok "private web skipped curl"; fi

out=$(CURL_LOG="$curl_log" MERIDIAN_CURL_BIN="$work/curl" HOME="$HOME" MERIDIAN_AGENT_TEST=1 MERIDIAN_TOOL="TOOL WEB http://10.0.0.8/a" bash "$agent")
has "ten-net refused" "$out" "refused"
if [ -s "$curl_log" ]; then bad "ten-net called curl"; else ok "ten-net skipped curl"; fi

out=$(CURL_LOG="$curl_log" MERIDIAN_CURL_BIN="$work/curl" HOME="$HOME" MERIDIAN_AGENT_TEST=1 MERIDIAN_TOOL="TOOL WEB https://example.com/hello" bash "$agent")
has "public web fetched" "$out" "Hello page"
lacks "script stripped" "$out" "bad()"
has "public web logged" "$(cat "$curl_log")" "https://example.com/hello"

: > "$curl_log"
out=$(CURL_LOG="$curl_log" MERIDIAN_CURL_BIN="$work/curl" HOME="$HOME" MERIDIAN_AGENT_TEST=1 MERIDIAN_TOOL="TOOL WEB cats and dogs" bash "$agent")
has "web search fetched" "$out" "Hello page"
has "web search url" "$(cat "$curl_log")" "https://html.duckduckgo.com/html/?q=cats+and+dogs"

steps=""
i=1
while [ "$i" -le 6 ]; do
  steps="${steps}TOOL WRITE meridian-sandbox/n${i}.txt :: ${i}"$'\n'
  i=$((i + 1))
done
out=$(run_tool "$steps")
has "five step cap" "$out" "Stopped after 5 steps."
if [ -f "$HOME/meridian-sandbox/n5.txt" ] && [ ! -f "$HOME/meridian-sandbox/n6.txt" ]; then ok "sixth write did not run"; else bad "step cap files"; fi

out=$(run_tool "$(printf '%s\n' 'TOOL WRITE meridian-sandbox/done.txt :: yes' 'DONE' 'TOOL WRITE meridian-sandbox/after.txt :: no')")
need "done stops" "$(cat "$HOME/meridian-sandbox/done.txt")" "yes"
if [ -e "$HOME/meridian-sandbox/after.txt" ]; then bad "work after DONE"; else ok "nothing after DONE"; fi

if grep -n -E '(^|[^[:alnum:]_])eval([^[:alnum:]_]|$)' "$agent" >/dev/null; then bad "agent uses eval"; else ok "agent does not eval"; fi

if [ "$fail" -eq 0 ]; then say "sandbox gate ok"; exit 0; fi
say "sandbox gate failed"
exit 1
