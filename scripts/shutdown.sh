#!/usr/bin/env bash
set -euo pipefail

export KIND_EXPERIMENTAL_PROVIDER=podman
CLUSTER="${KIND_CLUSTER:-kind}"

echo ">>> stopping observability-stack port-forwards"
pkill -f "kubectl.*port-forward.*observability-stack" 2>/dev/null || true
pkill -f "port-forward svc/loan-" 2>/dev/null || true

if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo ">>> deleting kind cluster '$CLUSTER' (removes app + observability stack)"
  kind delete cluster --name "$CLUSTER"
else
  echo ">>> kind cluster '$CLUSTER' not found, nothing to delete"
fi

echo ">>> done"
echo "podman machine and any non-kind containers are left running."
echo "to bring it back up: ./scripts/build-images.sh && ./scripts/deploy.sh"
