# QuizCadia

QuizCadia is a quiz platform made of Spring Boot microservices, Kafka, and an Angular frontend.

| Mode | What it does |
|---|---|
| **Timed** | Questions run from easy to advanced, each with its own time limit and points. The server does the scoring. |
| **Adaptive** | You start on easy questions. Score at least 60% on a block of 4 and you move up a level; score under 30% and you move down. |
| **Multiplayer** | 2–4 players get the same question; the first correct answer wins the points and a wrong answer costs 5. |
| **Challenge a friend** | Send a friend a link; you both play the same questions in your own time, and the higher score wins. |

## Quick start (everything in Docker)

```bash
cp .env.example .env          # then set QUIZCADIA_JWT_SECRET to 32+ random characters
./mvnw -DskipTests package    # builds the 13 service jars
docker compose up --build
```

Open **http://localhost:4280** (change it with `QUIZCADIA_HTTP_PORT`). On first start the question generator
imports **Open Trivia DB** (about 5,300 questions in 24 categories; it takes about 10 minutes because the API
allows one request every 5 seconds) and every topic with at least 12 questions gets a solo game and a
multiplayer game automatically. Games fill in while the import runs. Register with an email listed in
`QUIZCADIA_ADMIN_EMAILS` to get the **Admin** pages: **Users & activity** (accounts, sign-ups per day, active
players, games per day, top games and countries, newest accounts) and **Questions & games** (the question bank,
the import, and creating games).

Set `GA_MEASUREMENT_ID` (a Google Analytics 4 id, `G-…`) to add Google Analytics. Players are asked first;
nothing loads until they allow it, ad features are off, and only page views and a few events (login, sign_up,
share) are sent, never names, emails or ids.

### Questions don't repeat

A game is a topic plus a difficulty mix, not a fixed list. Every time someone plays, the engine draws a fresh
set from the topic's whole pool: questions that player has never seen come first, then the ones they saw longest
ago. Multiplayer matches prefer questions none of the players have seen.

Questions come from three places:
- **Open Trivia DB** import (`POST /api/v1/question-generator/import/opentdb`), shown with attribution
  (CC BY-SA 4.0)
- **Wikidata** templates (`POST .../category/{id}/{topic}/auto-questions?template=capitals|currencies|us-presidents`)
- **Your own questions** (`POST .../category/{id}/{topic}/questions`)

### Challenge a friend

A signed-in player picks a solo game (or its International version) and gets a link to send: there is no
friends list, so the link is how a friend is reached. It lives in the game engine (`game`, package `challenge`,
collection `friend_duels`, `/api/v1/challenges/duels`) next to the older "beat my score" links, and both sides
play it as ordinary timed sessions.

- **Accept or decline.** The first other player to open the link accepts or declines it. It expires if nobody
  answers within 3 days. You can't take your own challenge, and once one friend has it nobody else can.
- **Asynchronous.** The questions are drawn once, when the friend accepts, because that is when both players'
  countries are known. Then each player plays them whenever they like within 7 days: the same questions, in the
  same order, with the same options. Questions neither of them has seen come first.
- **Which questions.** The same rule as multiplayer: if both players are from the same country, about 75% of the
  questions come from that country and the rest are general. If their countries differ (or one is unknown), only
  general questions are used. International uses questions from every country. The country is sent when the
  player creates or accepts a challenge and is never shown to anyone.
- **Result.** When both have played, the higher score wins (equal scores are a tie). You don't see your friend's
  score until you have played. If only one player plays in time, they win.
- **Where it shows.** "Challenge a friend" on the home page's friends row and on every solo game card, the
  challenge page `/vs/{id}`, and a **Challenges** list on the home page once you have one.

## Development

Requirements: JDK 21+, Node 24.15+, Docker (for MongoDB/Kafka/Neo4j and the integration tests).

```bash
./mvnw verify                          # all backend tests (Testcontainers ITs skip themselves without Docker)
cd frontend && npm ci && npm test      # frontend unit tests (Vitest)
cd frontend && npm start               # dev server on :4200, proxies /api/* to the services (proxy.conf.json)
```

Every service runs on its own with `KAFKA_ENABLED=false`, `EUREKA_ENABLED=false` and a local MongoDB,
for example the game engine with demo data:

```bash
cd game && KAFKA_ENABLED=false EUREKA_ENABLED=false DEMO_DATA=true ../mvnw spring-boot:run
```

## Services

| Service | Folder | Port | Store | Main API |
|---|---|---|---|---|
| Discovery (Eureka) | `Quiz-app` | 8082 | – | – |
| Config server | `configuration-service` | 8083 | bundled `config-repo` | – |
| Question generator | `questionGenerator` | 9090 | MongoDB | `/api/v1/question-generator` |
| Game manager | `GameManager` | 9091 | MongoDB | `/api/game` |
| Game engine (timed) | `game` | 9092 | MongoDB | `/api/v1/games`, `/api/v1/sessions` |
| Multiplayer engine | `MultiplayerGameEngine` | 9093 | MongoDB | `/api/v1/matches` |
| Adaptive engine | `AdaptiveGameEngine` | 9094 | MongoDB | `/api/v1/adaptive/sessions` |
| Recommendations | `recommendations` | 9095 | Neo4j | `/api/v1/recommendation` |
| Search | `SearchService` | 9096 | – | `/api/v1/search` |
| Login | `login` | 9097 | H2 / MySQL | `/api/v1/auth` |
| User profiles | `UserService-master` | 9098 | MongoDB | `/api/v1/users` |
| Reporting | `GameReporting` | 9099 | MongoDB | `/api/v1/reports` |
| Player matcher | `PlayerMatcher` | 9089 | in memory | `/api/v1/matching` |
| Frontend | `frontend` | 4280 (Docker) | – | nginx routes `/api/*` |

