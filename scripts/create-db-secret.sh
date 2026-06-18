#!/usr/bin/env bash
set -euo pipefail

CTX=kind-kind
NS=observability-stack

kubectl config get-contexts "$CTX" >/dev/null 2>&1 || { echo "context $CTX not found; create the kind cluster first"; exit 1; }
kubectl --context "$CTX" create namespace "$NS" --dry-run=client -o yaml | kubectl --context "$CTX" apply -f -

read -rs -p "DB password: " PW
echo
kubectl --context "$CTX" -n "$NS" create secret generic loan-db \
  --from-literal=password="$PW" \
  --dry-run=client -o yaml | kubectl --context "$CTX" -n "$NS" apply -f -
echo "secret loan-db created in $NS"
