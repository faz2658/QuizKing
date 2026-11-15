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
pull request is merged, and from the Actions tab by hand. It uploads the commit CI tested and runs
`deploy/deploy.sh` on the server: swap in the code (keeping `.env`, the AI model and the databases), build the
jars, rebuild and restart the containers, and wait until the site answers (the run fails if it doesn't).

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
```bash
# on your machine: send the source (jars are built on the server, it has fast downloads)
git archive --format=tar.gz --prefix=quiz-king/ HEAD | ssh quizking 'tar -xzf -'
ssh quizking 'cd quiz-king && ./mvnw -B -q -DskipTests package && \
  sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai up -d --build'
```
`.env` on the server (never committed) holds `SITE_ADDRESS`, `CORS_ALLOWED_ORIGINS=https://<site>`,
`QUIZKING_JWT_SECRET`, `QUIZKING_ADMIN_EMAILS`, `MYSQL_PASSWORD`, `NEO4J_PASSWORD`, `GRAFANA_PASSWORD` and
`AI_MODEL_URL=http://question-model:8080` (with the GGUF in `ai/question-model/models/`).

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
