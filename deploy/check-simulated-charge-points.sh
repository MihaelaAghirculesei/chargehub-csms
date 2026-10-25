#!/usr/bin/env bash
# Checks the definition of done of Phase 2 on a running `sim` profile: every simulated charge
# point is accepted, and none of them reconnects while we watch.
#
#   docker compose -f deploy/docker-compose.yml --profile sim up --detach --wait
#   deploy/check-simulated-charge-points.sh [expected charge points] [seconds to watch]
set -euo pipefail

expected=${1:-100}
watch_seconds=${2:-60}
compose=(docker compose -f "$(dirname "$0")/docker-compose.yml" --profile sim)

accepted() {
  "${compose[@]}" logs simulator | grep -oE 'Charge point [^ ]+ accepted' | sort -u | wc -l
}

# grep -c prints 0 but fails when nothing matches.
reconnects() {
  "${compose[@]}" logs simulator | grep -c 'connects again' || true
}

for _ in $(seq 1 60); do
  [ "$(accepted)" -ge "$expected" ] && break
  sleep 2
done
if [ "$(accepted)" -lt "$expected" ]; then
  echo "Only $(accepted) of $expected charge points were accepted" >&2
  exit 1
fi
echo "All $expected charge points accepted"

before=$(reconnects)
sleep "$watch_seconds"
after=$(reconnects)
if [ "$after" -gt "$before" ]; then
  echo "$((after - before)) reconnections in $watch_seconds seconds" >&2
  exit 1
fi
echo "No reconnection in $watch_seconds seconds"
