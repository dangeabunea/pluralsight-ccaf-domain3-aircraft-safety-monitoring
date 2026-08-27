"""
Flight Data Generator — ATC Safety Alert System test fixture.
Spec: docs/superpowers/specs/2026-03-20-flight-data-generator-design.md

Run:  python tools/generate_radar_dump.py
"""

import json
import math
import random
from datetime import datetime, timezone, timedelta

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

RADAR_LAT = 50.847
RADAR_LON = 4.349
NUM_CYCLES = 120         # 10 minutes at 5 s/cycle
CYCLE_S = 5
START_TIME = datetime(2026, 3, 20, 10, 0, 0, tzinfo=timezone.utc)
SEED = 42

# Computed at runtime — never hard-code the cosine constant.
LON_DEG_PER_KM = 1.0 / (111.320 * math.cos(math.radians(RADAR_LAT)))
LAT_DEG_PER_KM = 1.0 / 111.320

# ---------------------------------------------------------------------------
# Flight roster (20 flights)
# turns: [(0-based cycle at which heading changes, new heading_deg), ...]
# ---------------------------------------------------------------------------

FLIGHTS = [
    # --- Inbound to Brussels (descending toward (0, 0)) ---
    dict(target_id="T001", flight_nb="BAW447",  route="London -> BRU",
         start_x=-180, start_y=160,  heading_deg=135, speed_kn=420,
         alt_ft_start=38000, alt_ft_end=22000,
         turns=[(65, 155)]),
    dict(target_id="T002", flight_nb="DLH612",  route="Frankfurt -> BRU",
         start_x=230,  start_y=20,   heading_deg=270, speed_kn=420,
         alt_ft_start=36000, alt_ft_end=20000,
         turns=[(55, 255)]),
    dict(target_id="T003", flight_nb="AFR2840", route="Paris -> BRU",
         start_x=-160, start_y=-150, heading_deg=45,  speed_kn=420,
         alt_ft_start=28000, alt_ft_end=15000,
         turns=[(65, 30)]),
    dict(target_id="T004", flight_nb="KLM1234", route="Amsterdam -> BRU",
         start_x=10,   start_y=200,  heading_deg=180, speed_kn=400,
         alt_ft_start=24000, alt_ft_end=12000,
         turns=[(70, 170)]),
    dict(target_id="T005", flight_nb="SAS535",  route="Copenhagen -> BRU",
         start_x=80,   start_y=250,  heading_deg=200, speed_kn=420,
         alt_ft_start=36000, alt_ft_end=22000,
         turns=[(55, 215)]),

    # --- Outbound from Brussels (climbing from near (0, 0)) ---
    dict(target_id="T006", flight_nb="BEL3501", route="BRU -> London",
         start_x=5,    start_y=5,    heading_deg=315, speed_kn=300,
         alt_ft_start=2000,  alt_ft_end=20000,
         turns=[(22, 300)]),
    dict(target_id="T007", flight_nb="RYR8814", route="BRU -> Dublin",
         start_x=-5,   start_y=5,    heading_deg=295, speed_kn=300,
         alt_ft_start=2000,  alt_ft_end=22000,
         turns=[(22, 310)]),
    dict(target_id="T008", flight_nb="VLG4521", route="BRU -> Barcelona",
         start_x=5,    start_y=-5,   heading_deg=210, speed_kn=300,
         alt_ft_start=2000,  alt_ft_end=26000,
         turns=[(22, 195)]),
    dict(target_id="T009", flight_nb="EZY4567", route="BRU -> Manchester",
         start_x=-5,   start_y=0,    heading_deg=340, speed_kn=300,
         alt_ft_start=2000,  alt_ft_end=24000,
         turns=[(22, 325)]),

    # --- En-route overflights (constant cruise altitude — straight, no turns) ---
    dict(target_id="T010", flight_nb="EZY7823", route="Amsterdam -> Paris",
         start_x=80,   start_y=120,  heading_deg=220, speed_kn=450,
         alt_ft_start=35000, alt_ft_end=35000),
    dict(target_id="T011", flight_nb="RYR5678", route="London -> Frankfurt",
         start_x=-200, start_y=50,   heading_deg=90,  speed_kn=460,
         alt_ft_start=39000, alt_ft_end=39000),
    dict(target_id="T012", flight_nb="DLH789",  route="Hamburg -> Madrid",
         start_x=50,   start_y=200,  heading_deg=180, speed_kn=450,
         alt_ft_start=37000, alt_ft_end=37000),
    dict(target_id="T013", flight_nb="AFR1234", route="Paris -> Amsterdam",
         start_x=-80,  start_y=-120, heading_deg=40,  speed_kn=450,
         alt_ft_start=34000, alt_ft_end=34000),
    dict(target_id="T014", flight_nb="BAW234",  route="London -> Vienna",
         start_x=-180, start_y=100,  heading_deg=130, speed_kn=460,
         alt_ft_start=38000, alt_ft_end=38000),
    dict(target_id="T015", flight_nb="RYR2345", route="Dublin -> Rome",
         start_x=-150, start_y=130,  heading_deg=145, speed_kn=450,
         alt_ft_start=35000, alt_ft_end=35000),
    dict(target_id="T016", flight_nb="SWR456",  route="Zurich -> London",
         start_x=200,  start_y=-40,  heading_deg=270, speed_kn=460,
         alt_ft_start=39000, alt_ft_end=39000),
    dict(target_id="T017", flight_nb="KLM5678", route="Amsterdam -> Lisbon",
         start_x=70,   start_y=130,  heading_deg=215, speed_kn=450,
         alt_ft_start=36000, alt_ft_end=36000),
    dict(target_id="T018", flight_nb="TRA999",  route="Amsterdam -> Nice",
         start_x=30,   start_y=180,  heading_deg=190, speed_kn=440,
         alt_ft_start=34000, alt_ft_end=34000),
    dict(target_id="T019", flight_nb="AUA789",  route="Vienna -> London",
         start_x=200,  start_y=20,   heading_deg=280, speed_kn=460,
         alt_ft_start=38000, alt_ft_end=38000),
    dict(target_id="T020", flight_nb="IBE4532", route="Madrid -> Amsterdam",
         start_x=-100, start_y=-200, heading_deg=30,  speed_kn=450,
         alt_ft_start=33000, alt_ft_end=33000),

    # --- Event 4 pair: RVSM false positive (FL350 / FL365, 1,500 ft vert sep) ---
    # Current system (2,000 ft threshold): flagged. After RVSM (1,000 ft): cleared.
    dict(target_id="T021", flight_nb="TAP456",  route="Lisbon -> Warsaw",
         start_x=-86,  start_y=80,   heading_deg=90,  speed_kn=450,
         alt_ft_start=35000, alt_ft_end=35000),
    dict(target_id="T022", flight_nb="LOT789",  route="Warsaw -> London",
         start_x=86,   start_y=78,   heading_deg=270, speed_kn=450,
         alt_ft_start=36500, alt_ft_end=36500),

    # --- Event 5 pair: Germany head-on (FL370/FL380, 1,000 ft vert sep) ---
    dict(target_id="T023", flight_nb="DLH234",  route="Frankfurt -> Madrid",
         start_x=168,  start_y=35,   heading_deg=220, speed_kn=450,
         alt_ft_start=37000, alt_ft_end=37000),
    dict(target_id="T024", flight_nb="VLG567",  route="Barcelona -> Oslo",
         start_x=142,  start_y=5,    heading_deg=40,  speed_kn=450,
         alt_ft_start=38000, alt_ft_end=38000),

    # --- Event 6 pair: English Channel crossing (FL330/FL340, 1,000 ft vert sep) ---
    dict(target_id="T025", flight_nb="BAW891",  route="London -> Amsterdam",
         start_x=-169, start_y=11,   heading_deg=45,  speed_kn=450,
         alt_ft_start=33000, alt_ft_end=33000),
    dict(target_id="T026", flight_nb="KLM234",  route="Amsterdam -> London",
         start_x=-91,  start_y=89,   heading_deg=225, speed_kn=450,
         alt_ft_start=34000, alt_ft_end=34000),

    # --- Event 7 pair: Lille head-on (FL290/FL300, 1,000 ft vert sep) ---
    dict(target_id="T027", flight_nb="AFR567",  route="Paris -> Cologne",
         start_x=-105, start_y=71,   heading_deg=160, speed_kn=450,
         alt_ft_start=29000, alt_ft_end=29000),
    dict(target_id="T028", flight_nb="EZY789",  route="London -> Geneva",
         start_x=-35,  start_y=-111, heading_deg=340, speed_kn=450,
         alt_ft_start=30000, alt_ft_end=30000),

    # --- Event 8 pair: Liège RVSM false positive (FL380/FL395, 1,500 ft vert sep) ---
    dict(target_id="T029", flight_nb="MAU101",  route="Paris -> Warsaw",
         start_x=-10,  start_y=-110, heading_deg=45,  speed_kn=460,
         alt_ft_start=38000, alt_ft_end=38000),
    dict(target_id="T030", flight_nb="THY567",  route="Warsaw -> Paris",
         start_x=170,  start_y=70,   heading_deg=225, speed_kn=460,
         alt_ft_start=39500, alt_ft_end=39500),
]

