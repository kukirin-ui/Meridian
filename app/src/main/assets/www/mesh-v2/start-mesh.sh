#!/usr/bin/env bash
# Start one llama-server per role, with thermal watch.
M="${MODELS:-$HOME/models2}"
BIN="${LLAMA:-llama-server}"
PAUSE=69; RESUME=49
declare -A MODEL=( [scout]=qwen2.5-0.5b-instruct-q4_k_m.gguf [scribe]=qwen2.5-0.5b-instruct-q4_k_m.gguf [mid]=microsoft_Phi-4-mini-instruct-Q4_K_M.gguf [lead]=Qwen3-4B-Q4_K_M.gguf )
declare -A PORT=( [scout]=8081 [scribe]=8082 [mid]=8083 [lead]=8084 )
declare -A CTX=( [scout]=2048 [scribe]=2048 [mid]=2048 [lead]=4096 )
declare -A THR=( [scout]=2 [scribe]=2 [mid]=2 [lead]=2 )
mkdir -p logs
temp() { local m=0 v; for z in /sys/class/thermal/thermal_zone*/temp; do v=$(cat "$z" 2>/dev/null) || continue; [ "$v" -gt "$m" ] 2>/dev/null && m=$v; done; echo $((m>1000 ? m/1000 : m)); }
for r in scout scribe mid lead; do
  f="$M/${MODEL[$r]}"; [ -f "$f" ] || { echo "missing $f"; exit 1; }
  "$BIN" -m "$f" --port "${PORT[$r]}" -c "${CTX[$r]}" -t "${THR[$r]}" --host 127.0.0.1 > "logs/$r.log" 2>&1 &
  echo $! > "logs/$r.pid"; echo "$r on ${PORT[$r]} pid $!"; sleep 3
done
trap 'for p in logs/*.pid; do kill -CONT $(cat $p) 2>/dev/null; kill $(cat $p) 2>/dev/null; done; exit' INT TERM
paused=0
while true; do
  t=$(temp)
  if [ $paused -eq 0 ] && [ "$t" -ge $PAUSE ]; then echo "hot ${t}C: pause"; for p in logs/*.pid; do kill -STOP $(cat $p); done; paused=1
  elif [ $paused -eq 1 ] && [ "$t" -le $RESUME ]; then echo "cool ${t}C: resume"; for p in logs/*.pid; do kill -CONT $(cat $p); done; paused=0; fi
  sleep 10
done
