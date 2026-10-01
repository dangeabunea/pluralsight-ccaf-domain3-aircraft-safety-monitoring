# Near-airport horizontal separation minimum (3 NM)

Final destination once approved: `docs/plans/near-airport-horizontal-separation.plan.md`
(plan mode only allows writing this file; it will be copied there as the first step of implementation).

## Context

Today every aircraft pair is flagged when `hSep < 5.0 NM` (and `vSep < 1000 ft`), anywhere in the sector.
Spec `docs/specs/near-airport-horizontal-separation.spec.md`: near the radar/airport the minimum drops to
**3 NM**. "Near" = distance from origin `√(x²+y²)/1852 < 40 NM`, and **both** aircraft must be near;
exactly 40 NM is en route. Vertical rule is unchanged. Strict `<` on both axes stays.

**This is a safety parameter change** (root CLAUDE.md): it makes detection *less* sensitive near the airport.
Flagging it explicitly, not as a side effect.

## Assumptions to review before implementation

1. **Both-near test = `max(distA, distB) < range`.** Equivalent to "both < 40 NM"; the spec example
   A=38 / B=42 → 5 NM confirms it.
2. **Rule lives in `SeparationThresholds`** (pure domain, no new class), same shape as the unimplemented
   RVSM plan (`docs/plans/rvsm-vertical-separation.plan.md`). The distance-from-radar computation goes in
   `SeparationCalculator`, next to the existing 1852 m/NM constant.
3. **Record widening conflicts with the RVSM plan.** Both widen `SeparationThresholds`. RVSM is not yet in
   code (record is still 2-arg), although `docker-compose.yml` already carries its two env vars.
   This plan assumes it is implemented **independently/first-come**; whichever lands second adds its
   fields and fixes the constructor calls. Tell me if you want a combined plan instead.
4. **Startup validation (my addition, not in spec):** `nearAirportRangeNm > 0` and
   `horizontalThresholdNearAirportNm <= horizontalThresholdNm` (a *larger* near-airport minimum is almost
   certainly a typo). Fail fast with `IllegalStateException` naming the property, like `gracePeriodCycles`.
   Say the word if you'd rather drop these.
5. **Demo-data impact is unknown.** I have not inspected `tools/radar-dump.json`. If any scripted
   infringement occurs within 40 NM of the origin at 3–5 NM, the e2e count (8 scripted / 3 CLOSED) changes.
   Per CLAUDE.md that is a regression to be investigated, not silently accepted — see Verification step 4.
6. `MinSeparationEventLifecycleManager` takes only `SeparationCalculator` (not thresholds), so
   grace/observation logic is assumed unaffected. Will confirm by reading it when implementing.
7. No frontend / REST / Mongo change — detection-only.

## Files that change

| File (under `backend/separation-infringement-detection/`) | Change |
|---|---|
| `src/main/java/.../domain/SeparationCalculator.java` | add `distanceFromRadarNm` |
| `src/main/java/.../domain/SeparationThresholds.java` | widen record + `requiredHorizontalSeparationNm` |
| `src/main/java/.../domain/InfringementDetector.java` | use per-pair horizontal minimum |
| `src/main/java/.../application/DetectionConfig.java` | 2 new boxed fields |
| `src/main/java/.../application/DetectionApplicationConfig.java` | defaults + validation |
| `src/main/resources/application.properties` | 2 new properties |
| `docker-compose.yml` (~line 60), `README.md` (~line 213) | 2 new env vars / table rows |
| `docs/architecture-overview.md` (line 9) | rule text |
| tests: `SeparationCalculatorTest`, `SeparationThresholdsTest`, `InfringementDetectorTest`, `DetectionApplicationConfigTest` | see Tests |

## Implementation steps (in order, test-first)

### 1. Domain — `SeparationCalculator`: distance from radar

```java
/**
 * Computes an aircraft's distance from the radar (the origin, at the airport) in nautical miles.
 *
 * @param p aircraft position; x and y in metres
 * @return distance in nautical miles; always >= 0.0
 */
public double distanceFromRadarNm(TrajectoryPosition p) {
    return Math.sqrt(p.x() * p.x() + p.y() * p.y()) / METRES_PER_NAUTICAL_MILE;
}
```

