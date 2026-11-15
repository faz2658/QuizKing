#!/bin/bash
# Runs on the live server (from .github/workflows/deploy.yml, or by hand) after a release was unpacked into
# ~/quiz-king-next: swaps in the new code, builds the jars, rebuilds and restarts the containers, and waits
# until the site answers. Keeps the server's own .env, the AI model and the databases (Docker volumes).
set -euo pipefail
COMMIT="${1:-manual}"
NEXT="$HOME/quiz-king-next"
APP="$HOME/quiz-king"
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai)

echo "==> Deploying $COMMIT"
rsync -a --delete \
  --exclude .env --exclude 'ai/question-model/models/' --exclude 'target/' --exclude 'frontend/node_modules/' \
  --exclude DEPLOYED_COMMIT \
  "$NEXT/" "$APP/"
rm -rf "$NEXT"

cd "$APP"
echo "==> Building jars"
chmod +x mvnw deploy/deploy.sh
./mvnw -B -q -DskipTests package

echo "==> Rebuilding and restarting containers"
"${COMPOSE[@]}" up -d --build --remove-orphans
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
