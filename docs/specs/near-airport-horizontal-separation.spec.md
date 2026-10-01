# Spec: Closer horizontal separation near the airport

## Goal

Today two aircraft are flagged when they are **less than 5 NM apart horizontally**, wherever they are.
Near the airport the radar is more precise, so aircraft may fly closer together.

**New rule:** if both aircraft are near the airport, the horizontal minimum is **3 NM** instead of **5 NM**.

The vertical rule is unchanged.

## Rules

1. The radar is at the airport. An aircraft's distance from the airport is √(x² + y²), with x and y in metres; convert to NM (1 NM = 1852 m).
2. **Near the airport** means less than 40 NM away. Exactly 40 NM counts as en route.
3. **Both** aircraft must be near the airport for 3 NM to apply. Otherwise the minimum is 5 NM.
4. An infringement still needs horizontal **and** vertical separation **below** their minimums. Equal is not an infringement.

## Examples

All aircraft are at the same altitude.

- A at 10 NM, B at 12 NM, 4 NM apart → minimum 3 NM → **no infringement**
- A at 10 NM, B at 12 NM, 2 NM apart → minimum 3 NM → **infringement**
- A at 90 NM, B at 92 NM, 4 NM apart → minimum 5 NM → **infringement**
- A at 38 NM, B at 42 NM, 4 NM apart → minimum 5 NM (B is outside) → **infringement**

## Configuration

- `detection.horizontalThresholdNm`: en-route minimum (existing property), default **5.0**
- `detection.horizontalThresholdNearAirportNm`: near-airport minimum, default **3.0**
- `detection.nearAirportRangeNm`: size of the near-airport zone, default **40**
