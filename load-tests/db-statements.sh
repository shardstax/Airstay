#!/usr/bin/env bash
# Counts the SQL statements sent to PostgreSQL while an operation runs. Needs the bench override (see
# load-tests/docker-compose.bench.yml). Run from the repository root.
#
#   bash load-tests/db-statements.sh enable   # create the extension (once per database)
#   bash load-tests/db-statements.sh reset    # start counting from zero
#   ... run the operation ...
#   bash load-tests/db-statements.sh show     # statements sent, database time, and the most frequent statements
#
# "statements" counts what the application sent (top-level statements only, so the checks PostgreSQL runs internally for
# foreign keys are not included), including BEGIN and COMMIT; the outbox relay's background polling is left out. Database time is time spent inside
# PostgreSQL only; the rest of a request (Java, network, JSON) is not included.
set -euo pipefail

psql_airstay() { docker compose exec -T postgres psql -U airstay -d airstay -P pager=off "$@"; }

case "${1:-}" in
  enable)
    psql_airstay -qc "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" ;;
  reset)
    psql_airstay -qAt -c "SELECT pg_stat_statements_reset()" >/dev/null && echo "statement counters reset" ;;
  show)
    psql_airstay -c "
      SELECT sum(calls) AS statements, round(sum(total_exec_time)::numeric) AS database_ms, sum(rows) AS rows
      FROM pg_stat_statements
      WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'airstay')
        AND query NOT ILIKE '%pg_stat_statements%' AND query NOT ILIKE '%outbox_event%'"
    psql_airstay -c "
      SELECT calls, round(total_exec_time::numeric) AS ms, rows, left(regexp_replace(query, '\s+', ' ', 'g'), 95) AS statement
      FROM pg_stat_statements
      WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'airstay')
        AND query NOT ILIKE '%pg_stat_statements%' AND query NOT ILIKE '%outbox_event%'
      ORDER BY calls DESC LIMIT 8" ;;
  *)
    echo "usage: $0 enable|reset|show" >&2; exit 2 ;;
esac
