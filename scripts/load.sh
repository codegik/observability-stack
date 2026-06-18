#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
N="${N:-50}"
PURPOSES=(HOME_IMPROVEMENT DEBT_CONSOLIDATION AUTO MEDICAL EDUCATION OTHER)

for i in $(seq 1 "$N"); do
  uid="load-user-$((RANDOM % 10))"
  cid="load-corr-${i}-${RANDOM}"
  hdr=(-H "X-Correlation-Id: $cid" -H "X-User-Id: $uid" -H "Content-Type: application/json")
  purpose=${PURPOSES[$((RANDOM % 6))]}
  amount=$(( (RANDOM % 40000) + 2000 ))
  term=$(( ((RANDOM % 6) + 1) * 12 ))
  income=$(( (RANDOM % 8000) + 1500 ))

  lr=$(curl -s "${hdr[@]}" -d "{\"amount\":$amount,\"termMonths\":$term,\"purpose\":\"$purpose\"}" "$BASE/api/loan-requests")
  rid=$(echo "$lr" | sed -E 's/.*"id":"([^"]+)".*/\1/')
  curl -s "${hdr[@]}" -d "{\"email\":\"${uid}@test.dev\",\"displayName\":\"Load $uid\",\"monthlyIncome\":$income}" "$BASE/api/users" >/dev/null
  curl -s "${hdr[@]}" "$BASE/api/loan-requests/$rid/offers" >/dev/null

  echo "journey $i: purpose=$purpose amount=$amount term=$term income=$income request=$rid"
done
