Requires Docker Desktop, Java 21+, Maven 3.9+, Node 20+.

```bash
# 1. Infrastructure
docker compose up -d kafka mongodb kafka-ui

# 2. Detection service (long-running — start before the radar pipeline, keep this terminal open)
cd backend/separation-infringement-detection && mvn spring-boot:run

# 3. Radar pipeline (one-shot — publishes ~3,600 messages then exits)
cd backend/radar-data-processing && mvn spring-boot:run

# 4. REST API (port 38090)
cd backend/rest-api && mvn spring-boot:run

# 5. Angular dev server (port 4200, proxies /api/* to the REST API)
cd web-app && npm install && npx ng serve --proxy-config proxy.conf.json
```

Or run everything together: `docker compose up --build` (web app on port 4300).

Regenerate the radar dump (30 flights, 120 cycles, 8 scripted infringements) with `python tools/generate_radar_dump.py`.
