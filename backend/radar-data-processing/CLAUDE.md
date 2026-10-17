## Module notes (radar-data-processing)

- One-shot batch, not a service: loads the radar dump JSON, validates, enriches, publishes to Kafka, then exits
  (`restart: "no"` in compose). Don't add web endpoints or long-lived listeners here.
- Package layout differs from the other modules: besides `domain/` and `application/` it has feature packages
  `loading/`, `validation/` and `enrichment/`. The ArchUnit rules and the no-cycles rule still apply across them.
- `radar.lat`, `radar.lon` and `radar.kafka.topic` are required; the app must fail at startup if any is missing.
  `radar.processing.inter-cycle-delay-ms` is `0` by default (max speed) and negative values are rejected.
- Kafka values are serialized as JSON by `JacksonJsonSerializer`. `RadarPositionMessage` is the wire format:
  the detection service keeps its own copy and deserializes without a shared library, so a field rename or type change
  here silently breaks the consumer. Treat it as a cross-module contract change.
- Invalid positions are rejected by `RadarPositionValidator` and logged, not published and not fatal to the run.
- Dump file path comes from `radar.dump.file-path` (`RADAR_DUMP_FILE_PATH` in Docker); regenerate with `tools/generate_radar_dump.py`.
