## Module notes (separation-infringement-detection)

- Long-running Kafka consumer (group `detection-service`, `auto-offset-reset=earliest`). Start it before the radar pipeline.
- State model: open events (ACTIVE, GRACE_PERIOD) live only in memory in `MinSeparationActiveEventRegistry`
  (impl `InMemoryMinSeparationActiveEventRegistry`); only CLOSED events are persisted through `MinSeparationEventStore`.
  A restart loses open events by design, so don't add persistence for them without asking.
- Flow per cycle: `RadarPositionConsumer` -> `CycleDetectionProcessor` -> `InfringementDetector` (pairs + `SeparationCalculator`)
  -> `MinSeparationEventLifecycleManager` -> store. Keep detection math in `domain/`; `application/` only orchestrates.
- Safety parameters: `detection.horizontalThresholdNm`, `detection.verticalThresholdFt` and `detection.gracePeriodCycles`
  live in `application.properties`; the 12-cycle observation window is in `domain/`. Never change them as a side effect.
- Messages are deserialized into a local `RadarPositionMessage`, with `use.type.headers=false`, so there is no compile-time
  link to the producer. A change to the producer's message shape must be mirrored here by hand.
- `AircraftPairKey` identifies a pair independent of order; use it for any lookup of pair-keyed state.
- Spring Boot 4 property namespace is `spring.mongodb.*`; override via `SPRING_MONGODB_URI` and `SPRING_KAFKA_BOOTSTRAP_SERVERS`.
