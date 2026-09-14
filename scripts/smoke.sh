#!/usr/bin/env bash
# End-to-end smoke test for a running TaskMesh stack.
# Assumes: compose infra up, server on :8080, at least one worker running.
# Usage: scripts/smoke.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"

pass=0; fail=0
check() { # check <name> <command...>
  local name="$1"; shift
  if "$@" >/dev/null 2>&1; then echo "PASS: $name"; pass=$((pass+1));
  else echo "FAIL: $name"; fail=$((fail+1)); fi
}
api() { curl -sf "$BASE$1" ${@:2}; }

echo "== submit 5 jobs =="
IDS=""
for p in 9 7 5 3 1; do
  id=$(curl -sf -XPOST "$BASE/api/v1/jobs" -H 'Content-Type: application/json' \
    -d "{\"name\":\"smoke-p$p\",\"priority\":$p,\"cpu\":1,\"memoryMb\":512}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  IDS="$IDS $id"
done
echo "submitted:$IDS"

echo "== jobs drain =="
for i in $(seq 1 30); do
  running=$(api /api/v1/summary | python3 -c 'import json,sys; s=json.load(sys.stdin); print(s["jobs"].get("RUNNING",0)+s["jobs"].get("QUEUED",0)+s["jobs"].get("ASSIGNED",0))')
  [ "$running" = "0" ] && break
  sleep 2
done
completed=$(api /api/v1/summary | python3 -c 'import json,sys; print(json.load(sys.stdin)["jobs"].get("COMPLETED",0))')
[ "${completed:-0}" -ge 5 ] && { echo "PASS: 5 jobs completed (completed=$completed)"; pass=$((pass+1)); } \
  || { echo "FAIL: only $completed completed"; fail=$((fail+1)); }

echo "== cancel =="
cid=$(curl -sf -XPOST "$BASE/api/v1/jobs" -H 'Content-Type: application/json' \
  -d '{"name":"smoke-cancel","priority":1,"cpu":8,"memoryMb":65536}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])') || true
if [ -n "${cid:-}" ]; then
  check "cancel queued job" curl -sf -XPOST "$BASE/api/v1/jobs/$cid/cancel"
else
  echo "SKIP: cancel (no capacity to hold job queued)"
fi

echo "== backpressure =="
code=$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$BASE/api/v1/jobs" -H 'Content-Type: application/json' \
  -d '{"name":"smoke-bp","priority":1,"cpu":1,"memoryMb":1}')
echo "low-priority submit status under threshold env: $code (informational)"

echo "== simulate =="
check "simulate endpoint" curl -sf -XPOST "$BASE/api/v1/simulate" -H 'Content-Type: application/json' \
  -d '{"jobs":100,"strategies":["fifo","fair"]}'

echo "== metrics =="
check "prometheus metrics" curl -sf "$BASE/actuator/prometheus"

echo
echo "smoke: $pass passed, $fail failed"
[ "$fail" = "0" ]
