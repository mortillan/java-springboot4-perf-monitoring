#!/usr/bin/env bash
#
# Runs the app with the OpenTelemetry Java agent attached, so every HTTP request
# and every JDBC query becomes a span in a distributed trace.
#
#   - With Grafana Cloud creds (in .env)  -> traces are sent to Grafana Cloud (Tempo)
#   - Without creds                       -> traces are printed to the console,
#                                            so you can verify instrumentation works
#
# Usage:
#   ./scripts/run-with-tracing.sh                          # uses .env if present
#   ./scripts/run-with-tracing.sh --demo.sales-count=15000 # extra app args pass through
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"

VANILLA_AGENT="third_party/opentelemetry-javaagent.jar"
AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar"

# Prefer the Grafana OpenTelemetry Java distribution if it's present (this is the
# jar Grafana Cloud tells you to download). Fall back to the upstream OTel agent.
if [ -f "grafana-opentelemetry-java.jar" ]; then
  AGENT="grafana-opentelemetry-java.jar"
elif [ -f "third_party/grafana-opentelemetry-java.jar" ]; then
  AGENT="third_party/grafana-opentelemetry-java.jar"
else
  AGENT="$VANILLA_AGENT"
fi
echo "Using agent: $AGENT"

JAR="$(ls target/*-SNAPSHOT.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  echo "No jar found in target/. Build first:  ./mvnw -DskipTests package" >&2
  exit 1
fi

# Download the upstream OTel Java agent once (only if no agent is available).
if [ ! -f "$AGENT" ]; then
  echo "Downloading OpenTelemetry Java agent..."
  mkdir -p third_party
  curl -fsSL "$AGENT_URL" -o "$AGENT"
fi

# Load Grafana Cloud (or any OTLP) settings if provided.
if [ -f ".env" ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# Common resource attributes -> show up as labels in Grafana Cloud Traces.
export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-bottleneck-springboot}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-deployment.environment=brownbag-demo}"
# Sample everything for the demo (don't do this in production).
export OTEL_TRACES_SAMPLER="${OTEL_TRACES_SAMPLER:-always_on}"
# The N+1 endpoint and startup seeding emit thousands of JDBC spans. Grow the
# batch queue so none get dropped and the full fan-out shows up in Grafana.
export OTEL_BSP_MAX_QUEUE_SIZE="${OTEL_BSP_MAX_QUEUE_SIZE:-20000}"
export OTEL_BSP_MAX_EXPORT_BATCH_SIZE="${OTEL_BSP_MAX_EXPORT_BATCH_SIZE:-2048}"

if [ -n "${OTEL_EXPORTER_OTLP_ENDPOINT:-}" ]; then
  # Grafana Cloud's OTLP gateway speaks HTTP/protobuf.
  export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-http/protobuf}"
  # Leave metrics/logs exporters at the agent's defaults so the Grafana
  # distribution ships full telemetry (traces + metrics + logs) to your stack.
  echo "Tracing -> OTLP endpoint: $OTEL_EXPORTER_OTLP_ENDPOINT (service=$OTEL_SERVICE_NAME)"
else
  # No endpoint configured: print spans to the console so you can SEE them.
  export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-console}"
  export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-none}"
  export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-none}"
  echo "No OTEL_EXPORTER_OTLP_ENDPOINT set -> printing spans to console (local verify mode)."
  echo "Add Grafana Cloud creds to .env (see .env.example) to ship traces to Tempo."
fi

exec java -javaagent:"$AGENT" -jar "$JAR" "$@"