# ---------------------------------------------------------------------------
# Infringement overrides
# {(target_id, 0-based-cycle): {"x": float, "y": float[, "alt_ft": int]}}
# Position overrides replace the kinematic formula for the listed cycles only.
# alt_ft is present only where altitude deviates from linear interpolation.
# ---------------------------------------------------------------------------

OVERRIDES: dict[tuple[str, int], dict] = {}

# Event 1 — Head-on corridor conflict (radarCycles 33-38 => 0-based 32-37)
# EZY7823 (T010, FL350) vs AFR1234 (T013, FL340) — vert sep 1,000 ft constant
_E1 = [
    (32, (53.0, 73.0), (47.0, 67.0)),
    (33, (52.3, 72.1), (47.7, 67.9)),
    (34, (51.5, 71.2), (48.5, 68.8)),
    (35, (50.8, 70.3), (49.2, 69.7)),
    (36, (50.0, 69.5), (50.0, 70.5)),
    (37, (49.3, 68.6), (50.7, 71.4)),
]
for _c, _ezy, _afr in _E1:
    OVERRIDES[("T010", _c)] = {"x": _ezy[0], "y": _ezy[1]}
    OVERRIDES[("T013", _c)] = {"x": _afr[0], "y": _afr[1]}

# Event 2 — Parallel opposite-direction tracks (radarCycles 60-67 => 0-based 59-66)
# RYR5678 (T011, FL390) vs AUA789 (T019, FL380) — vert sep 1,000 ft constant
_E2 = [
    (59, (-4.00, 40), (+4.00, 43)),
    (60, (-2.84, 40), (+2.86, 43)),
    (61, (-1.68, 40), (+1.72, 43)),
    (62, (-0.53, 40), (+0.58, 43)),
    (63, (+0.63, 40), (-0.56, 43)),
    (64, (+1.78, 40), (-1.70, 43)),
    (65, (+2.94, 40), (-2.84, 43)),
    (66, (+4.09, 40), (-3.98, 43)),
]
for _c, _ryr, _aua in _E2:
    OVERRIDES[("T011", _c)] = {"x": _ryr[0], "y": _ryr[1]}
    OVERRIDES[("T019", _c)] = {"x": _aua[0], "y": _aua[1]}

