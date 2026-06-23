#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLUSTER="${KIND_CLUSTER:-kind}"
OTEL_AGENT_VERSION="${OTEL_AGENT_VERSION:-2.29.0}"

cd "$ROOT/backend"
sbt -batch assembly
cp "$(ls target/scala-3.7.2/*assembly*.jar | head -1)" target/scala-3.7.2/loan-backend-assembly.jar
curl -sL -o target/otel-agent.jar \
  "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"
podman build -t loan-backend:dev .

cd "$ROOT/frontend"
npm ci
node ./node_modules/vite/bin/vite.js build
podman build -t loan-frontend:dev .

podman save loan-backend:dev -o /tmp/loan-backend.tar
podman save loan-frontend:dev -o /tmp/loan-frontend.tar
kind load image-archive /tmp/loan-backend.tar --name "$CLUSTER"
kind load image-archive /tmp/loan-frontend.tar --name "$CLUSTER"
echo "images built and loaded into kind cluster '$CLUSTER'"
