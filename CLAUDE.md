# CLAUDE.md

This file gives Claude Code persistent context for the **ATC Safety Monitoring System** — a polyglot demo app (Spring Boot + Angular + Kafka + MongoDB) that detects aircraft separation-minima infringements from replayed radar data.

## Architecture

Data flow: `radar-data-processing` (batch, publishes radar positions to Kafka, then exits) → 
Kafka topic `radar.validated-positions` → 
`separation-infringement-detection` (long-running consumer, holds open events in memory) → 
MongoDB (closed events only) → 
`rest-api` (Backend for Frontend) → 
`web-app` (Angular, where analysts review events).

See `docs/architecture-overview.md` for the full write-up and diagrams.

`backend/atcsafety-contracts` holds types shared across services (e.g. `RadarPosition`) and is built first — see the `<modules>` order in the root `pom.xml`. Downstream modules depend on it; it must never depend on them.

## Running the system locally

Please refer to @docs/running-locally.md

## Backend conventions (Java 21 / Spring Boot 4)

Each backend module is layered into `domain/`, `application/`, `infrastructure/`, enforced at `mvn test` time 
by an `ArchitectureRulesTest` (ArchUnit) per module — these aren't just conventions, violating them fails the build:
- `domain/` is plain Java — zero Spring, Spring Data, or Kafka imports.
- `application/` may depend on `domain/` but never on `infrastructure/`.
- Classes named `*Document` (MongoDB persistence models) must live in `infrastructure/`.
- Classes named `*Event` (domain aggregates/events) must live in `domain/`.
- Constructor injection only — `@Autowired` on a field fails the build.
- No class ending in `Controller` may depend on a class ending in `Repository`.
- No cyclic dependencies between a module's top-level sub-packages.

Other conventions:

- Classes and their methods default to **package-private**, not `public`, unless they need to be used outside their package (see `EventController`, `EventService`).
- Logging goes through SLF4J only — `System.out`/`System.err` fails both PMD (`SystemPrintln`) and the ArchUnit `LoggingPolicy` rule. This matters here: log lines carry the timestamps and severity that an incident investigation would rely on.
- DTOs and domain events are Java records.
- Kafka topic names are read from config as `<module>.kafka.topic` (e.g. `radar.kafka.topic`, `detection.kafka.topic` in each module's `application.properties`) — never hardcode a topic string in code.
- REST endpoints are versioned under `/api/v1/...`; list endpoints return a `PagedResponse<T>` and accept `page`, `size`, and `sort` (`field,direction`, e.g. `startedAt,asc`) query params.

## Frontend conventions (Angular 21 / Tailwind v4)

- Standalone components only, no `NgModule`.
- Dependency injection via `inject()`, not constructor parameters.
- Local component state as signals (`signal()`), not plain class fields.
- File naming drops the `.component.ts` suffix (`dashboard.ts`, `events-page.ts`); the class itself keeps the `Component` suffix.
- Feature folders under `web-app/src/app/`: `dashboard/`, `events/`, `services/`, `models/`, `shared/`.
- Tailwind v4, CSS-first config in `styles.css` (`@import "tailwindcss"`, `@source` globs) — there is no `tailwind.config.js`. Design tokens (colors, shadows) are CSS custom properties under `:root`.
- Components never call `HttpClient` directly — all REST calls go through `EventApiService` (`src/app/services/event-api.service.ts`).

## Testing

### Backend

- `mvn test` (from repo root, or `-pl backend/<module>` for one module) runs JUnit 5 unit tests, the ArchUnit `ArchitectureRulesTest` suite, and the Testcontainers-backed MongoDB integration tests in `separation-infringement-detection` and `rest-api`. Docker must be running.
- Static analysis is a **separate, explicit** step, deliberately not bound to the `mvn test` lifecycle so TDD loops stay fast: `mvn pmd:check pmd:cpd-check`. Enforced thresholds: cyclomatic complexity ≤ 12/method, cognitive complexity ≤ 15/method, ≤ 20 methods/class, no empty catch blocks, no unused private fields/methods/params, no method-level `synchronized` or broken double-checked locking.
- Test sources are excluded from PMD — ArchUnit and code review already gate test quality there.

### Frontend

- `npm test` from `web-app/` (`ng test`, the Vitest-based `@angular/build:unit-test` builder).
- Specs are colocated next to the file they test (e.g. `dashboard.spec.ts` beside `dashboard.ts`), using `TestBed` with `provideHttpClientTesting()` / `HttpTestingController` — assert against the real DOM (`fixture.nativeElement`) rather than mocking `EventApiService` or `HttpClient` directly, and call `controller.verify()` in `afterEach`.

### End-to-end

- `bash e2e/e2e-smoke.sh` (from repo root) rebuilds and starts the full `docker compose` stack, waits for the `radar-data-processing` container to exit, polls MongoDB until 3 `CLOSED` infringement events exist, then checks `GET /api/v1/events/summary` reports `pendingCount: 3`. It tears the stack down (`docker compose down -v`) on exit either way.
- The demo dataset is deterministic — 30 flights over 120 cycles produce exactly 8 scripted infringements, 3 of which close within the replay window. If that count changes, treat it as a regression, not a data artifact.

## Local environment gotchas

- Start the detection service before the radar pipeline — it must already be subscribed, or early messages on a fresh topic are missed.
- `docker compose down` (no `-v`) preserves the `kafka-data` volume: Kafka messages, consumer offsets, and Mongo data all survive, and the radar pipeline won't republish (`restart: "no"`), so nothing duplicates.
- `docker compose down -v && docker compose up` is the standard demo reset — wipes the topic, MongoDB, and offsets for a reproducible run.
- The REST API listens on port 38090; the Angular dev server proxies `/api/*` to it via `proxy.conf.json` — don't hardcode `localhost:38090` in frontend code.

## Working in this codebase

- This is a safety-critical detection system. The separation thresholds are configured as `detection.horizontalThresholdNm` / `detection.verticalThresholdFt` in `separation-infringement-detection`'s `application.properties`) and the event-lifecycle timing (3-cycle grace period, 12-cycle observation window) in `separation-infringement-detection/domain` are safety parameters, not arbitrary constants — flag any change to them explicitly rather than adjusting them silently as a side effect of an unrelated fix.
- If a change touches `atcsafety-contracts`, rebuild it (`mvn install -pl backend/atcsafety-contracts`) before testing downstream modules against it.

## Claude behavior

- Never assume anything, always ask human when you are in doubt
- Always try to surface implicit assumptions and make them explicit so human can review
- Do noy overengineer code, prefer simple, maintainable solutions