### 2. Domain — `SeparationThresholds`: the rule

```java
public record SeparationThresholds(
        double horizontalThresholdNm,            // en route, default 5.0
        double horizontalThresholdNearAirportNm, // both near airport, default 3.0
        double nearAirportRangeNm,               // zone radius, default 40.0
        int verticalThresholdFt) {

    /** 3 NM only when BOTH aircraft are strictly inside the zone; exactly at the range is en route. */
    public double requiredHorizontalSeparationNm(double distanceANm, double distanceBNm) {
        return Math.max(distanceANm, distanceBNm) < nearAirportRangeNm
                ? horizontalThresholdNearAirportNm
                : horizontalThresholdNm;
    }
}
```

Update the Javadoc (currently documents a flat 5.0 NM and the defaults list). Stays pure Java.

### 3. Domain — `InfringementDetector`: replace the single comparison (line 79)

```java
double hSep = calculator.horizontalSeparationNm(a, b);
double vSep = calculator.verticalSeparationFt(a, b);
double hMinimumNm = thresholds.requiredHorizontalSeparationNm(
        calculator.distanceFromRadarNm(a), calculator.distanceFromRadarNm(b));

if (hSep < hMinimumNm && vSep < thresholds.verticalThresholdFt()) {
    infringedPairs.add(AircraftPairKey.of(a.callsign(), b.callsign()));
}
```

Update class/method Javadoc ("`hSep < horizontalThresholdNm`"). The detector stays a thin stateless predicate.

### 4. Application — config binding

`DetectionConfig.java`: add boxed fields so absence arrives as `null` (existing convention) and document them:

```java
public record DetectionConfig(
        Double horizontalThresholdNm,
        Double horizontalThresholdNearAirportNm,
        Double nearAirportRangeNm,
        Integer verticalThresholdFt,
        Integer gracePeriodCycles,
        Integer observationWindowCycles,
        Kafka kafka) { ... }
```