# Event 3 — Converging inbounds with grace period (radarCycles 90-96 => 0-based 89-95)
# DLH612 (T002) vs SAS535 (T005) — altitude override at 0-based 92 (radarCycle 93)
_E3 = [
    # (0-based cycle, dlh_x, dlh_y, sas_x, sas_y, dlh_alt_override, sas_alt_override)
    (89,  8.00, 2.00,  4.00, 8.00,   None,   None),
    (90,  6.92, 2.00,  3.63, 6.99,   None,   None),
    (91,  5.84, 2.00,  3.26, 5.97,   None,   None),
    (92,  4.76, 2.00,  2.89, 4.96,  21000,  23400),  # gap — alt diff 2,400 ft
    (93,  3.68, 2.00,  2.52, 3.95,   None,   None),
    (94,  2.60, 2.00,  2.15, 2.93,   None,   None),
    (95,  1.52, 2.00,  1.78, 1.92,   None,   None),
]
for _c, _dx, _dy, _sx, _sy, _da, _sa in _E3:
    ov_d: dict = {"x": _dx, "y": _dy}
    if _da is not None:
        ov_d["alt_ft"] = _da
    OVERRIDES[("T002", _c)] = ov_d

    ov_s: dict = {"x": _sx, "y": _sy}
    if _sa is not None:
        ov_s["alt_ft"] = _sa
    OVERRIDES[("T005", _c)] = ov_s

