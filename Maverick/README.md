# Quiz King

Quiz King (project codename *Maverick*) is a quiz platform made of Spring Boot microservices, Kafka, and an Angular frontend.

| Mode | What it does |
|---|---|
| **Timed** | Questions run from easy to advanced, each with its own time limit and points. The server does the scoring. |
| **Adaptive** | You start on easy questions. Score at least 60% on a block of 4 and you move up a level; score under 30% and you move down. |
| **Multiplayer** | 2–4 players get the same question; the first correct answer wins the points and a wrong answer costs 5. |

## Quick start (everything in Docker)

```bash
cp .env.example .env          # then set MAVERICK_JWT_SECRET to 32+ random characters
./mvnw -DskipTests package    # builds the 13 service jars
docker compose up --build
```

Open **http://localhost:4280** (change it with `MAVERICK_HTTP_PORT`). The game engine loads three demo games
(`DEMO_DATA=true`), so you can play right away as a guest. Register with an email listed in
`MAVERICK_ADMIN_EMAILS` to get the **Create** page.

To add your own games: create a category, topic and questions in the question generator
(`POST /api/v1/question-generator/...`; generated questions can come from Wikidata with
`POST .../auto-questions?template=capitals`), then create a game from that topic on the **Create** page.

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

## Configuration

All settings have local defaults and can be overridden with environment variables: `MONGODB_URI`,
`KAFKA_BOOTSTRAP_SERVERS` (default `localhost:19092`), `EUREKA_URL`, `CONFIG_SERVER_URL`, `GAME_MANAGER_URL`,
`NEO4J_URI`/`NEO4J_PASSWORD`, `DATABASE_URL` (login), `MAVERICK_JWT_SECRET`, `MAVERICK_ADMIN_EMAILS`,
`CORS_ALLOWED_ORIGINS`. Metrics: `/actuator/prometheus` on every service; Prometheus on :9190, Grafana on :3000.
