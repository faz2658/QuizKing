#!/bin/bash
# Grabs a performance recording, heap dump or thread dump from a running service on this server, into
# ~/quizcadia/profiles/. Copy it to your computer and open it there (see deploy/README.md, "Profiling").
#
#   deploy/profile.sh game-engine                  last 30 minutes of the always-on flight recording (.jfr)
#   deploy/profile.sh game-engine jfr 60           record the next 60 s with detailed settings (max 300)
#   deploy/profile.sh game-engine heap             heap dump (.hprof.gz); pauses the service for a few seconds
#   deploy/profile.sh game-engine threads          what every thread is doing right now (.txt)
#
# The endpoints only answer requests from inside the service's own container, so this runs curl there.
set -euo pipefail
cd "$(dirname "$0")/.."

declare -A PORTS=([question-generator]=9090 [game-manager]=9091 [game-engine]=9092 [multiplayer-game-engine]=9093
  [adaptive-game-engine]=9094 [recommendations]=9095 [search-service]=9096 [login]=9097 [user-service]=9098
  [game-reporting]=9099 [player-matcher]=9089)

SERVICE="${1:-}"
WHAT="${2:-jfr}"
SECONDS_TO_RECORD="${3:-}"
if [ -z "$SERVICE" ] || [ -z "${PORTS[$SERVICE]:-}" ]; then
  echo "usage: $0 <service> [jfr [seconds] | heap | threads]" >&2
  echo "services: ${!PORTS[*]}" >&2
  exit 2
fi
PORT="${PORTS[$SERVICE]}"
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai)
mkdir -p profiles
STAMP="$(date -u +%Y%m%d-%H%M%S)"

fetch() { # <path> : the endpoint's answer on stdout, binary-safe
  "${COMPOSE[@]}" exec -T "$SERVICE" curl -sSf --max-time 400 "http://localhost:$PORT/actuator/$1"
}

case "$WHAT" in
  jfr)
    if [ -n "$SECONDS_TO_RECORD" ]; then
      [[ "$SECONDS_TO_RECORD" =~ ^[0-9]+$ ]] || { echo "seconds must be a number" >&2; exit 2; }
      echo "Recording $SERVICE for ${SECONDS_TO_RECORD}s..."
      OUT="profiles/$SERVICE-$STAMP-${SECONDS_TO_RECORD}s.jfr"
      fetch "jfr?seconds=$SECONDS_TO_RECORD" > "$OUT"
    else
      OUT="profiles/$SERVICE-$STAMP.jfr"
      fetch jfr > "$OUT"
    fi
    ;;
  heap)
    echo "Dumping the heap of $SERVICE (it pauses for a few seconds)..."
    OUT="profiles/$SERVICE-$STAMP.hprof.gz"
    fetch heapdump | gzip > "$OUT"
    ;;
  threads)
    OUT="profiles/$SERVICE-$STAMP-threads.txt"
    "${COMPOSE[@]}" exec -T "$SERVICE" curl -sSf -H 'Accept: text/plain' "http://localhost:$PORT/actuator/threaddump" > "$OUT"
    ;;
  *)
    echo "unknown: $WHAT (jfr, heap or threads)" >&2
    exit 2
    ;;
esac
chmod 600 "$OUT" # heap dumps hold secrets
echo "Saved $(du -h "$OUT" | cut -f1) to ~/quizcadia/$OUT"
echo "Copy it with:  scp quizking:quizcadia/$OUT ."
