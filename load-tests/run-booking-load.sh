#!/usr/bin/env bash
# One booking load run: k6 flow -> overbooking check in the database -> resource snapshot. Run from the repository root.
# The app must run with PAYMENTS_FAKE=true RATE_LIMIT_ENABLED=false (see load-tests/README.md section 6).
#
#   bash load-tests/run-booking-load.sh mixed       RATE=10 LABEL=mixed-10
#   bash load-tests/run-booking-load.sh contention  VUS=100 LABEL=contention
#
# Remaining arguments are k6 environment variables (KEY=value). The exit code is non-zero if k6 reports an error,
# a server error / unexpected answer happened, or the database shows overbooking.
set -euo pipefail
export MSYS_NO_PATHCONV=1

MODE="${1:?usage: run-booking-load.sh mixed|contention [KEY=value ...]}"; shift
RUN_ID="$(date +%s)"
ENVS=(-e "MODE=$MODE" -e "RUN_ID=$RUN_ID" -e "BASE_URL=http://app:8080/api/v1")
WEBHOOK_SECRET="$(docker compose exec -T app printenv STRIPE_WEBHOOK_SECRET | tr -d '\r\n')"
ENVS+=(-e "STRIPE_WEBHOOK_SECRET=$WEBHOOK_SECRET")
for kv in "$@"; do ENVS+=(-e "$kv"); done
mkdir -p load-tests/results

psql_airstay() { docker compose exec -T postgres psql -U airstay -d airstay -P pager=off "$@"; }

# Resource sampler: pool and database activity once a second while k6 runs.
SAMPLES="load-tests/results/.samples-$RUN_ID.csv"
( while true; do
    psql_airstay -qAt -F, -c "SELECT now()::time(0), count(*) FILTER (WHERE state='active'), count(*) FILTER (WHERE wait_event_type='Lock'), count(*) FROM pg_stat_activity WHERE datname='airstay'" 2>/dev/null >>"$SAMPLES" || true
    sleep 1
  done ) & SAMPLER=$!
STATS="load-tests/results/.docker-stats-$RUN_ID.txt"
( while true; do docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' >>"$STATS" 2>/dev/null || true; sleep 5; done ) & STATSPID=$!
trap 'kill $SAMPLER $STATSPID 2>/dev/null || true' EXIT

K6_STATUS=0
docker run --rm -i --network airstay_default "${ENVS[@]}" -v "$PWD/load-tests:/scripts" grafana/k6:latest run /scripts/booking-flow.js || K6_STATUS=$?
kill $SAMPLER $STATSPID 2>/dev/null || true

echo; echo "== Database check (run $RUN_ID) =="
FAIL=0
VERIFY="$(psql_airstay -v run="$RUN_ID" < load-tests/verify-no-overbooking.sql)" || { echo "database check could not run"; VERIFY=""; FAIL=1; }
echo "$VERIFY"
# Line 3 of the first result is the data row: hotels | nights | oversold | counters differ | negative | ...
read -r HOTELS NIGHTS BAD <<<"$(echo "$VERIFY" | awk -F'|' 'NR==3 {gsub(/ /,""); print $1, $2, ($3+$4+$5)}')"
if [ -z "${HOTELS:-}" ] || [ "${HOTELS:-0}" -eq 0 ] || [ "${NIGHTS:-0}" -eq 0 ]; then
  echo "NOTHING WAS CHECKED: no hotels or inventory nights found for this run (a pass here would mean nothing)"; FAIL=1
fi
if echo "$VERIFY" | grep -qE '^ *(CONFIRMED|RESERVED|GUESTS_ADDED|PAYMENTS_PENDING|CANCELLED|EXPIRED) '; then :; else
  echo "NOTHING WAS CHECKED: the run created no bookings"; FAIL=1
fi

echo; echo "== Peak database activity (active / waiting on a lock / total connections) =="
if [ -s "$SAMPLES" ]; then
  sort -t, -k2 -n -r "$SAMPLES" | head -1 | awk -F, '{print "peak active "$2", peak total "$4}' || true
  sort -t, -k3 -n -r "$SAMPLES" | head -1 | awk -F, '{print "peak waiting on a lock "$3}' || true
else echo "(no samples were collected)"; fi
echo "== Container CPU/memory, highest samples =="
{ grep -E '^airstay-(app|postgres)' "$STATS" | sort -t= -k2 -n -r | head -4; } 2>/dev/null || true

[ "$K6_STATUS" -ne 0 ] && { echo "k6 exited with $K6_STATUS"; FAIL=1; }
[ "${BAD:-1}" != "0" ] && { echo "OVERBOOKING OR COUNTER DRIFT DETECTED (violations: ${BAD:-unknown})"; FAIL=1; }
echo "run id $RUN_ID  (clean up: docker compose exec -T postgres psql -U airstay -d airstay -v run=$RUN_ID < load-tests/unload.sql)"
exit $FAIL
