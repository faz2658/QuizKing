#!/bin/bash
# Runs on the live server (from .github/workflows/deploy.yml) after a release was unpacked into ~/quiz-king-next.
# Swaps in the new compose files, pulls the images CI built for this commit and restarts only the services whose
# image changed, then waits until the site answers. Keeps the server's own .env, the AI model and the databases
# (Docker volumes). Nothing is compiled here.
#
#   deploy.sh <commit> <registry> <registry-user>   with a registry token on stdin (what the workflow does)
#   BUILD_ON_SERVER=1 deploy.sh manual              emergency fallback: build the images on this server
set -euo pipefail
COMMIT="${1:-manual}"
REGISTRY="${2:-ghcr.io/faz2658/quizking}"
REGISTRY_USER="${3:-faz2658}"
TOKEN=""
if [ ! -t 0 ]; then TOKEN="$(cat)"; fi
NEXT="$HOME/quiz-king-next"
APP="$HOME/quiz-king"
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai)
APP_SERVICES=(discovery config question-generator game-manager game-engine multiplayer-game-engine
  adaptive-game-engine player-matcher recommendations search-service login user-service game-reporting frontend)

echo "==> Deploying $COMMIT"
if [ -d "$NEXT" ]; then
  rsync -a --delete \
    --exclude .env --exclude 'secrets/' --exclude 'ai/question-model/models/' --exclude 'target/' \
    --exclude 'frontend/node_modules/' \
    --exclude DEPLOYED_COMMIT \
    "$NEXT/" "$APP/"
  rm -rf "$NEXT"
fi
cd "$APP"
chmod +x mvnw deploy/*.sh

# Remember which images are live, so a manual "docker compose up -d" later uses the same ones.
set_env() { if grep -q "^$1=" .env; then sed -i "s#^$1=.*#$1=$2#" .env; else echo "$1=$2" >> .env; fi; }

if [ "${BUILD_ON_SERVER:-0}" = 1 ]; then
  echo "==> Building jars and images on this server (fallback)"
  set_env IMAGE_REGISTRY quizking-local
  set_env IMAGE_TAG "$COMMIT"
  ./mvnw -B -q -DskipTests package
  DOCKER="sudo docker" bash deploy/rename-migration.sh before
  "${COMPOSE[@]}" up -d --build --remove-orphans
else
  set_env IMAGE_REGISTRY "$REGISTRY"
  set_env IMAGE_TAG "$COMMIT"
  if [ -n "$TOKEN" ]; then
    printf '%s' "$TOKEN" | sudo docker login "${REGISTRY%%/*}" -u "$REGISTRY_USER" --password-stdin >/dev/null
  fi
  echo "==> Pulling images for $COMMIT"
  "${COMPOSE[@]}" pull --quiet "${APP_SERVICES[@]}"
  [ -n "$TOKEN" ] && sudo docker logout "${REGISTRY%%/*}" >/dev/null || true
  DOCKER="sudo docker" bash deploy/rename-migration.sh before
  echo "==> Restarting the services that changed"
  "${COMPOSE[@]}" up -d --no-build --remove-orphans
fi
DOCKER="sudo docker" bash deploy/rename-migration.sh after
sudo docker image prune -f >/dev/null

SITE=$(grep -E '^SITE_ADDRESS=' .env | cut -d= -f2-)
echo "==> Waiting for https://$SITE"
for i in $(seq 1 60); do
  if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$SITE/api/game/games")" = 200 ]; then
    echo "$COMMIT" > "$APP/DEPLOYED_COMMIT"
    echo "==> Live: https://$SITE ($COMMIT)"
    exit 0
  fi
  sleep 5
done
echo "!! The site did not come back within 5 minutes" >&2
"${COMPOSE[@]}" ps >&2
exit 1
