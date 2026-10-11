#!/bin/bash
# Meridian sandbox agent.
# Paths stay under $HOME. Inside $HOME/meridian-sandbox the model may read,
# write, edit, delete, and search. Outside that folder, still under $HOME,
# a new file is written immediately. Deleting or replacing an existing file
# prints MERIDIAN_SIGN and stops. The page asks for a fingerprint, then runs
# a fresh command built from PATH, SRC, and DEST only.
# Installing an apk prints MERIDIAN_SIGN install and stops, even inside the sandbox.
# The page builds pm install from that PATH and runs it through Shizuku rish.
# Reading the system log runs logcat through rish when that script is already exported.
# A model already in Downloads is linked into ~/models.
# A line that starts with TOOL is data. The shell does not run it.

agent_need_box() {
  [ -n "${HOME:-}" ] || return 1
  box="$HOME/meridian-sandbox"
  mkdir -p -- "$box" || return 1
}

agent_charset() {
  local text stripped
  text=$(printf '%s' "$1" | tr -d "$2")
  [ -z "$text" ]
}

agent_safe_token() {
  local raw="$1"
  [ -n "$raw" ] || return 1
  [ "${#raw}" -le 400 ] || return 1
  case "$raw" in
    *..*|*$'\n'*|*$'\r'*|*$'\t'*|/*|~) return 1 ;;
  esac
  agent_charset "$raw" 'A-Za-z0-9_./~+-'
}

agent_resolve() {
  local raw="$1" rel abs parent base real cur next suffix
  agent_safe_token "$raw" || return 1
  case "$raw" in
    ~/*) rel="${raw#~/}" ;;
    *) rel="$raw" ;;
  esac
  [ -n "$rel" ] || return 1
  abs="$HOME/$rel"
  parent=$(dirname -- "$abs")
  base=$(basename -- "$abs")
  [ -n "$base" ] && [ "$base" != "." ] && [ "$base" != ".." ] || return 1
  suffix=""
  cur="$parent"
  while [ ! -d "$cur" ]; do
    suffix="/$(basename -- "$cur")$suffix"
    next=$(dirname -- "$cur")
    [ "$next" = "$cur" ] && return 1
    cur="$next"
  done
  real=$(cd -P -- "$cur" && pwd) || return 1
  case "$real" in
    "$HOME"|"$HOME"/*) ;;
    *) return 1 ;;
  esac
  printf '%s%s/%s\n' "$real" "$suffix" "$base"
}

agent_in_box() {
  case "$1" in
    "$box"/*) return 0 ;;
    *) return 1 ;;
  esac
}

agent_parent_ok() {
  local parent="$1" real
  [ -d "$parent" ] || return 1
  real=$(cd -P -- "$parent" && pwd) || return 1
  case "$real" in
    "$HOME"|"$HOME"/*) ;;
    *) return 1 ;;
  esac
  printf '%s\n' "$real"
}

agent_public_host() {
  local host second
  host=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
  [ -n "$host" ] || return 1
  case "$host" in
    *.*) ;;
    *) return 1 ;;
  esac
  case "$host" in
    localhost|*.local|0.0.0.0) return 1 ;;
    127.*|10.*|192.168.*|169.254.*) return 1 ;;
  esac
  case "$host" in
    172.*)
      second=$(printf '%s' "$host" | cut -d. -f2)
      case "$second" in
        16|17|18|19|20|21|22|23|24|25|26|27|28|29|30|31) return 1 ;;
      esac
      ;;
  esac
  return 0
}

agent_edit_text() {
  [ -n "$1" ] || return 1
  [ "${#1}" -le 400 ] || return 1
  agent_charset "$1" 'A-Za-z0-9 ._:/+-'
}

agent_plain() {
  printf '%s' "$1" | tr '\n\r\t' '   ' | awk '
    BEGIN { skip = 0 }
    {
      n = split($0, parts, "<")
      out = parts[1]
      for (i = 2; i <= n; i++) {
        p = parts[i]
        low = tolower(p)
        gt = index(p, ">")
        if (gt == 0) {
          tag = low
          rest = ""
        } else {
          tag = substr(low, 1, gt - 1)
          rest = substr(p, gt + 1)
        }
        gsub(/^[ \t]+/, "", tag)
        if (tag ~ /^script([ \t>]|$)/ || tag ~ /^script$/) skip = 1
        else if (tag ~ /^\/script([ \t>]|$)/ || tag ~ /^\/script$/) skip = 0
        else if (tag ~ /^style([ \t>]|$)/ || tag ~ /^style$/) skip = 1
        else if (tag ~ /^\/style([ \t>]|$)/ || tag ~ /^\/style$/) skip = 0
        if (skip) continue
        out = out " " rest
      }
      gsub(/[ \t]+/, " ", out)
      printf "%s", out
    }
  ' | head -c 2000
  echo
}

agent_curl() {
  if [ -n "${MERIDIAN_CURL_BIN:-}" ]; then
    "$MERIDIAN_CURL_BIN" "$@"
    return
  fi
  command curl "$@"
}

agent_fetch() {
  local url="$1" tmp code
  agent_need_box || return 1
  tmp="$box/.fetch"
  code=$(agent_curl -fsS --max-time 8 --max-redirs 0 --proto '=http,https' --output "$tmp" --write-out '%{http_code}' -- "$url" 2>/dev/null) || code=""
  case "$code" in
    2??) head -c 200000 -- "$tmp"; return 0 ;;
    *) return 1 ;;
  esac
}

agent_show_diff() {
  local current="$1" proposed="$2"
  if command -v diff >/dev/null 2>&1; then
    diff -u -- "$current" "$proposed" | head -c 4000 || true
    echo
    return
  fi
  echo "--- current ---"
  head -c 800 -- "$current" || true
  echo
  echo "--- proposed ---"
  head -c 800 -- "$proposed" || true
  echo
}

agent_sign_apply() {
  local src="$1" dest="$2"
  echo "MERIDIAN_SIGN apply"
  echo "SRC=$src"
  echo "DEST=$dest"
  if [ -f "$dest" ] && [ ! -L "$dest" ]; then
    agent_show_diff "$dest" "$src"
  else
    echo "Destination exists and is not a plain file."
  fi
}

agent_read() {
  local dest n
  dest=$(agent_resolve "$1") || { echo "That path was refused."; return; }
  if [ ! -f "$dest" ] || [ -L "$dest" ]; then
    echo "That file is not on this phone."
    return
  fi
  n=$(wc -c < "$dest" | tr -d ' ')
  if [ "$n" -gt 1000000 ]; then
    echo "That file is over 1 MB."
    return
  fi
  echo "--- $dest ---"
  head -c 4000 -- "$dest"
  echo
}

agent_write() {
  local path="$1" content="$2" dest parent real
  [ "${#content}" -le 2000 ] || { echo "That text is too long."; return; }
  dest=$(agent_resolve "$path") || { echo "That path was refused."; return; }
  if agent_in_box "$dest"; then
    parent=$(dirname -- "$dest")
    mkdir -p -- "$parent" || { echo "Could not create the folder."; return; }
    real=$(agent_parent_ok "$parent") || { echo "That path was refused."; return; }
    case "$real" in
      "$box"|"$box"/*) ;;
      *) echo "That path was refused."; return ;;
    esac
    printf '%s' "$content" > "$real/$(basename -- "$dest")"
    echo "Wrote $real/$(basename -- "$dest")"
    return
  fi
  if [ -e "$dest" ] || [ -L "$dest" ]; then
    printf '%s' "$content" > "$box/.pending-write"
    agent_sign_apply "$box/.pending-write" "$dest"
    return
  fi
  parent=$(dirname -- "$dest")
  mkdir -p -- "$parent" || { echo "Could not create the folder."; return; }
  real=$(agent_parent_ok "$parent") || { echo "That path was refused."; return; }
  case "$real/$(basename -- "$dest")" in
    "$box"|"$box"/*) echo "That path was refused."; return ;;
  esac
  printf '%s' "$content" > "$real/$(basename -- "$dest")"
  echo "Wrote $real/$(basename -- "$dest")"
}

agent_edit() {
  local path="$1" old="$2" new="$3" dest data prefix suffix updated
  agent_edit_text "$old" || { echo "Old text was refused."; return; }
  agent_edit_text "$new" || { echo "New text was refused."; return; }
  [ "$old" != "$new" ] || { echo "Nothing to change."; return; }
  dest=$(agent_resolve "$path") || { echo "That path was refused."; return; }
  if [ ! -f "$dest" ] || [ -L "$dest" ]; then
    echo "That file is not on this phone."
    return
  fi
  data=$(head -c 100000 -- "$dest") || { echo "Could not read the file."; return; }
  case "$data" in
    *"$old"*) ;;
    *) echo "Old text was not found."; return ;;
  esac
  prefix=${data%%"$old"*}
  suffix=${data#*"$old"}
  updated="$prefix$new$suffix"
  if agent_in_box "$dest"; then
    printf '%s' "$updated" > "$dest"
    echo "Edited $dest"
    return
  fi
  printf '%s' "$updated" > "$box/.pending-edit"
  agent_sign_apply "$box/.pending-edit" "$dest"
}

agent_delete() {
  local dest
  dest=$(agent_resolve "$1") || { echo "That path was refused."; return; }
  case "$dest" in
    "$HOME"|"$box") echo "That path was refused."; return ;;
  esac
  if agent_in_box "$dest"; then
    if [ -L "$dest" ]; then
      rm -f -- "$dest"
      echo "Removed $dest"
      return
    fi
    if [ -d "$dest" ]; then
      rm -rf -- "$dest"
      echo "Removed $dest"
      return
    fi
    if [ -f "$dest" ]; then
      rm -f -- "$dest"
      echo "Removed $dest"
      return
    fi
    echo "That path is not there."
    return
  fi
  if [ ! -e "$dest" ] && [ ! -L "$dest" ]; then
    echo "That path is not there."
    return
  fi
  echo "MERIDIAN_SIGN delete"
  echo "PATH=$dest"
  if [ -L "$dest" ]; then
    echo "--- symlink ---"
    ls -ld -- "$dest"
    return
  fi
  if [ -f "$dest" ]; then
    echo "--- preview ---"
    head -c 1500 -- "$dest"
    echo
    return
  fi
  if [ -d "$dest" ]; then
    echo "--- directory ---"
    ls -la -- "$dest" | head -n 40
    return
  fi
  echo "--- not a plain file ---"
  ls -ld -- "$dest"
}

agent_search() {
  local words="$1"
  [ -n "$words" ] || { echo "That search was refused."; return; }
  [ "${#words}" -le 200 ] || { echo "That search was refused."; return; }
  agent_charset "$words" 'A-Za-z0-9 ._+-' || { echo "That search was refused."; return; }
  grep -r -n -I -F -m 5 --binary-files=without-match --exclude-dir=models --exclude-dir=.git -- "$words" "$HOME" 2>/dev/null | head -n 40 || true
}

agent_web_search() {
  local words="$1" q body
  [ -n "$words" ] || { echo "That search was refused."; return; }
  [ "${#words}" -le 200 ] || { echo "That search was refused."; return; }
  agent_charset "$words" 'A-Za-z0-9 ._+-' || { echo "That search was refused."; return; }
  agent_public_host "html.duckduckgo.com" || { echo "That address was refused."; return; }
  q=$(printf '%s' "$words" | tr ' ' '+')
  body=$(agent_fetch "https://html.duckduckgo.com/html/?q=${q}") || { echo "That page could not be read."; return; }
  agent_plain "$body"
}

agent_web() {
  local url="$1" rest host body
  case "$url" in
    http://*|https://*) ;;
    *) agent_web_search "$url"; return ;;
  esac
  [ "${#url}" -le 300 ] || { echo "That address was refused."; return; }
  case "$url" in
    *[[:space:]]*|*[\'\"\\\;\|\&\<\>\[]*) echo "That address was refused."; return ;;
  esac
  rest="${url#*://}"
  case "$rest" in
    *@*) echo "That address was refused."; return ;;
  esac
  host="${rest%%/*}"
  host="${host%%\?*}"
  host="${host%%#*}"
  host="${host%%:*}"
  agent_public_host "$host" || { echo "That address was refused."; return; }
  body=$(agent_fetch "$url") || { echo "That page could not be read."; return; }
  agent_plain "$body"
}

agent_find_rish() {
  local candidate
  for candidate in "$HOME/rish" "$HOME/bin/rish" "$HOME/shizuku/rish"; do
    if [ -f "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  if [ -n "${PREFIX:-}" ] && [ -f "$PREFIX/bin/rish" ]; then
    printf '%s\n' "$PREFIX/bin/rish"
    return 0
  fi
  if command -v rish >/dev/null 2>&1; then
    candidate=$(command -v rish)
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi
  return 1
}

agent_logs() {
  local rish
  rish=$(agent_find_rish) || {
    echo "Shizuku is not in this shell yet."
    echo "Open Shizuku, start it with Wireless debugging, choose Use Shizuku in terminal apps, and export rish into the Termux home folder."
    echo "Termux alone cannot install an APK or read the system log."
    return 0
  }
  sh "$rish" -c "logcat -d -t 80" | head -c 8000
  echo
}

agent_install() {
  local dest
  dest=$(agent_resolve "$1") || { echo "That path was refused."; return; }
  case "$dest" in
    *.apk) ;;
    *) echo "That path was refused."; return ;;
  esac
  case "$dest" in
    "$HOME"/*) ;;
    *) echo "That path was refused."; return ;;
  esac
  if [ -L "$dest" ]; then
    echo "That path was refused."
    return
  fi
  if [ ! -f "$dest" ]; then
    echo "That apk is not on this phone."
    return
  fi
  echo "MERIDIAN_SIGN install"
  echo "PATH=$dest"
  ls -lh -- "$dest"
}

agent_exact_model() {
  local dir="$1" name="$2" n
  [ -n "$name" ] || return 1
  [ -f "$dir/$name" ] || return 1
  [ -L "$dir/$name" ] && return 1
  n=$(wc -c < "$dir/$name" | tr -d ' ')
  [ "$n" -gt 1000000 ] || return 1
  printf '%s\n' "$dir/$name"
}

agent_gemma_model() {
  local dir="$1" candidate n
  for candidate in "$dir"/gemma*.gguf "$dir"/Gemma*.gguf; do
    [ -f "$candidate" ] || continue
    [ -L "$candidate" ] && continue
    n=$(wc -c < "$candidate" | tr -d ' ')
    if [ "$n" -gt 1000000 ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

agent_adopt_model() {
  local name="$1" dest found dir n
  agent_charset "$name" 'A-Za-z0-9._+-' || { echo "That model name was refused."; return 1; }
  case "$name" in
    *.gguf) ;;
    *) echo "That model name was refused."; return 1 ;;
  esac
  case "$name" in
    */*|*..*) echo "That model name was refused."; return 1 ;;
  esac
  mkdir -p -- "$HOME/models" || { echo "Could not create ~/models."; return 1; }
  dest="$HOME/models/$name"
  if [ -f "$dest" ] && [ ! -L "$dest" ]; then
    n=$(wc -c < "$dest" | tr -d ' ')
    if [ "$n" -gt 1000000 ]; then
      echo "Using ~/models/$name"
      return 0
    fi
    rm -f -- "$dest"
    echo "Removed a short file that was not the model."
  fi
  found=""
  for dir in "$HOME/storage/downloads" "$HOME/storage/shared/Download" "$HOME/storage/shared/Downloads" "$HOME/downloads" "/sdcard/Download" "/sdcard/Downloads"; do
    found=$(agent_exact_model "$dir" "$name") && break
    found=""
  done
  if [ -z "$found" ]; then
    case "$name" in
      [Gg]emma*)
        for dir in "$HOME/storage/downloads" "$HOME/storage/shared/Download" "$HOME/storage/shared/Downloads" "$HOME/downloads" "/sdcard/Download" "/sdcard/Downloads"; do
          found=$(agent_gemma_model "$dir") && break
          found=""
        done
        ;;
    esac
  fi
  if [ -z "$found" ]; then
    echo "No model file in Downloads yet."
    return 1
  fi
  if ln -sfn -- "$found" "$dest"; then
    echo "Using the file already in Downloads: $found"
    return 0
  fi
  cp -f -- "$found" "$dest" || { echo "Could not copy the model."; return 1; }
  echo "Using the file already in Downloads: $found"
}

