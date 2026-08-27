#!/usr/bin/env bash
# E2E smoke test — full pipeline: radar → Kafka → detection → MongoDB → REST API
# Must be run from the project root: bash e2e/e2e-smoke.sh
set -euo pipefail

TIMEOUT=60
POLL_INTERVAL=2
EXPECTED=3
API_BASE="http://localhost:38090"

cleanup() {
  echo "[e2e] Tearing down..."
  docker compose down -v
}
trap cleanup EXIT

echo "[e2e] Cleaning previous state..."
docker compose down -v

echo "[e2e] Building images and starting services..."
docker compose up --build -d

echo "[e2e] Waiting for radar-data-processing to finish publishing..."
# Use 'docker wait' instead of 'docker compose wait' because the latter
# returns exit code 1 when the container has already exited before the
# command runs — a race condition on fast CI machines.
RADAR_CONTAINER=$(docker compose ps -q radar-data-processing)
docker wait "$RADAR_CONTAINER"

# ── Step 1: MongoDB pipeline check ────────────────────────────────────────────
echo "[e2e] Polling MongoDB for $EXPECTED closed infringement events (timeout ${TIMEOUT}s)..."
elapsed=0
while true; do
  count=$(docker compose exec -T mongodb mongosh --quiet atc_safety \
    --eval "db.separation_infringement_events.countDocuments({status:'CLOSED'})" 2>/dev/null \
    | grep -E '^[0-9]+$' | tail -1 || echo "0")

  echo "[e2e] Closed events: ${count:-0} / $EXPECTED (${elapsed}s elapsed)"

  if [ "${count:-0}" -eq "$EXPECTED" ]; then
    echo "[e2e] MongoDB check PASSED — $EXPECTED closed events found"
    break
  fi

  if [ "$elapsed" -ge "$TIMEOUT" ]; then
    echo "[e2e] FAILED — timeout after ${TIMEOUT}s, found ${count:-0} / $EXPECTED events"
    exit 1
  fi

  sleep "$POLL_INTERVAL"
  elapsed=$((elapsed + POLL_INTERVAL))
done

# ── Step 2: REST API readiness ───────────────────────────────────────────────
echo "[e2e] Waiting for REST API on $API_BASE..."
api_elapsed=0
while true; do
  if curl -sf "$API_BASE/api/v1/events/summary" > /dev/null 2>&1; then
    break
  fi
  if [ "$api_elapsed" -ge "$TIMEOUT" ]; then
    echo "[e2e] FAILED — REST API did not become available within ${TIMEOUT}s"
    exit 1
  fi
  sleep "$POLL_INTERVAL"
  api_elapsed=$((api_elapsed + POLL_INTERVAL))
done

# ── Step 3: REST API summary check ──────────────────────────────────────────────
echo "[e2e] Calling GET $API_BASE/api/v1/events/summary..."
response=$(curl -sf "$API_BASE/api/v1/events/summary")
echo "[e2e] Response: $response"

pending=$(echo "$response" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['pendingCount'])")

echo "[e2e] REST API pendingCount: $pending / $EXPECTED"
if [ "$pending" -eq "$EXPECTED" ]; then
  echo "[e2e] REST API check PASSED — $EXPECTED pending events reported"
  exit 0
else
  echo "[e2e] FAILED — REST API reports $pending pending events, expected $EXPECTED"
  exit 1
fi