# Event 4 — RVSM false positive (radarCycles 72-79 => 0-based 71-78)
# TAP456 (T021, FL350) vs LOT789 (T022, FL365) — vert sep 1,500 ft constant
# Current system (2,000 ft threshold): flagged. After RVSM (1,000 ft): cleared.
_E4 = [
    (71, (-4.00, 80), (+4.00, 78)),
    (72, (-2.84, 80), (+2.84, 78)),
    (73, (-1.69, 80), (+1.69, 78)),
    (74, (-0.53, 80), (+0.53, 78)),
    (75, (+0.63, 80), (-0.63, 78)),
    (76, (+1.78, 80), (-1.78, 78)),
    (77, (+2.94, 80), (-2.94, 78)),
    (78, (+4.09, 80), (-4.09, 78)),
]
for _c, _tap, _lot in _E4:
    OVERRIDES[("T021", _c)] = {"x": _tap[0], "y": _tap[1]}
    OVERRIDES[("T022", _c)] = {"x": _lot[0], "y": _lot[1]}

# Event 5 — Germany head-on (radarCycles 15-20 => 0-based 14-19)
# DLH234 (T023, FL370) vs VLG567 (T024, FL380) — same geometry as Event 1, shifted to x~155, y~20
_E5 = [
    (14, (158.0, 23.0), (152.0, 17.0)),
    (15, (157.3, 22.1), (152.7, 17.9)),
    (16, (156.5, 21.2), (153.5, 18.8)),
    (17, (155.8, 20.3), (154.2, 19.7)),
    (18, (155.0, 19.5), (155.0, 20.5)),
    (19, (154.3, 18.6), (155.7, 21.4)),
]
for _c, _a, _b in _E5:
    OVERRIDES[("T023", _c)] = {"x": _a[0], "y": _a[1]}
    OVERRIDES[("T024", _c)] = {"x": _b[0], "y": _b[1]}

# Event 6 — English Channel crossing (radarCycles 45-50 => 0-based 44-49)
# BAW891 (T025, FL330) vs KLM234 (T026, FL340) — NE/SW crossing, x~-130, y~50
_E6 = [
    (44, (-133.0, 47.0), (-127.0, 53.0)),
    (45, (-132.2, 47.8), (-127.8, 52.2)),
    (46, (-131.4, 48.6), (-128.6, 51.4)),
    (47, (-130.5, 49.5), (-129.5, 50.5)),
    (48, (-129.7, 50.3), (-130.3, 49.7)),
    (49, (-128.9, 51.1), (-131.1, 48.9)),
]
for _c, _a, _b in _E6:
    OVERRIDES[("T025", _c)] = {"x": _a[0], "y": _a[1]}
    OVERRIDES[("T026", _c)] = {"x": _b[0], "y": _b[1]}

# Event 7 — Lille head-on (radarCycles 82-88 => 0-based 81-87)
# AFR567 (T027, FL290) vs EZY789 (T028, FL300) — N/S corridor, x~-70, y~-20
_E7 = [
    (81, (-73.0, -17.0), (-67.0, -23.0)),
    (82, (-72.6, -18.1), (-67.4, -21.9)),
    (83, (-72.2, -19.2), (-67.8, -20.8)),
    (84, (-71.8, -20.3), (-68.2, -19.7)),
    (85, (-71.4, -21.3), (-68.6, -18.7)),
    (86, (-71.0, -22.4), (-69.0, -17.6)),
    (87, (-70.6, -23.5), (-69.4, -16.5)),
]
for _c, _a, _b in _E7:
    OVERRIDES[("T027", _c)] = {"x": _a[0], "y": _a[1]}
    OVERRIDES[("T028", _c)] = {"x": _b[0], "y": _b[1]}

# Event 8 — Liège RVSM false positive (radarCycles 105-112 => 0-based 104-111)
# MAU101 (T029, FL380) vs THY567 (T030, FL395) — NE/SW crossing, x~80, y~-20
_E8 = [
    (104, (77.0, -23.0), (83.0, -17.0)),
    (105, (77.8, -22.2), (82.2, -17.8)),
    (106, (78.7, -21.3), (81.3, -18.7)),
    (107, (79.5, -20.5), (80.5, -19.5)),
    (108, (80.3, -19.7), (79.7, -20.3)),
    (109, (81.2, -18.8), (78.8, -21.2)),
    (110, (82.0, -18.0), (78.0, -22.0)),
    (111, (82.8, -17.2), (77.2, -22.8)),
]
for _c, _a, _b in _E8:
    OVERRIDES[("T029", _c)] = {"x": _a[0], "y": _a[1]}
    OVERRIDES[("T030", _c)] = {"x": _b[0], "y": _b[1]}

