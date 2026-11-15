# Deploying Quiz King (Oracle Cloud Always Free)

Live: https://40-233-75-213.sslip.io (Ampere A1, 4 OCPU / 12 GB, Ubuntu 24.04, Toronto).

## Server setup (once)
1. Ubuntu 24.04 on `VM.Standard.A1.Flex`, public subnet, ingress TCP 80 and 443 in the security list.
2. `sudo apt-get install docker.io docker-compose-v2 docker-buildx openjdk-21-jdk-headless`
3. Oracle images reject everything but SSH in iptables: insert ACCEPT rules for 80/443 before the REJECT
   rule and `sudo netfilter-persistent save`.
4. A 4 GB swap file as a memory safety net.

## Deploy / update
```bash
# on your machine: send the source (jars are built on the server, it has fast downloads)
git archive --format=tar.gz --prefix=quiz-king/ HEAD | ssh quizking 'tar -xzf -'
ssh quizking 'cd quiz-king && ./mvnw -B -q -DskipTests package && \
  sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai up -d --build'
```
`.env` on the server (never committed) holds `SITE_ADDRESS`, `CORS_ALLOWED_ORIGINS=https://<site>`,
`MAVERICK_JWT_SECRET`, `MAVERICK_ADMIN_EMAILS`, `MYSQL_PASSWORD`, `NEO4J_PASSWORD`, `GRAFANA_PASSWORD` and
`AI_MODEL_URL=http://question-model:8080` (with the GGUF in `ai/question-model/models/`).

`docker-compose.prod.yml` puts Caddy (automatic Let's Encrypt HTTPS) in front and closes every other port.

## Spending guard ($20/month)
- Oracle budget `quiz-king-monthly-cap`: $20 a month, email alert at 100%.
- Dynamic group `quiz-king-server` (this instance only) and policy `quiz-king-budget-guard`:
  read `usage-budgets`, use this instance.
- `budget-guard/`: an hourly systemd timer that reads the budget with the instance's own identity and,
  at $20, stops the containers and soft-stops the server. Logs: `/var/log/quizking-budget-guard.log`.
  Test without stopping anything: `sudo LIMIT=0 quizking-budget-guard --dry-run`.
