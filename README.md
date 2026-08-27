# ATC Safety Monitoring
This is a large demo project aimed to simulate the detection of safety events in ATC. Because of its complexity it can also be used as a demo for integrating Claude Code in large enterprise systems.

---

## Local Development — Starting Fresh

This is the definitive startup sequence for running the backend services locally against a Docker-hosted Kafka and MongoDB.

### Prerequisites

- Docker Desktop running
- Java 21+
- Maven 3.9+
- Node.js 20+ with `npm` (only needed to run the Angular app locally — Angular CLI is a devDependency, no global install required)
- Python 3.x (only needed to regenerate the radar dump)

### Step 1 — Start the infrastructure

```bash
docker compose up -d kafka mongodb kafka-ui
```

Wait until Kafka is healthy (the healthcheck runs `kafka-topics.sh --list`; takes ~15 s). Verify:

```bash
docker compose ps
```

All three containers should show `healthy` or `Up`.

### Step 2 — Start the detection service (keep this terminal open)

```bash
cd backend/separation-infringement-detection
mvn spring-boot:run
```

The service subscribes to `radar.validated-positions` and waits for messages. You should see:

```
partitions assigned: [radar.validated-positions-0]
```

### Step 3 — Produce radar data (one-shot, exits when done)

```bash
cd backend/radar-data-processing
mvn spring-boot:run
```

The service reads `tools/radar-dump.json`, publishes ~3 600 messages, then exits:

```
INFO  Pipeline complete — published=3596, rejected=4, errors=0
```

The detection service processes all 120 radar cycles. On the final message:

```
INFO  Flush sentinel received — dispatching final buffered cycle 120
```

### Step 4 — Verify results

**Redpanda Console** — http://localhost:8082  
Browse topics, inspect messages, and check that the `detection-service` consumer group lag is 0.

**MongoDB**

```bash
docker exec -it pluralsight-ccaf-domain3-aircraft-safety-monitoring-mongodb-1 \
  mongosh atc_safety --quiet \
  --eval 'db.separation_infringement_events.countDocuments({})'
```

Expect `3` closed infringement events on the default dump.

---

### Resetting after a full `docker compose down -v`

When volumes are wiped, the Kafka topic and MongoDB data are gone. The consumer group offset record is also gone — the detection service will automatically start from the beginning (`auto-offset-reset=earliest`) on the next run. Simply follow steps 1–3 again.

If the topic already has messages (volume was preserved) but the detection service already consumed them and you need to reprocess:

```bash
# Consumer group must be in Empty state first (service stopped)
docker exec pluralsight-ccaf-domain3-aircraft-safety-monitoring-kafka-1 \
  bash -c "/opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --group detection-service \
    --reset-offsets --to-earliest \
    --topic radar.validated-positions --execute"
```

Then restart the detection service — it will replay from offset 0.

---

## Radar Data Processing Service

The radar processing service is a **batch pipeline** — it loads a JSON radar dump, validates and enriches each position, publishes every valid position to Kafka as an individual message, then exits. It is not a long-running server.

### Prerequisites

Start the infrastructure (Kafka + MongoDB) before running the service:

```bash
docker compose -f docker-compose.infra.yml up -d
```

### Generate test data

The service reads from `tools/radar-dump.json`. Regenerate it at any time with:

```bash
python tools/generate_radar_dump.py
```

The dump contains 30 flights over 120 radar cycles (~10 minutes at 5 s/cycle), including 8 pre-scripted infringement events.

### Start the service

From `backend/radar-data-processing/`:

```bash
mvn spring-boot:run
```

The pipeline runs immediately on startup. When complete, the log prints a summary and the process exits:

```
INFO  Pipeline complete — published=3596, rejected=4, errors=0
```

### Key configuration

Defaults are in `src/main/resources/application.properties`. Override via environment variables:

| Property | Env var | Default |
|---|---|---|
| Radar dump file path | `RADAR_DUMP_FILE_PATH` | `tools/radar-dump.json` |
| Kafka bootstrap servers | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| Kafka topic | `RADAR_KAFKA_TOPIC` | `radar.validated-positions` |
| Radar installation latitude | — | `48.8566` |
| Radar installation longitude | — | `2.3522` |

Example — point at a different dump file without editing `application.properties`:

```bash
RADAR_DUMP_FILE_PATH=tools/my-custom-dump.json mvn spring-boot:run
```

### Debugging tips

**Service fails to start — Kafka not reachable**
The pipeline refuses to connect if Kafka is not up. Verify the broker:
```bash
docker compose -f docker-compose.infra.yml ps
```

**Dump file not found**
The service throws `RadarDumpFileNotFoundException` if the path in `RADAR_DUMP_FILE_PATH` does not exist. Run `generate_radar_dump.py` first, or check the path.