# ---------------------------------------------------------------------------
# Infringement event metadata — used for GeoJSON red-overlay segments
# ---------------------------------------------------------------------------

INFRINGEMENT_EVENTS = [
    {
        "label": "Event 1: head-on conflict FL350/FL340",
        "targets": ["T010", "T013"],
        "radar_cycles": set(range(33, 39)),   # 33-38 inclusive
        "color": "#dc2626",                   # red — real infringement
    },
    {
        "label": "Event 2: parallel opposite tracks FL390/FL380",
        "targets": ["T011", "T019"],
        "radar_cycles": set(range(60, 68)),   # 60-67 inclusive
        "color": "#dc2626",
    },
    {
        "label": "Event 3: converging inbounds (gap at cycle 93)",
        "targets": ["T002", "T005"],
        "radar_cycles": {90, 91, 92, 94, 95, 96},  # gap cycle 93 excluded
        "color": "#dc2626",
    },
    {
        "label": "Event 4: RVSM false positive FL350/FL365 (1,500 ft — cleared under RVSM)",
        "targets": ["T021", "T022"],
        "radar_cycles": set(range(72, 80)),   # 72-79 inclusive
        "color": "#f97316",                   # orange — false positive, eliminated after RVSM
    },
    {
        "label": "Event 5: Germany head-on FL370/FL380",
        "targets": ["T023", "T024"],
        "radar_cycles": set(range(15, 21)),   # 15-20 inclusive
        "color": "#dc2626",
    },
    {
        "label": "Event 6: Channel crossing FL330/FL340",
        "targets": ["T025", "T026"],
        "radar_cycles": set(range(45, 51)),   # 45-50 inclusive
        "color": "#dc2626",
    },
    {
        "label": "Event 7: Lille head-on FL290/FL300",
        "targets": ["T027", "T028"],
        "radar_cycles": set(range(82, 89)),   # 82-88 inclusive
        "color": "#dc2626",
    },
    {
        "label": "Event 8: Liege RVSM false positive FL380/FL395 (1,500 ft — cleared under RVSM)",
        "targets": ["T029", "T030"],
        "radar_cycles": set(range(105, 113)), # 105-112 inclusive
        "color": "#f97316",
    },
]

# ---------------------------------------------------------------------------
# Invalid records (4) — targetId T097-T100, scattered, no flightNb field
# radarCycles 17, 47, 73, 103 (scaled from original 5, 14, 22, 31)
# ---------------------------------------------------------------------------

INVALID_CYCLE_IDS = [
    (17,  "T097"),
    (47,  "T098"),
    (73,  "T099"),
    (103, "T100"),
]

# ---------------------------------------------------------------------------
# Kinematic helpers
# ---------------------------------------------------------------------------

def speed_km_per_cycle(speed_kn: float) -> float:
    return speed_kn * 1.852 / 3600 * CYCLE_S


def kinematic_position(flight: dict, cycle: int) -> tuple[float, float]:
    """
    Return (x, y) km from radar, applying any heading turns defined in the flight.
    Each turn is (0-based cycle at which new heading takes effect, new heading_deg).
    """
    x = flight["start_x"]
    y = flight["start_y"]
    current_cycle = 0
    current_hdg = flight["heading_deg"]
    step_km = speed_km_per_cycle(flight["speed_kn"])

    for turn_cycle, new_hdg in sorted(flight.get("turns", [])):
        if cycle < turn_cycle:
            break
        # Accumulate displacement up to this turn point.
        steps = turn_cycle - current_cycle
        hdg_rad = math.radians(current_hdg)
        x += math.sin(hdg_rad) * step_km * steps
        y += math.cos(hdg_rad) * step_km * steps
        current_cycle = turn_cycle
        current_hdg = new_hdg

    # Remaining steps at current heading.
    steps = cycle - current_cycle
    hdg_rad = math.radians(current_hdg)
    x += math.sin(hdg_rad) * step_km * steps
    y += math.cos(hdg_rad) * step_km * steps
    return x, y