`DetectionApplicationConfig.java`: add constants and extract helpers (PMD caps: cyclomatic ≤ 12,
cognitive ≤ 15 — `separationThresholds` already has two if/else blocks, so don't inline):

```java
private static final double DEFAULT_HORIZONTAL_THRESHOLD_NEAR_AIRPORT_NM = 3.0;
private static final double DEFAULT_NEAR_AIRPORT_RANGE_NM = 40.0;

@Bean
public SeparationThresholds separationThresholds(DetectionConfig config) {
    double horizontal = resolveHorizontalThresholdNm(config);   // existing logic, extracted
    double nearAirport = resolveNearAirportThresholdNm(config);
    double range = resolveNearAirportRangeNm(config);
    if (nearAirport > horizontal) {
        throw new IllegalStateException(
                "Invalid detection.horizontalThresholdNearAirportNm: " + nearAirport
                + " -- must be <= detection.horizontalThresholdNm (" + horizontal + ").");
    }
    int vertical = resolveVerticalThresholdFt(config);          // existing logic, extracted
    return new SeparationThresholds(horizontal, nearAirport, range, vertical);
}

private static double resolveNearAirportRangeNm(DetectionConfig config) {
    if (config.nearAirportRangeNm() == null) {
        log.warn("detection.nearAirportRangeNm not configured -- using default: {} NM",
                DEFAULT_NEAR_AIRPORT_RANGE_NM);
        return DEFAULT_NEAR_AIRPORT_RANGE_NM;
    }
    if (config.nearAirportRangeNm() <= 0) {
        throw new IllegalStateException(
                "Invalid detection.nearAirportRangeNm: " + config.nearAirportRangeNm() + " -- must be > 0.");
    }
    log.info("Near-airport range configured: {} NM", config.nearAirportRangeNm());
    return config.nearAirportRangeNm();
}
// resolveNearAirportThresholdNm follows the same default-with-WARN pattern (also must be > 0)
```

Update the `separationThresholds` Javadoc (`@throws IllegalStateException never` is no longer true).

### 5. Configuration files

`application.properties` (keep existing comment style; update the "< 5.0 NM" comment):

```properties
# horizontalThresholdNm: en-route horizontal separation < 5.0 NM triggers infringement
# horizontalThresholdNearAirportNm: when BOTH aircraft are within nearAirportRangeNm of the airport, < 3.0 NM triggers infringement
# nearAirportRangeNm: radius of the near-airport zone; exactly at the range counts as en route
detection.horizontalThresholdNm=5.0
detection.horizontalThresholdNearAirportNm=3.0
detection.nearAirportRangeNm=40
```

`docker-compose.yml` (alongside `DETECTION_HORIZONTAL_THRESHOLD_NM`, relaxed binding):

```yaml
DETECTION_HORIZONTAL_THRESHOLD_NEAR_AIRPORT_NM: "3.0"
DETECTION_NEAR_AIRPORT_RANGE_NM: "40"
```

`README.md` table (~line 213): two matching rows.

### 6. Documentation

`docs/architecture-overview.md` line 9 states "less than **5 NM** apart horizontally" — rewrite to: less than
5 NM, or less than 3 NM when both aircraft are within 40 NM of the airport; vertical rule unchanged.
Also update `backend/separation-infringement-detection/CLAUDE.md` to list the two new safety parameters.

## Tests

Module conventions: `@Nested` class per method, `should_<expected>_when_<condition>`, `// arrange // act // assert`,
helper functions for fixtures, AssertJ.

- **`SeparationCalculatorTest`** — `@Nested DistanceFromRadarNm`: origin → 0; (1852, 0) → 1.0; (1852·3, 1852·4) → 5.0
  (uses both axes).
- **`SeparationThresholdsTest`** — update the 8 two-arg constructor calls to four args; add
  `@Nested RequiredHorizontalSeparationNm`: both inside → 3.0; both outside → 5.0; one inside/one outside → 5.0;
  `max` exactly at range (40/10) → 5.0 (exactly 40 is en route); just below range (39.99/10) → 3.0;
  argument order symmetric.
- **`InfringementDetectorTest`** (constructor at line 22 → `new SeparationThresholds(5.0, 3.0, 40.0, 1000)`;
  existing cases sit at 0–5 NM from origin, so they now fall in the near zone — **update their distances/expectations
  or anchor them en route so they still test the 5 NM boundary**). New cases = the spec examples, all same altitude:
  - A 10 NM, B 12 NM, 4 NM apart → no infringement
  - A 10 NM, B 12 NM, 2 NM apart → infringement
  - A 90 NM, B 92 NM, 4 NM apart → infringement
  - A 38 NM, B 42 NM, 4 NM apart → infringement
  - near pair exactly 3.0 NM apart → no infringement (strict `<`)
  - near pair 2.9 NM apart but vSep exactly 1000 ft → no infringement
- **`DetectionApplicationConfigTest`** (13 occurrences of `detection.horizontalThresholdNm=` stay valid since new
  properties are optional): add — both new properties bind when set; absent → defaults 3.0 / 40.0 and context
  does not fail; `nearAirportRangeNm=0` → context fails naming the property; near-airport minimum greater than
  en-route minimum → context fails naming the property.
- `ArchitectureRulesTest` — no change (no new package, annotation or naming pattern).

## Verification

From repo root, Docker running:

1. `mvn test -pl backend/separation-infringement-detection` — unit, ArchUnit, Testcontainers green.
2. `mvn pmd:check pmd:cpd-check` — confirms the widened `separationThresholds` stays within complexity limits.
3. `mvn test` — full reactor; no downstream module may depend on the old 2-arg record.
4. `bash e2e/e2e-smoke.sh` — regression gate: must still report exactly 3 CLOSED events and `pendingCount: 3`.
   If not, **stop and report** which scripted infringements fall inside the 40 NM zone (check
   `tools/radar-dump.json` / `tools/generate_radar_dump.py`); do not adjust the dataset or thresholds to make it pass.
5. Fail-fast check by hand: start the service with `DETECTION_NEAR_AIRPORT_RANGE_NM=0` and confirm startup aborts
   with a message naming the property.
