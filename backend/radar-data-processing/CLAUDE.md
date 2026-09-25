## Module-specific notes

- Batch job: loads `tools/radar-dump.json`, validates/enriches each cycle, publishes to `radar.kafka.topic`, then exits — it does not stay running.
- `radar.lat` / `radar.lon` (radar installation coordinates) are required at startup; missing either fails fast.
- `radar.processing.inter-cycle-delay-ms` controls cadence: `0` for max-speed test/demo runs, a positive value (e.g. 4000ms) to simulate real SSR sweep timing.
- No shared serialization class with the consumer side — `separation-infringement-detection` deserializes into its own local `RadarPositionMessage`, not one from `atcsafety-contracts` (see SAD §7).

# Java Testing Conventions

- Test method names must follow `should_<expected>_when_<condition>`convention
- Every test method body must be split into three explicitly commented sections,
  in this order: `// arrange`, `// act`, `// assert`.
- Tests for a given method under test must live in their own nested class (`@Nested`),
  one nested class per method.
- Write helper functions to avoid code duplication.
  Use them to set up the fixtures or create the objects needed for the test.

Example:

```java
@Test
void should_return_empty_set_and_skip_analysis_when_cycle_has_only_one_aircraft() {
    // arrange
    var a = pos("BA123", 0.0f, 20000.0f);

    // act
    var result = detector.detectInfringements(List.of(a), 1);

    // assert
    assertThat(result).isEmpty();
}
```