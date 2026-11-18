# Deploying QuizCadia (Oracle Cloud Always Free)

Live: https://quizcadia.com (Ampere A1, 4 OCPU / 12 GB, Ubuntu 24.04, Toronto). The old address
https://40-233-75-213.sslip.io and www.quizcadia.com redirect there.

## Domain
`quizcadia.com` is registered at Cloudflare. DNS: `A @` and `A www` to `40.233.75.213`, both **DNS only** (grey
cloud) so Caddy gets its own Let's Encrypt certificates. Server `.env`: `SITE_ADDRESS=quizcadia.com` (the one name
used in links and emails), `REDIRECT_ADDRESSES=www.quizcadia.com, 40-233-75-213.sslip.io`, and
`CORS_ALLOWED_ORIGINS=https://quizcadia.com`.

## Server setup (once)
1. Ubuntu 24.04 on `VM.Standard.A1.Flex`, public subnet, ingress TCP 80 and 443 in the security list.
2. `sudo apt-get install docker.io docker-compose-v2 docker-buildx openjdk-21-jdk-headless`
3. Oracle images reject everything but SSH in iptables: insert ACCEPT rules for 80/443 before the REJECT
   rule and `sudo netfilter-persistent save`.
4. A 4 GB swap file as a memory safety net.

## Caching

- **App files:** the build files have a hash in their names, so they are cached for a year (`immutable`).
  `index.html`, `sw.js`, the manifest, icons and the sitemap are `no-cache`: browsers re-check them each time and
  get a cheap `304`. The service worker keeps the app shell for offline use. It never caches `/api`.
