# Deploying Quiz King (Oracle Cloud Always Free)

Live: https://40-233-75-213.sslip.io (Ampere A1, 4 OCPU / 12 GB, Ubuntu 24.04, Toronto).

## Server setup (once)
1. Ubuntu 24.04 on `VM.Standard.A1.Flex`, public subnet, ingress TCP 80 and 443 in the security list.
2. `sudo apt-get install docker.io docker-compose-v2 docker-buildx openjdk-21-jdk-headless`
3. Oracle images reject everything but SSH in iptables: insert ACCEPT rules for 80/443 before the REJECT
   rule and `sudo netfilter-persistent save`.
4. A 4 GB swap file as a memory safety net.

## Continuous deployment (GitHub Actions)
`.github/workflows/deploy.yml` deploys automatically after CI passes on `master` (or `main`), that is, after a
pull request is merged, and from the Actions tab by hand:

1. **Build images** (on GitHub's ARM runner, the same architecture as the server): builds the jars and every app
   image for the commit CI tested and pushes them to `ghcr.io/faz2658/quizking/<service>:<commit>`.
2. **Deploy**: copies the compose files to the server and runs `deploy/deploy.sh`, which records the image tag
   in `.env` (`IMAGE_REGISTRY`, `IMAGE_TAG`), pulls the new images with the job's short-lived token, restarts
   only the services whose image changed, and waits until the site answers (the run fails if it doesn't).

Nothing is compiled on the live server any more, so a deploy no longer eats its CPU and memory. Keeps `.env`,
the AI model and the databases. Emergency fallback when GitHub is unavailable: `BUILD_ON_SERVER=1 bash
deploy/deploy.sh manual` builds on the server the old way.

One-time setup:
1. Make a key just for deployments and allow it on the server:
   ```bash
   ssh-keygen -t ed25519 -N '' -C github-actions-deploy -f ~/.ssh/quizking_deploy
   ssh-copy-id -i ~/.ssh/quizking_deploy.pub -o IdentityFile=~/.ssh/quizking_oracle ubuntu@40.233.75.213
   ```
2. In GitHub: Settings > Secrets and variables > Actions > New repository secret:
   - `ORACLE_SSH_KEY`: the private key (`pbcopy < ~/.ssh/quizking_deploy`, then paste)
   - `ORACLE_HOST`: `40.233.75.213`
   - `ORACLE_KNOWN_HOSTS`: the output of `ssh-keyscan -t ed25519 40.233.75.213`
3. Optional: Settings > Environments > `production` can require an approval before each deploy.

## Deploy / update by hand
Run the Deploy workflow from the Actions tab. To restart the live images without a new release:
```bash
ssh quizking 'cd quiz-king && sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai up -d'
```

## Website analytics on the admin page
Admin > Website analytics reads Google Analytics with a read-only service account:
1. Google Cloud (project quiz-king-514821): enable the **Google Analytics Data API**, create a service account
   (no roles needed) and add a JSON key; the browser downloads it.
2. Google Analytics: Admin > Property access management > add the service account's email as **Viewer**.
3. Copy the key to the server and point the settings at it:
   ```bash
   ssh quizking 'mkdir -p ~/quiz-king/secrets && chmod 700 ~/quiz-king/secrets'
   scp ~/Downloads/<key-file>.json quizking:quiz-king/secrets/ga-service-account.json
   ssh quizking 'chmod 644 ~/quiz-king/secrets/ga-service-account.json'   # the container reads it as uid 1000
   ```
   In the server's `.env`: `GA_PROPERTY_ID=564461039` and `GA_CREDENTIALS_FILE=/run/secrets/ga-service-account.json`,
   then restart game-reporting. `secrets/` is never committed and deploys leave it alone.

## Memory
Every container has a memory ceiling (`mem_limit` in `docker-compose.yml`) and no swap of its own, and Java
heaps are 60% of their container. A service that runs out restarts on its own instead of pushing the whole
server into swap (which froze it for 20 minutes on 16 Nov 2026). Most Java services get 640 MB
(`JAVA_MEM_LIMIT`), game-manager, game-engine and game-reporting 896 MB (`HEAVY_MEM_LIMIT`); MongoDB's cache
is 0.5 GB, Kafka's heap 384 MB, and the AI model 3 GB (its 2.5 GB file stays loaded). Under a 100-player
load test the whole stack peaked at about 10 GB of the server's 12 GB.
`.env` on the server (never committed) holds `SITE_ADDRESS`, `CORS_ALLOWED_ORIGINS=https://<site>`,
`QUIZKING_JWT_SECRET`, `QUIZKING_ADMIN_EMAILS`, `MYSQL_PASSWORD`, `NEO4J_PASSWORD`, `GRAFANA_PASSWORD` and
`AI_MODEL_URL=http://question-model:8080` (with the GGUF in `ai/question-model/models/`), and optionally
`GOOGLE_CLIENT_ID`, `GA_MEASUREMENT_ID` and `MAIL_*`.

`docker-compose.prod.yml` puts Caddy (automatic Let's Encrypt HTTPS) in front and closes every other port.

## Spending guard ($20/month)
- Oracle budget `quiz-king-monthly-cap`: $20 a month, email alert at 100%.
- Dynamic group `quiz-king-server` (this instance only) and policy `quiz-king-budget-guard`:
  read `usage-budgets`, use this instance.
- `budget-guard/`: an hourly systemd timer that reads the budget with the instance's own identity and,
  at $20, stops the containers and soft-stops the server. Logs: `/var/log/quizking-budget-guard.log`.
  Test without stopping anything: `sudo LIMIT=0 quizking-budget-guard --dry-run`.

## The rename to Quiz King (one-time)

`deploy.sh` runs `deploy/rename-migration.sh` around the restart. On a machine still running the stack under
its old name it stops the old containers, copies the database and certificate volumes to `quiz-king_*`
volumes, renames the old settings in `.env` to `QUIZKING_*`, and pins the existing database login
(`MYSQL_USER`/`MYSQL_PASSWORD`/`NEO4J_PASSWORD`), which lives inside the data. On a laptop run it by hand:

    bash deploy/rename-migration.sh before && docker compose --profile ai up -d --build && bash deploy/rename-migration.sh after

The old volumes are kept as a backup. Once everything works, delete them (`docker volume ls` shows them)
and delete `rename-migration.sh`.