### Kafka topics

```
question generator --QuestionGen.t--> game manager --gameManager.t--> game engine, adaptive engine
                                       game manager <--GameEng.t----- game engine (asks for a game)
login --new-user-created.t--> user service, recommendations
game manager --created-new-game.t / created-new-multiplayer-game.t / deleted-game-by-id.t--> recommendations, multiplayer engine
player matcher --players-matched.t--> multiplayer engine
engines --Results3.t / multiplayerresult.ts / AdaptiveResult.t--> reporting, recommendations
```

## Security

The login service signs a JWT (HS256, `QUIZCADIA_JWT_SECRET`) for accounts and for guests (`POST /api/v1/auth/guest`,
no account stored). Every other service checks that token itself through the shared `quizcadia-security` module:

- the player is always the token's subject; a request for another player's session, match, lobby, stats,
  recommendations or profile gets 403 (admins may act for anyone)
- question bank, AI drafts and every game change (create, update, delete) are admin-only; admins come from
  `QUIZCADIA_ADMIN_EMAILS`
- public without a token: browsing games (`GET /api/game/**`) and topic counts (`/api/v1/question-generator/stats`)
- the full game pools the engines fetch (`/api/game/game1/*`, `/api/game/games/mp/*`) are blocked in nginx
- `/actuator/**` is only reachable inside the Docker network

Sessions: the JWT is an access token that lasts 15 minutes (`QUIZCADIA_JWT_LIFETIME`). Signing in also sets a
refresh token in the `qc_refresh` cookie (HttpOnly, SameSite=Strict, path `/api/v1/auth`, Secure when `PUBLIC_URL`
is https). The app swaps it at `POST /api/v1/auth/refresh` for a new access token and the next refresh token; a
used-up refresh token that comes back later ends that sign-in. A sign-in ends after 30 days unused or 90 days in
all (guests: 1 day; `QUIZCADIA_SESSION_IDLE`, `_ABSOLUTE`, `_GUEST`). `POST /api/v1/auth/logout` ends it,
`/logout/all` ends every sign-in of the account, and so does a password reset. Other services only check the
access token, so an ended sign-in can still reach them until its access token runs out.

Errors: every API error is RFC 9457 problem details (`application/problem+json`) with a `detail` the app shows as
is. Each service answers its own errors (404, 409, 400 with field messages...). `FallbackExceptionHandler` in
`quizcadia-security` answers the rest:
- Spring's request errors become 400/404/405/415.
- A database or service that can't be reached becomes 503 with `maintenance: true`, which the app shows as its
  "being updated" banner.
- Anything else becomes 500 with a short `errorId` that is also in the log. Internals never reach the browser.

Calls between services give up after 3 s connecting and 15 s waiting (`InternalHttp`). A Kafka that is down holds a
request for at most 5 s (`max.block.ms`). A Kafka listener that fails retries with exponential backoff (1, 2, 4, 8, then
every 10 s) for up to a minute before it logs the event as lost (`KafkaRetries`). When the servers are down, the
app checks back after 5, 10, 20, then every 30 s, each spread by ±20% so open tabs don't all return at once.

### Veracode scanning

`.github/workflows/veracode.yml` runs Veracode static analysis. It only reports and never blocks a merge or a
deploy. It scans each service's own compiled classes (built with `./mvnw`) and the Angular source in `frontend/`.

- **Pull requests to master:** a pipeline scan. Findings appear in the run's summary and log, and `results.json` is
  kept as the artifact `veracode-pipeline-backend` / `-frontend`.
- **Every Monday, or Actions > Veracode > Run workflow:** a policy scan uploaded to the Veracode platform
  (application profile `QuizCadia`, at https://analysis.veracode.com). It also runs an SCA scan of the Maven and npm
  dependencies when `SRCCLR_API_TOKEN` is set.
- **Secrets** (Settings > Secrets and variables > Actions): `VERACODE_API_ID` and `VERACODE_API_KEY`, plus the
  optional `SRCCLR_API_TOKEN`. Without them the scans are skipped.


All settings have local defaults and can be overridden with environment variables: `MONGODB_URI`,
`KAFKA_BOOTSTRAP_SERVERS` (default `localhost:19092`), `EUREKA_URL`, `CONFIG_SERVER_URL`, `GAME_MANAGER_URL`,
`NEO4J_URI`/`NEO4J_PASSWORD`, `DATABASE_URL` (login), `QUIZCADIA_JWT_SECRET`, `QUIZCADIA_ADMIN_EMAILS`,
`GA_MEASUREMENT_ID`, `CORS_ALLOWED_ORIGINS`. Metrics: `/actuator/prometheus` on every service; Prometheus on :9190, Grafana on :3000.
