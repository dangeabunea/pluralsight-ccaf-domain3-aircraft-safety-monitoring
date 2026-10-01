# Altitude-dependent vertical separation minima

## Context

Today the detection service applies a single, flat vertical separation minimum —
detection.verticalThresholdFt=1000 — to every aircraft pair regardless of altitude.
Real ATC practice does not work that way: above roughly FL410, vertical separation
minima increase because altimetry error grows with altitude.

This change makes the vertical minimum a function of the pair's altitude:

        2000 ft   ← both aircraft above the limit
──────────────────────────────────  upper altitude limit (configurable, default 41 000 ft)
1000 ft   ← at or below the limit
──────────────────────────────────  ground

The horizontal minimum (5.0 NM) is unaffected — altitude changes the vertical rule only.

Decisions taken (confirmed with the user)

1. The band runs from the ground to a configurable upper limit. There is no lower
   bound; 1000 ft applies everywhere at or below the limit. This is why only the upper
   limit needs configuring.
2. A pair uses 2000 ft only when both aircraft are above the limit
   (min(altA, altB) > upperLimit). A straddling pair keeps 1000 ft.

Assumptions to review before implementation

- The 2000 ft value is made configurable, not hardcoded. The existing 1000 ft is a
  property, and CLAUDE.md classes these as safety parameters — a constant buried in code
  would be inconsistent. Say the word and it becomes a domain constant instead.
- "At the limit" counts as inside the band → 1000 ft applies at exactly 41 000 ft.
  Uses strict > for "above", matching this module's existing strict-comparison
  convention (InfringementDetector: "Equality at either threshold is not an infringement").
- The "both above" rule detects fewer events than an "either above" rule for
  straddling pairs. This was chosen deliberately; flagging once because it is a safety
  parameter, per CLAUDE.md.
- No demo-data impact. The dataset (tools/radar-dump.json) tops out at 39 500 ft,
  below the 41 000 ft default — so no pair is ever "both above" and detection results are
  byte-identical. The e2e smoke test's 3 CLOSED events must stay 3; that is the regression guard.

Implementation

1. Domain — SeparationThresholds (the whole rule lives here)

backend/separation-infringement-detection/src/main/java/com/atcsafety/detection/domain/SeparationThresholds.java

Widen the record from 2 to 4 components and give it one behaviour method:

public record SeparationThresholds(
double horizontalThresholdNm,
int verticalThresholdFt,        // at or below the limit
int verticalThresholdUpperFt,   // both aircraft above the limit
int upperAltitudeLimitFt) {

    public int requiredVerticalSeparationFt(double altAFeet, double altBFeet) {
        return Math.min(altAFeet, altBFeet) > upperAltitudeLimitFt
                ? verticalThresholdUpperFt
                : verticalThresholdFt;
    }
}

Math.min(...) > limit encodes "both above" and "at the limit is inside" in one expression.
Stays pure Java with zero framework imports, so the ArchUnit domain-purity rule holds.
Update the Javadoc, which currently documents a flat 1000 ft minimum.

2. Domain — InfringementDetector

.../domain/InfringementDetector.java — one line inside the existing pair loop:

int verticalMinimumFt = thresholds.requiredVerticalSeparationFt(a.altFeet(), b.altFeet());
if (hSep < thresholds.horizontalThresholdNm() && vSep < verticalMinimumFt) {

The detector stays a thin stateless predicate; no new class is introduced. Update the
class and method Javadoc, which currently assert a fixed vSep < verticalThresholdFt.

3. Application — config binding

.../application/DetectionConfig.java — add two boxed fields so an absent property
arrives as null, per the convention already documented in that record:

Integer verticalThresholdUpperFt,
Integer upperAltitudeLimitFt,

.../application/DetectionApplicationConfig.java — add
DEFAULT_VERTICAL_THRESHOLD_UPPER_FT = 2000 and DEFAULT_UPPER_ALTITUDE_LIMIT_FT = 41000,
and resolve both in the existing separationThresholds(...) bean, reusing the established
default-with-WARN / fail-fast-with-IllegalStateException pattern.

Extract the two new resolutions into small private helpers rather than inlining them —
separationThresholds(...) already carries two if/else blocks and PMD caps methods at
cyclomatic 12 / cognitive 15.

Startup validation (fail fast, naming the offending property, as gracePeriodCycles does):
- upperAltitudeLimitFt must be > 0
- verticalThresholdUpperFt must be >= verticalThresholdFt — a smaller minimum in
  higher airspace is operationally nonsensical and almost certainly a typo

4. Configuration files

.../src/main/resources/application.properties — three properties with comments matching
the existing commented style:

detection.verticalThresholdFt=1000
detection.verticalThresholdUpperFt=2000
detection.upperAltitudeLimitFt=41000

docker-compose.yml (~line 61) — add alongside the existing DETECTION_* vars, which
bind via Spring relaxed binding:

DETECTION_VERTICAL_THRESHOLD_UPPER_FT: "2000"
DETECTION_UPPER_ALTITUDE_LIMIT_FT: "41000"

5. Documentation

docs/architecture-overview.md — the "Separation minima infringement" section states the
rule as a flat "less than 1,000 ft apart vertically". Rewrite it to describe the
altitude-dependent rule. The table of detected infringements stays correct unchanged.

Tests

Written test-first, following the module's existing @Nested / should_… /
arrange-act-assert conventions with AssertJ.

SeparationThresholdsTest — update the 8 two-arg constructor calls to four args; add a
@Nested class RequiredVerticalSeparation:
- both below the limit → 1000
- both above the limit → 2000
- straddling pair (one above, one below) → 1000
- both exactly at the limit → 1000 (at limit is inside)
- one at the limit, one above → 1000
- argument order is symmetric

InfringementDetectorTest — update the single constructor call at line 22; add the
behavioural contrast cases, which are the heart of this change:
- pair above the limit separated by 1500 ft → is an infringement (1500 < 2000)
- pair below the limit separated by 1500 ft → not an infringement (1500 > 1000)
- straddling pair separated by 1500 ft → not an infringement (1000 ft rule applies)

The existing cases fly at 20 000–21 000 ft and must keep their current expected results.

DetectionApplicationConfigTest — add to the existing ApplicationContextRunner slices:
- both new properties bind when set
- absent → defaults of 2000 / 41 000, context does not fail
- upperAltitudeLimitFt=0 → context fails with a message naming the property
- verticalThresholdUpperFt below verticalThresholdFt → context fails

ArchitectureRulesTest needs no change — no new package, annotation, or naming pattern.

Verification

Run from the repo root, with Docker running:

1. mvn test -pl backend/separation-infringement-detection — unit, ArchUnit and
   Testcontainers suites green.
2. mvn pmd:check pmd:cpd-check — confirms the widened separationThresholds(...) bean
   stays inside the complexity thresholds.
3. mvn test — full reactor, confirming no downstream module was relying on the old
   two-arg record.
4. bash e2e/e2e-smoke.sh — the key regression gate. Must still report exactly
   3 CLOSED events and pendingCount: 3. Any other number means the altitude rule
   changed behaviour on data where it should have been inert, and is a bug in this change.
5. Sanity check the fail-fast path by hand: start the service with
   DETECTION_UPPER_ALTITUDE_LIMIT_FT=0 and confirm startup aborts with a message naming
   the property.