**All records rejected**
The validator logs a `WARN` for each rejected position. Check the logs for `[INVALID]` lines — common causes are a missing `flightNb` field (the 4 synthetic invalid records in the test dump are expected) or `x`/`y` both zero.

**Inspect published messages**
Open **Kafka UI** at `http://localhost:8082` — browse topics, inspect JSON payloads, and track consumer lag in the browser. Both compose files include it.

Alternatively, consume the topic directly from the CLI:
```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic radar.validated-positions \
  --from-beginning
```

---

## Separation Infringement Detection Service

The detection service is a **long-running Kafka consumer** — it listens on the `radar.validated-positions` topic, buffers positions by radar cycle, runs separation-infringement detection on each completed cycle, and persists closed events to MongoDB.

### Prerequisites

Start the infrastructure and the radar pipeline first:

```bash
docker compose -f docker-compose.infra.yml up -d
```

### Start the service

From `backend/separation-infringement-detection/`:

```bash
mvn spring-boot:run
```

The service starts consuming immediately. Detected infringement events are logged at INFO and written to MongoDB once closed.

### Key configuration

Defaults are in `src/main/resources/application.properties`. Override via environment variables:

| Property | Env var | Default |
|---|---|---|
| Kafka bootstrap servers | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` |
| Kafka topic | `DETECTION_KAFKA_TOPIC` | `radar.validated-positions` |
| MongoDB URI | `SPRING_MONGODB_URI` | `mongodb://localhost:27017/atc_safety` |
| Horizontal threshold (NM) | `DETECTION_HORIZONTAL_THRESHOLD_NM` | `5.0` |
| Vertical threshold (ft) | `DETECTION_VERTICAL_THRESHOLD_FT` | `1000` |
| Grace period (cycles) | `DETECTION_GRACE_PERIOD_CYCLES` | `3` |

---

## REST API Service

The REST API is a **long-running Spring Boot service** that exposes separation infringement events stored in MongoDB. It serves the Angular web app and any external clients.

### Prerequisites

Start MongoDB before running the service:

```bash
docker compose -f docker-compose.infra.yml up -d
```

### Start the service

From `backend/rest-api/`:

```bash
mvn spring-boot:run
```

The service listens on port **38090** by default.

### Key configuration

| Property | Env var | Default |
|---|---|---|
| MongoDB URI | `SPRING_MONGODB_URI` | `mongodb://localhost:27017/atc_safety` |

---

## Angular Web App

The web app is an **Angular 21 SPA** that displays separation infringement events fetched from the REST API. During local development it runs on the Angular dev server; in the Docker demo it is served by nginx.

### Prerequisites

- Node.js 20+
- REST API running on port **38090** (see section above)

### Install dependencies

From `web-app/`:

```bash
npm install
```

Angular CLI is a devDependency — `npm install` makes it available as `./node_modules/.bin/ng`. No global install needed.

### Start the dev server

```bash
npx ng serve --proxy-config proxy.conf.json
```

The app is available at **http://localhost:4200**.

The `proxy.conf.json` forwards all `/api/*` requests to `http://localhost:38090`, so the Angular dev server transparently proxies calls to the REST API without CORS issues.

### Key configuration

| Setting | Value |
|---|---|
| Dev server port | `4200` |
| REST API proxy target | `http://localhost:38090` |
| Production URL (Docker) | `http://localhost:4300` |

---

## Running the Full Stack

To run all services together (radar pipeline, detection, REST API, web app, Kafka UI):

```bash
docker compose up --build
```

Services start in dependency order: Kafka and MongoDB first, then the detection service and REST API, then the radar pipeline (which publishes one batch and exits), and finally the web app.

| Service | URL |
|---|---|
| Web app | http://localhost:4300 |
| REST API | http://localhost:38090 |
| Kafka UI | http://localhost:8082 |

---

## Infrastructure Lifecycle and Kafka Message Persistence

Understanding what `docker compose down` does to Kafka messages is important for demos and local development.

### `docker compose down` — messages survive

Named volumes (`kafka-data`) are **preserved**. Kafka messages remain on disk and are available when the broker restarts. The `radar-data-processing` service will not re-run on the next `up` (`restart: "no"`), so there are no duplicates.

Consumer group offsets are also preserved in `kafka-data`. A downstream service that was mid-consumption will resume from where it left off on the next `up`.

### `docker compose down -v` — messages are wiped

The `-v` flag deletes named volumes. `kafka-data` is removed, and the broker starts completely fresh on the next `up`. This is the recommended reset command for demos:

```bash
docker compose down -v && docker compose up
```

**Why this is the right demo workflow:** the radar dump is deterministic (fixed seed, always 3 600 messages, same 8 infringement events). A fresh broker guarantees a reproducible demo — no stale dismissed or annotated events from a previous run.

### Summary

| Command | `kafka-data` volume | Messages after next `up` |
|---|---|---|
| `docker compose down` | preserved | still there |
| `docker compose down -v` | deleted | gone — fresh start |
