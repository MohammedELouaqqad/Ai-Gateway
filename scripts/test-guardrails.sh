#!/usr/bin/env bash
# Test des garde-fous AI Gateway (rate limit + budget).
# Prérequis : stack up, TOKEN JWT, modèle actif.
#
#   export API=http://localhost:8080
#   export TOKEN='eyJ...'
#   ./scripts/test-guardrails.sh
#
set -euo pipefail

API="${API:-http://localhost:8080}"
TOKEN="${TOKEN:?Set TOKEN to a Bearer JWT}"
MODEL_LOCAL="${MODEL_LOCAL:-llama3.2:1b}"
MODEL_CLOUD="${MODEL_CLOUD:-gpt-4o-mini}"

auth() { echo "Authorization: Bearer $TOKEN"; }

echo "== Config garde-fous =="
curl -sS -H "$(auth)" "$API/api/admin/guardrails/config" | head -c 500; echo

echo
echo "== (a) Boucle rapide → rate_limit_minute (429) =="
curl -sS -X PUT -H "$(auth)" -H 'Content-Type: application/json' \
  -d '{"requestsPerMinute":3}' \
  "$API/api/admin/guardrails/config" >/dev/null

ok=0
blocked=0
for i in $(seq 1 8); do
  code=$(curl -sS -o /tmp/guard-body.json -w '%{http_code}' -X POST -H "$(auth)" -H 'Content-Type: application/json' \
    -d "{\"content\":\"ping $i\",\"model\":\"$MODEL_LOCAL\",\"stream\":false}" \
    "$API/api/chat" || true)
  echo "  request $i → HTTP $code"
  if [[ "$code" == "429" ]]; then
    blocked=$((blocked + 1))
    echo "  body: $(head -c 200 /tmp/guard-body.json)"
  elif [[ "$code" == "200" ]]; then
    ok=$((ok + 1))
  fi
done
echo "→ OK=$ok BLOCKED=$blocked (attendu: BLOCKED>=1)"

echo
echo "== (b) Gros prompt modèle cher → budget_* si plafond bas =="
curl -sS -X PUT -H "$(auth)" -H 'Content-Type: application/json' \
  -d '{"userBudgetPerDayEur":0.0000001,"globalBudgetPerDayEur":50,"requestsPerMinute":100}' \
  "$API/api/admin/guardrails/config" >/dev/null

LONG=$(python3 -c 'print("x"*8000)')
code=$(curl -sS -o /tmp/guard-budget.json -w '%{http_code}' -X POST -H "$(auth)" -H 'Content-Type: application/json' \
  -d "{\"content\":\"$LONG\",\"model\":\"$MODEL_CLOUD\",\"stream\":false}" \
  "$API/api/chat" || true)
echo "  cloud call → HTTP $code"
head -c 300 /tmp/guard-budget.json; echo
echo "  (attendu 429 reason=budget_user si GPT activé + pricing YAML)"

echo
echo "== (c) Flood Ollama → rate limit seulement (pas budget) =="
curl -sS -X PUT -H "$(auth)" -H 'Content-Type: application/json' \
  -d '{"requestsPerMinute":2,"userBudgetPerDayEur":5,"globalBudgetPerDayEur":50}' \
  "$API/api/admin/guardrails/config" >/dev/null

for i in $(seq 1 5); do
  code=$(curl -sS -o /tmp/guard-ollama.json -w '%{http_code}' -X POST -H "$(auth)" -H 'Content-Type: application/json' \
    -d "{\"content\":\"hi $i\",\"model\":\"$MODEL_LOCAL\",\"stream\":false}" \
    "$API/api/chat" || true)
  reason=$(python3 -c "import json;d=json.load(open('/tmp/guard-ollama.json'));print(d.get('reason',''))" 2>/dev/null || true)
  echo "  ollama $i → HTTP $code reason=$reason"
done
echo "  (attendu: 429 reason=rate_limit_minute, PAS budget_*)"

echo
echo "== Status =="
curl -sS -H "$(auth)" "$API/api/admin/guardrails/status"; echo
echo "Done."
