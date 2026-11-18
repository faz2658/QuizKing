#!/bin/bash
# The master queue: a merge to master waits here, in merge order, before CI builds it. It goes ahead once
#   1. every earlier CI run on master has finished, and
#   2. no production deploy is running, including the one that follows the last earlier CI run that passed.
# So builds and deploys of master happen one after the other, oldest merge first, and nothing is cancelled.
# (A concurrency group can't do this: it keeps one waiting run and cancels the rest.)
#
#   QUEUE_REPO=owner/repo QUEUE_RUN_ID=<this run's id> GITHUB_TOKEN=... master-queue.sh
# Optional: QUEUE_BRANCH (master), QUEUE_POLL_SECONDS (30), QUEUE_DEPLOY_GRACE_SECONDS (900: how long after an earlier
# run passed its deploy may still be about to start), QUEUE_ONCE=1 (check once and print, for testing).
set -euo pipefail
REPO="${QUEUE_REPO:?QUEUE_REPO}"
RUN_ID="${QUEUE_RUN_ID:?QUEUE_RUN_ID}"
BRANCH="${QUEUE_BRANCH:-master}"
POLL="${QUEUE_POLL_SECONDS:-30}"
GRACE="${QUEUE_DEPLOY_GRACE_SECONDS:-900}"

api() {
  local auth=()
  [ -n "${GITHUB_TOKEN:-}" ] && auth=(-H "Authorization: Bearer $GITHUB_TOKEN")
  curl -fsSL --retry 3 ${auth[@]+"${auth[@]}"} -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$REPO/$1"
}

# reasons <ci runs json> <deploy runs json>: why this run must wait, one per line; nothing when it may go.
reasons() {
  local ci="$1" deploys="$2" now
  now="$(date -u +%s)"
  jq -r --argjson me "$RUN_ID" '
    .workflow_runs[] | select(.id < $me and .status != "completed")
    | "CI is still building an earlier merge: \(.head_sha[0:8]) \(.display_title) (\(.html_url))"' <<<"$ci"
  jq -r '.workflow_runs[] | select(.status != "completed")
    | "A production deploy is running: \(.display_title) (\(.html_url))"' <<<"$deploys"
  # The deploy of the last earlier run that passed starts a few seconds after it: wait for it to exist and finish
  jq -r --argjson me "$RUN_ID" --argjson now "$now" --argjson grace "$GRACE" --argjson deploys "$deploys" '
    [.workflow_runs[] | select(.id < $me and .status == "completed" and .conclusion == "success")]
    | max_by(.id) // empty
    | . as $ok
    | select(($now - (.updated_at | fromdateiso8601)) < $grace)
    | select([$deploys.workflow_runs[] | select(.status == "completed"
               and ((.display_title | contains($ok.head_sha))
                    or ((.created_at | fromdateiso8601) >= ($ok.updated_at | fromdateiso8601) - 60)))]
             | length == 0)
    | "Waiting for the deploy of \($ok.head_sha[0:8]) (\($ok.display_title)) to start and finish"' <<<"$ci"
}

started="$(date -u +%s)"
while true; do
  if ! ci="$(api "actions/workflows/ci.yml/runs?branch=$BRANCH&event=push&per_page=50")" \
      || ! deploys="$(api "actions/workflows/deploy.yml/runs?per_page=30")"; then
    echo "[$(date -u +%H:%M:%S)] GitHub API did not answer, trying again"
    [ "${QUEUE_ONCE:-0}" = 1 ] && exit 1
    sleep "$POLL"
    continue
  fi
  why="$(reasons "$ci" "$deploys")"
  if [ -z "$why" ]; then
    echo "Our turn after $(( $(date -u +%s) - started ))s: building."
    exit 0
  fi
  echo "[$(date -u +%H:%M:%S)] waiting:"
  sed 's/^/  - /' <<<"$why"
  [ "${QUEUE_ONCE:-0}" = 1 ] && exit 0
  sleep "$POLL"
done