agent_apply() {
  local src dest parent real landed
  src=$(agent_resolve "$1") || { echo "That path was refused."; return; }
  dest=$(agent_resolve "$2") || { echo "That path was refused."; return; }
  agent_in_box "$src" || { echo "Apply only copies out of the sandbox."; return; }
  if [ ! -f "$src" ] || [ -L "$src" ]; then
    echo "The sandbox file is missing."
    return
  fi
  case "$dest" in
    "$HOME"|"$box"|"$box"/*) echo "That destination was refused."; return ;;
    "$HOME"/*) ;;
    *) echo "That destination was refused."; return ;;
  esac
  if [ -e "$dest" ] || [ -L "$dest" ]; then
    agent_sign_apply "$src" "$dest"
    return
  fi
  parent=$(dirname -- "$dest")
  mkdir -p -- "$parent" || { echo "Could not create the folder."; return; }
  real=$(agent_parent_ok "$parent") || { echo "That destination was refused."; return; }
  landed="$real/$(basename -- "$dest")"
  case "$landed" in
    "$box"|"$box"/*) echo "That destination was refused."; return ;;
    "$HOME"/*) ;;
    *) echo "That destination was refused."; return ;;
  esac
  cp -f -- "$src" "$landed"
  echo "Copied to $landed"
}

agent_dispatch() {
  local line="$1" rest verb path tail old new src dest
  agent_need_box || { echo "The sandbox could not be prepared."; return; }
  line=$(printf '%s' "$line" | tr -d '\r')
  [ "${#line}" -le 4000 ] || { echo "That tool line is too long."; return; }
  case "$line" in
    DONE) echo DONE; return ;;
    "TOOL "*) ;;
    *) echo "No tool."; return ;;
  esac
  rest="${line#TOOL }"
  verb="${rest%% *}"
  case "$rest" in
    *" "*) rest="${rest#"$verb" }" ;;
    *) rest="" ;;
  esac
  case "$verb" in
    READ) agent_read "$rest" ;;
    WRITE)
      case "$rest" in
        *" :: "*) ;;
        *) echo "Write needs text."; return ;;
      esac
      path="${rest%% :: *}"
      tail="${rest#* :: }"
      agent_write "$path" "$tail"
      ;;
    EDIT)
      case "$rest" in
        *" :: "*" :: "*) ;;
        *) echo "Edit needs old and new text."; return ;;
      esac
      path="${rest%% :: *}"
      tail="${rest#* :: }"
      old="${tail%% :: *}"
      new="${tail#* :: }"
      agent_edit "$path" "$old" "$new"
      ;;
    DELETE) agent_delete "$rest" ;;
    LOGS) agent_logs ;;
    INSTALL) agent_install "$rest" ;;
    SEARCH) agent_search "$rest" ;;
    WEB) agent_web "$rest" ;;
    APPLY)
      src="${rest%% *}"
      dest="${rest#"$src"}"
      dest="${dest# }"
      [ -n "$src" ] && [ -n "$dest" ] && [ "$dest" != "$rest" ] || { echo "Apply needs two paths."; return; }
      agent_apply "$src" "$dest"
      ;;
    *) echo "Unknown tool." ;;
  esac
}

agent_last_tool() {
  printf '%s\n' "$1" | grep -E '^(TOOL |DONE$)' | tail -n 1
}

agent_note() {
  printf 'note: %s\n%s\n' "$1" "$2" >> "$box/.notes"
  tail -c 1800 "$box/.notes" > "$box/.notes.trim"
  mv -f -- "$box/.notes.trim" "$box/.notes"
}

agent_llama_mode() {
  local help
  if [ -n "${agent_llama_cached:-}" ]; then
    printf '%s\n' "$agent_llama_cached"
    return
  fi
  help=$(llama-cli --help 2>&1 || true)
  case "$help" in
    *" -f "*|*" -f,"*) agent_llama_cached=f ;;
    *) agent_llama_cached=p ;;
  esac
  printf '%s\n' "$agent_llama_cached"
}

agent_reply() {
  local step="$1" ask mode
  if [ "${MERIDIAN_AGENT_TEST:-}" = "1" ]; then
    printf '%s\n' "${MERIDIAN_TOOL:-}" | sed -n "${step}p"
    return 0
  fi
  if [ ! -s "$box/.prompt" ]; then
    echo "The question was empty."
    return 0
  fi
  ask=$(cat -- "$box/.prompt")
  if [ -s "$box/.notes" ]; then
    ask="$ask

Results so far:
$(tail -c 1800 "$box/.notes")"
  fi
  printf '%s' "$ask" > "$box/.ask"
  mode=$(agent_llama_mode)
  if [ "$mode" = "f" ]; then
    llama-cli -m "$model" -f "$box/.ask" -n 220 -c 4096 -ngl "$ngl"
    return
  fi
  llama-cli -m "$model" -p "$(cat -- "$box/.ask")" -n 220 -c 4096 -ngl "$ngl"
}

agent_run() {
  local step reply tool result
  for step in 1 2 3 4 5; do
    reply=$(agent_reply "$step") || reply=""
    tool=$(agent_last_tool "$reply")
    if [ -z "$tool" ] || [ "$tool" = "DONE" ]; then
      printf '%s\n' "$reply"
      echo DONE
      return 0
    fi
    echo "STEP $step"
    printf '%s\n' "$tool"
    result=$(agent_dispatch "$tool")
    printf '%s\n' "$result"
    case "$result" in
      *"MERIDIAN_SIGN"*) return 0 ;;
    esac
    agent_note "$tool" "$result"
  done
  echo "Stopped after 5 steps."
}

agent_main() {
  model="${1:-}"
  ngl="${2:-0}"
  case "$ngl" in
    0|16|99) ;;
    *) ngl=0 ;;
  esac
  agent_need_box || exit 1
  if [ "${MERIDIAN_AGENT_TEST:-}" != "1" ]; then
    if ! command -v llama-cli >/dev/null 2>&1; then
      echo "llama.cpp is not installed yet. Press Download model on Mesh."
      exit 0
    fi
    if [ ! -f "$model" ]; then
      echo "This model is not on the phone yet. Press Download model on Mesh."
      exit 0
    fi
    if [ ! -s "$box/.prompt" ]; then
      echo "The question was empty."
      exit 0
    fi
  fi
  : > "$box/.notes"
  agent_run
}

if [ "${MERIDIAN_AGENT_SOURCE:-}" = "1" ]; then
  return 0 2>/dev/null || exit 0
fi
agent_main "$@"
