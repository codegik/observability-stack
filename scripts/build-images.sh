#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLUSTER="${KIND_CLUSTER:-kind}"
OTEL_AGENT_VERSION="${OTEL_AGENT_VERSION:-2.29.0}"

# Auto-detect container tool, prefer docker if available
CONTAINER_TOOL="${CONTAINER_TOOL:-}"
if [ -z "$CONTAINER_TOOL" ]; then
  if command -v docker >/dev/null 2>&1; then
    CONTAINER_TOOL="docker"
  elif command -v podman >/dev/null 2>&1; then
    CONTAINER_TOOL="podman"
  else
    echo "Error: Neither docker nor podman is installed or in PATH." >&2
    exit 1
  fi
fi

echo "Using container tool: $CONTAINER_TOOL"

# Auto-detect container tool, prefer docker if available
CONTAINER_TOOL="${CONTAINER_TOOL:-}"
if [ -z "$CONTAINER_TOOL" ]; then
  if command -v docker >/dev/null 2>&1; then
    CONTAINER_TOOL="docker"
  elif command -v podman >/dev/null 2>&1; then
    CONTAINER_TOOL="podman"
  else
    echo "Error: Neither docker nor podman is installed or in PATH." >&2
    exit 1
  fi
fi

echo "Using container tool: $CONTAINER_TOOL"

cd "$ROOT/backend"
sbt -batch assembly
cp "$(ls target/scala-3.7.2/*assembly*.jar | head -1)" target/scala-3.7.2/loan-backend-assembly.jar
curl -sL -o target/otel-agent.jar \
  "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"
"$CONTAINER_TOOL" build -t localhost/loan-backend:dev .

cd "$ROOT/frontend"
npm ci
node ./node_modules/vite/bin/vite.js build
"$CONTAINER_TOOL" build -t localhost/loan-frontend:dev .

if [ "$CONTAINER_TOOL" = "docker" ]; then
  echo "Loading images into kind cluster '$CLUSTER' using docker-image..."
  kind load docker-image localhost/loan-backend:dev --name "$CLUSTER"
  kind load docker-image localhost/loan-frontend:dev --name "$CLUSTER"
else
  echo "Loading images into kind cluster '$CLUSTER' using image-archive (via temporary tarballs)..."
  "$CONTAINER_TOOL" save localhost/loan-backend:dev -o /tmp/loan-backend.tar
  "$CONTAINER_TOOL" save localhost/loan-frontend:dev -o /tmp/loan-frontend.tar
  kind load image-archive /tmp/loan-backend.tar --name "$CLUSTER"
  kind load image-archive /tmp/loan-frontend.tar --name "$CLUSTER"
  rm -f /tmp/loan-backend.tar /tmp/loan-frontend.tar
fi

echo "images built and loaded into kind cluster '$CLUSTER'"