def current_heading(flight: dict, cycle: int) -> int:
    """Return the heading in effect at the given 0-based cycle."""
    hdg = flight["heading_deg"]
    for turn_cycle, new_hdg in sorted(flight.get("turns", [])):
        if cycle >= turn_cycle:
            hdg = new_hdg
    return hdg


def interpolate_alt(flight: dict, cycle: int) -> int:
    """Linear altitude interpolation over all NUM_CYCLES (0-based cycle)."""
    t = cycle / (NUM_CYCLES - 1)
    return round(flight["alt_ft_start"] + (flight["alt_ft_end"] - flight["alt_ft_start"]) * t)


def to_latlon(x: float, y: float) -> tuple[float, float]:
    lat = round(RADAR_LAT + y * LAT_DEG_PER_KM, 5)
    lon = round(RADAR_LON + x * LON_DEG_PER_KM, 5)
    return lat, lon


# ---------------------------------------------------------------------------
# Record builders
# ---------------------------------------------------------------------------

def generate_record(
    flight: dict,
    cycle: int,          # 0-based
    flight_index: int,
) -> dict:
    key = (flight["target_id"], cycle)
    override = OVERRIDES.get(key)

    if override:
        x, y = override["x"], override["y"]
        alt_ft = override.get("alt_ft", interpolate_alt(flight, cycle))
        hdg = current_heading(flight, cycle)
    else:
        x, y = kinematic_position(flight, cycle)
        alt_ft = interpolate_alt(flight, cycle)
        hdg = current_heading(flight, cycle)

    radar_cycle = cycle + 1
    timestamp = START_TIME + timedelta(seconds=radar_cycle * CYCLE_S)

    # x and y from kinematic_position() or OVERRIDES are in km from radar.
    # Multiply by 1000 to produce metres per the canonical RadarPosition spec.
    x_m = round(x * 1000, 1)
    y_m = round(y * 1000, 1)

    record: dict = {
        "targetId": int(flight["target_id"].lstrip("T")),  # "T001" -> 1
        "flightNb": flight["flight_nb"],
        "radarCycle": radar_cycle,
        "timestampUTC": timestamp.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "altFeet": alt_ft,
        "headingDeg": hdg,
        "speedKn": flight["speed_kn"],
        "x": x_m,  # metres east of radar installation
        "y": y_m,  # metres north of radar installation
    }

    # ~10% of records omit lat/lon to exercise the missing-coordinate branch.
    if (flight_index * NUM_CYCLES + cycle) % 10 != 0:
        lat, lon = to_latlon(x, y)
        record["lat"] = lat
        record["lon"] = lon

    return record


def generate_invalid_record(radar_cycle: int, target_id: str, rng: random.Random) -> dict:
    """Invalid record: no flightNb, random position within +-50 km of radar."""
    x_km = rng.uniform(-50, 50)
    y_km = rng.uniform(-50, 50)
    lat, lon = to_latlon(x_km, y_km)
    timestamp = START_TIME + timedelta(seconds=radar_cycle * CYCLE_S)
    # x_km / y_km are km offsets from radar; multiply by 1000 for metres per spec.
    return {
        "targetId": int(target_id.lstrip("T")),  # "T097" -> 97
        "radarCycle": radar_cycle,
        "timestampUTC": timestamp.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "altFeet": rng.randint(5000, 39000),
        "headingDeg": rng.randint(0, 359),
        "speedKn": rng.randint(200, 500),
        "x": round(x_km * 1000, 1),  # metres east of radar installation
        "y": round(y_km * 1000, 1),  # metres north of radar installation
        "lat": lat,
        "lon": lon,
    }


# ---------------------------------------------------------------------------
# GeoJSON builder
# ---------------------------------------------------------------------------

# Build a lookup: target_id -> flight_nb for GeoJSON labels.
_CALLSIGN = {f["target_id"]: f["flight_nb"] for f in FLIGHTS}

# Targets involved in any infringement event — used to suppress duplicates.
_INFRINGEMENT_TARGETS = {t for ev in INFRINGEMENT_EVENTS for t in ev["targets"]}