- **API:** answers are `no-store` (Spring Security's default) except the lists that are the same for everyone:
  - `/api/game/categories`, `/countries`, `/games` and `/games/mp`: re-checked by browsers through an ETag, kept
    30 s by a CDN.
  - `/api/v1/question-generator/stats`: 10 s.
  - `/api/v1/auth/providers`: 5 minutes.
- **On the servers:** the game manager builds the catalog lists once per catalog change, or once a minute. The game
  engines keep each topic's questions for 10 minutes, and the leaderboards are cached in the reporting service. The
  welcome page's question counts are counted by MongoDB and kept for 10 s.

## CDN (optional, Cloudflare)

The server is ready to sit behind a CDN, but nothing needs one at today's traffic. With Cloudflare's free plan:

1. Add the domain to Cloudflare and switch the `quizcadia.com` and `www` DNS records to **Proxied** (orange cloud).
2. **SSL/TLS → Full (strict)**: Caddy keeps its own Let's Encrypt certificate. Leave **Always Use HTTPS** off.
   Caddy already redirects to https, and Cloudflare's redirect would block Caddy's certificate renewals (the
   HTTP-01 challenge on port 80).
3. Let the server see visitors' real addresses (for the sign-in rate limit and the logs). Add Cloudflare's ranges
   to the server's `.env`, then `docker compose ... up -d caddy`:
   ```bash
   echo "TRUSTED_PROXIES=$(curl -s https://www.cloudflare.com/ips-v4 https://www.cloudflare.com/ips-v6 | tr '\n' ' ')" >> .env
   ```
   Without this, every visitor looks like one of Cloudflare's addresses to the server.
4. Caching works without extra settings: Cloudflare caches the static files by extension and follows the
   `Cache-Control` headers above. To also cache the public catalog lists at the edge, add a **Cache Rule**: URI path
   starts with `/api/game/`, method `GET` → *Eligible for cache*, *Use cache-control header if present*. Don't
   cache the rest of `/api`: it is per-player.
5. Optionally, lock the Oracle security list to Cloudflare's ranges for ports 80/443, so nobody can bypass the CDN.

## Profiling (where the time and memory go)

Every app service runs Java Flight Recorder all the time with its low-overhead settings (about 1% CPU). It keeps
the last 30 minutes, at most 25 MB, inside the container. When something was slow, grab that window on the
server, then copy it to your computer:

```bash
ssh quizking 'cd quizcadia && deploy/profile.sh game-engine'          # last 30 minutes → profiles/*.jfr
scp 'quizking:quizcadia/profiles/game-engine-*.jfr' .
```

- `deploy/profile.sh <service> jfr 60` records the next 60 s (up to 300) with detailed settings, while you
  reproduce the problem.
- `deploy/profile.sh <service> heap` takes a heap dump (`.hprof.gz`) when memory keeps growing. The service pauses
  for a few seconds, and **the dump contains secrets** (tokens, passwords): keep it private and delete it when done.
- `deploy/profile.sh <service> threads` shows what every thread is doing right now, for a request that hangs.

Open `.jfr` files in [JDK Mission Control](https://adoptium.net/jmc/) or IntelliJ IDEA (*Run → Open Profiler
Snapshot*):
- *Method Profiling* / the flame graph: CPU hot spots.
- *Memory → Allocations*: what creates the garbage.
- *Lock Instances*: contention.
- *Garbage Collections*: pauses.
- *Socket I/O* and *File I/O*: slow calls to MongoDB, Kafka or other services.
- *Exceptions*.

Open heap dumps in Eclipse MAT or IntelliJ.

These endpoints (`/actuator/jfr`, `/actuator/heapdump`, `/actuator/threaddump`) only answer from inside the
service's own container (`docker compose exec`), never from the Docker network or the internet. Locally,
`curl localhost:9092/actuator/jfr?seconds=30 -o game.jfr` works against a service started from the IDE.

## Spring profiles

Each app service runs with one Spring profile. The settings for each profile, shared by every service, are in
the config server's `configuration-service/src/main/resources/config-repo/application-<profile>.yml`.

| Profile | Where | Set by |
|---|---|---|
| `local` | a service started from the IDE or with `./mvnw spring-boot:run` | the default (`spring.profiles.default`) |
| `docker` | `docker compose up` on a laptop | `docker-compose.yml` |
| `prod` | the live server | `docker-compose.prod.yml` |

- `local` and `docker` show health details and log QuizCadia's own code at DEBUG (`local`).
- `prod` quiets the Kafka and MongoDB client logs and turns off the game manager's Swagger UI.
- Addresses and secrets still come from environment variables (`.env`). The config server is optional at
  startup, so nothing a service needs to run safely lives in these files.

## Continuous deployment (GitHub Actions)
`.github/workflows/deploy.yml` deploys automatically after CI passes on `master` (or `main`), that is, after a
pull request is merged, and from the Actions tab by hand:

1. **Build images** (on GitHub's ARM runner, the same architecture as the server): builds the jars and every app
   image for the commit CI tested and pushes them to `ghcr.io/faz2658/quizcadia/<service>:<commit>`.
2. **Deploy**: copies the compose files to the server and runs `deploy/deploy.sh`, which records the image tag
   in `.env` (`IMAGE_REGISTRY`, `IMAGE_TAG`), pulls the new images with the job's short-lived token, restarts
   only the services whose image changed, and waits until the site answers (the run fails if it doesn't).

**One merge at a time (the master queue).** Every merge to master gets its own CI run, and none is cancelled. The
run's first job, *Master queue* (`.github/scripts/master-queue.sh`), waits until two things are done: every earlier
master build, and the production deploy that follows the last one that passed. Only then does it build. So when
several pull requests are merged close together:
- they are built and deployed one after the other, in merge order;
- a new build never starts while another is building or a deploy is running (including one waiting for approval
  in the `production` environment);
- the job log says what it is waiting for.

Pull requests and other branches skip the queue. A *Run workflow* started by hand on master does too. A run waits
at most 5 hours.

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
ssh quizking 'cd quizcadia && sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile ai up -d'
```

## Website analytics on the admin page
Admin > Website analytics reads Google Analytics with a read-only service account:
1. Google Cloud (project quiz-king-514821): enable the **Google Analytics Data API**, create a service account
   (no roles needed) and add a JSON key; the browser downloads it.
2. Google Analytics: Admin > Property access management > add the service account's email as **Viewer**.
3. Copy the key to the server and point the settings at it:
   ```bash
   ssh quizking 'mkdir -p ~/quizcadia/secrets && chmod 711 ~/quizcadia/secrets'
   scp ~/Downloads/<key-file>.json quizking:quizcadia/secrets/ga-service-account.json
   # the Java containers run as uid 1000 (the server's own user is 1001): give the key to them only
   ssh quizking 'sudo chown 1000:1000 ~/quizcadia/secrets/ga-service-account.json && sudo chmod 400 ~/quizcadia/secrets/ga-service-account.json'
   ```
   In the server's `.env`: `GA_PROPERTY_ID=564461039` and `GA_CREDENTIALS_FILE=/run/secrets/ga-service-account.json`,
   then restart game-reporting. `secrets/` is never committed and deploys leave it alone.

## Health alerts
`.github/workflows/health.yml` runs every 15 minutes (and from the Actions tab). It checks that the site and its API
answer, and over SSH that the disk is under 85%, at least 10% of memory is available, and every container is running
and healthy. On a problem it opens a GitHub issue labelled `site-alert` that mentions the owner (GitHub emails and
pushes it), comments when the problem changes, and closes it when everything is fine again. Deploys also remove the
previous release's images and the Docker build cache, which had filled the disk to 96%.

## Memory
Every container has a memory ceiling (`mem_limit` in `docker-compose.yml`) and no swap of its own, and Java
heaps are 60% of their container. A service that runs out restarts on its own instead of pushing the whole
server into swap (which froze it for 20 minutes on 16 Nov 2026). Most Java services get 640 MB
(`JAVA_MEM_LIMIT`), game-manager, game-engine and game-reporting 896 MB (`HEAVY_MEM_LIMIT`); MongoDB's cache
is 0.5 GB, Kafka's heap 384 MB, and the AI model 3 GB (its 2.5 GB file stays loaded). Under a 100-player
load test the whole stack peaked at about 10 GB of the server's 12 GB.
`.env` on the server (never committed) holds `SITE_ADDRESS`, `CORS_ALLOWED_ORIGINS=https://<site>`,
`QUIZCADIA_JWT_SECRET`, `QUIZCADIA_ADMIN_EMAILS`, `MYSQL_PASSWORD`, `NEO4J_PASSWORD`, `GRAFANA_PASSWORD` and
`AI_MODEL_URL=http://question-model:8080` (with the GGUF in `ai/question-model/models/`), and optionally
`GOOGLE_CLIENT_ID`, `GA_MEASUREMENT_ID` and `MAIL_*`.

`docker-compose.prod.yml` puts Caddy (automatic Let's Encrypt HTTPS) in front and closes every other port.

## Spending guard ($20/month)
- Oracle budget `quiz-king-monthly-cap`: $20 a month, email alert at 100%.
- Dynamic group `quiz-king-server` (this instance only) and policy `quiz-king-budget-guard`:
  read `usage-budgets`, use this instance.
- `budget-guard/`: an hourly systemd timer that reads the budget with the instance's own identity and,
  at $20, stops the containers and soft-stops the server. Logs: `/var/log/quizcadia-budget-guard.log`.
  Test without stopping anything: `sudo LIMIT=0 quizcadia-budget-guard --dry-run`.

## Renames
- **Maverick to Quiz King** (November 2026): done; its migration script is in git history.
- **Quiz King to QuizCadia** (16 November 2026): `deploy/rename-migration.sh` runs on every deploy and does nothing
  once done. The first deploy after the rename stops the old `quiz-king` stack, moves `~/quiz-king` to
  `~/quizcadia`, copies the data volumes to `quizcadia_*` (the `quiz-king_*` ones stay as a backup until you delete
  them), renames `QUIZKING_*` settings to `QUIZCADIA_*`, rewrites the old Java package names stored in Mongo, and
  reinstalls the budget guard as `quizcadia-budget-guard`. Sign-in tokens issued before it stay valid.
  Names of outside resources keep the old spelling, because they can't (or needn't) change: the Google Cloud
  project `quiz-king-514821` and its service account, the Oracle budget, dynamic group and policy, and the GitHub
  repository URL.

The server's `.env` keeps the database login the data was created with (`MYSQL_USER`/`MYSQL_PASSWORD`/
`NEO4J_PASSWORD`); don't change those without changing the database.
