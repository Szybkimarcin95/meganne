#!/data/data/com.termux/files/usr/bin/bash
set -u

ROOT="${MEGANE_REPO:-$HOME/meganne}"
STATE="${MEGANE_AI_STATE:-$HOME/.megane-ai}"
PROMPT="$ROOT/tools/ai-autopilot/PROMPT.md"
POLICY="$ROOT/tools/ai-autopilot/policy.toml"
GEMINI_ENTRY="${GEMINI_ENTRY:-$(command -v gemini 2>/dev/null || true)}"
MODE="${1:-loop}"
mkdir -p "$STATE/logs"
trap 'rm -f "$STATE/pid"' EXIT
LOG="$STATE/logs/run-$(date +%Y%m%d-%H%M%S).log"

stop_job() {
  printf '%s %s\n' "$(date -Iseconds)" "$*" | tee -a "$LOG"
  rm -f "$STATE/enabled"
  exit 2
}

[ -d "$ROOT/.git" ] || stop_job "JOB STOP: repository not found: $ROOT"
[ -n "$GEMINI_ENTRY" ] || stop_job "JOB STOP: Gemini CLI not installed"
[ -f "$PROMPT" ] || stop_job "JOB STOP: missing PROMPT.md"
[ -f "$POLICY" ] || stop_job "JOB STOP: missing policy.toml"
cd "$ROOT" || stop_job "JOB STOP: cannot enter repository"

run_iteration() {
  if [ -n "$(git status --porcelain)" ]; then
    stop_job "JOB STOP: working tree is not clean before iteration"
  fi

  printf '\n===== ITERATION %s =====\n' "$(date -Iseconds)" | tee -a "$LOG"
  env TERMUX_VERSION="${TERMUX_VERSION:-0.118.3}" \
    node "$GEMINI_ENTRY" \
    --approval-mode auto_edit \
    --skip-trust \
    --policy "$POLICY" \
    --output-format stream-json \
    -p "$(cat "$PROMPT")" >>"$LOG" 2>&1
  rc=$?

  if [ "$rc" -ne 0 ]; then
    stop_job "JOB STOP: Gemini CLI exited rc=$rc"
  fi

  if [ -n "$(git status --porcelain)" ]; then
    stop_job "JOB STOP: iteration ended with uncommitted changes; preserved for review"
  fi
  printf '%s\n' "ITERATION PASS" | tee -a "$LOG"
}

if [ "$MODE" = "--once" ]; then
  run_iteration
  exit 0
fi

touch "$STATE/enabled"
while [ -f "$STATE/enabled" ]; do
  run_iteration
  [ -f "$STATE/enabled" ] || break
  sleep "${MEGANE_AI_INTERVAL:-60}"
done

printf '%s\n' "AUTOPILOT STOPPED" | tee -a "$LOG"