def build_geojson(flights: list[dict], all_records: list[dict]) -> dict:
    # Index records by (targetId_int, radarCycle) for fast lookup.
    # Records contain integer targetId per canonical spec; INFRINGEMENT_EVENTS still
    # uses string IDs ("T010") internally — convert to int at lookup time.
    by_key: dict[tuple[int, int], dict] = {
        (r["targetId"], r["radarCycle"]): r for r in all_records
    }

    features = []

    # --- Full flight tracks (dark blue) ---
    for flight in flights:
        target_id_int = int(flight["target_id"].lstrip("T"))
        coords = [
            [r["lon"], r["lat"]]
            for r in all_records
            if r.get("targetId") == target_id_int and "lon" in r
        ]
        features.append({
            "type": "Feature",
            "properties": {
                "callsign": flight["flight_nb"],
                "route": flight.get("route", ""),
                "targetId": flight["target_id"],
                "stroke": "#1e3a8a",
                "stroke-width": 2,
                "stroke-opacity": 0.7,
            },
            "geometry": {"type": "LineString", "coordinates": coords},
        })

    # --- Infringement segments (red overlay, thicker) ---
    for event in INFRINGEMENT_EVENTS:
        for target_id in event["targets"]:
            target_id_int = int(target_id.lstrip("T"))
            coords = [
                [r["lon"], r["lat"]]
                for rc in sorted(event["radar_cycles"])
                if (r := by_key.get((target_id_int, rc))) and "lon" in r
            ]
            if len(coords) < 2:
                continue
            features.append({
                "type": "Feature",
                "properties": {
                    "callsign": _CALLSIGN.get(target_id, target_id),
                    "event": event["label"],
                    "stroke": event.get("color", "#dc2626"),
                    "stroke-width": 5,
                    "stroke-opacity": 1.0,
                },
                "geometry": {"type": "LineString", "coordinates": coords},
            })

    return {"type": "FeatureCollection", "features": features}


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    rng = random.Random(SEED)

    records: list[dict] = []

    for fi, flight in enumerate(FLIGHTS):
        for cycle in range(NUM_CYCLES):
            records.append(generate_record(flight, cycle, fi))

    for radar_cycle, target_id in INVALID_CYCLE_IDS:
        records.append(generate_invalid_record(radar_cycle, target_id, rng))

    # Sort by (radarCycle, targetId) as per spec.
    records.sort(key=lambda r: (r["radarCycle"], r["targetId"]))

    out_json = "tools/radar-dump.json"
    with open(out_json, "w") as f:
        json.dump(records, f, indent=2)
    print(f"Wrote {len(records)} records -> {out_json}")

    geojson = build_geojson(FLIGHTS, records)
    out_geo = "tools/radar-dump.geojson"
    with open(out_geo, "w") as f:
        json.dump(geojson, f, indent=2)
    print(f"Wrote GeoJSON -> {out_geo}")

    # Spot-checks
    cycle1 = [r for r in records if r["radarCycle"] == 1]
    print(f"\nSpot-check radarCycle 1: {len(cycle1)} records (expect 30)")

    invalid = [r for r in records if "flightNb" not in r]
    print(f"Invalid records (no flightNb): {len(invalid)} (expect 4)")
    for r in invalid:
        print(f"  {r['targetId']} at radarCycle {r['radarCycle']}")

    no_latlon = [r for r in records if "lat" not in r]
    pct = 100 * len(no_latlon) / len([r for r in records if "flightNb" in r])
    print(f"Records missing lat/lon: {len(no_latlon)} ({pct:.1f}% of valid, expect ~10%)")

    baw = next(r for r in records if r.get("flightNb") == "BAW447" and r["radarCycle"] == 1)
    print(f"\nBAW447 radarCycle 1: altFeet={baw['altFeet']} (expect 38000), headingDeg={baw['headingDeg']} (expect 135)")
    baw_post = next(r for r in records if r.get("flightNb") == "BAW447" and r["radarCycle"] == 66)
    print(f"BAW447 radarCycle 66: headingDeg={baw_post['headingDeg']} (expect 155, post-turn)")

    conflict_features = [f for f in geojson["features"] if "event" in f["properties"]]
    print(f"\nGeoJSON infringement overlay features: {len(conflict_features)} (expect 16)")
    for cf in conflict_features:
        p = cf["properties"]
        print(f"  {p['callsign']:8s}  {p['stroke']}  {p['event']}")


if __name__ == "__main__":
    main()
