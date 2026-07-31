#!/usr/bin/env bash
set -euo pipefail

if ! kind get clusters | grep -qx "kind"; then
  kind create cluster --name kind --config deploy/kind-cluster.yaml
else
  echo "Cluster 'kind' already exists, skipping creation."
fi

./scripts/build-images.sh

./scripts/deploy.sh

 kubectl config set-context --current --namespace=observability-stack
