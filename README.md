# Project 29 — Intelligent Emergency Resource Allocation & Ambulance Dispatch System

**Using Point Location, Orthogonal Range Search, and Fractional Cascading — implemented in Java.**

Rapid emergency response saves lives. This system pre-processes the spatial
positions of ambulances, fire trucks, police units and hospitals so that
"find the nearest / all resources" queries are answered in **logarithmic
time** instead of scanning every resource (`O(n)`). It uses an **orthogonal
range tree augmented with fractional cascading** for rectangular spatial
queries, and **slab-decomposition point location** to route an incident to the
responsible city sector.

> Full write-up (theory, pseudocode, complexity, results): **[docs/REPORT.md](docs/REPORT.md)**

---

## 1. What it does (mapping to the project brief)

| # | Required query | Method | Where |
|---|----------------|--------|-------|
| 1 | All available ambulances in a region | Orthogonal range search | `DispatchSystem.availableAmbulancesInRegion` |
| 2 | Nearest ambulance to an accident | Range-tree nearest neighbour | `DispatchSystem.nearestAvailableAmbulance` |
| 3 | Hospitals within 5 km having ICU | Range search + radius/ICU filter | `DispatchSystem.hospitalsWithIcuWithinRadius` |
| 4 | Allocate vehicles by incident severity | Severity policy + nearest neighbour | `DispatchSystem.allocate` |
| 5 | Redistribute ambulances after dispatch | Re-balance + relocate + rebuild | `DispatchSystem.redistributeCoverage` |
| 6 | Coverage map for planning | Per-sector range + NN summary | `DispatchSystem.coverageReport` |
| – | Which sector does an incident fall in? | **Point location** (slab method) | `DispatchSystem.zoneOf` |

---

## 2. Data structures

* **Orthogonal Range Tree** — `structures/RangeTree2D.java`. Balanced BST on x;
  every node keeps its subtree's points sorted by y. Plain query `O(log² n + k)`.
* **Fractional Cascading** — same file. Each y-entry points to the matching
  position in both children's y-arrays, so the y-threshold is found by **one**
  binary search at the root and followed down. Query → optimal **`O(log n + k)`**.
* **Point Location** — `structures/PointLocation.java`. Slab decomposition over
  the rectangular response zones; `O(log n)` per query.

Build is `O(n log n)`. See the report for derivations and the complexity table.

---

## 3. Project layout

```
DSA PROJECT/
├── README.md                  this file
├── run.bat / run.sh           one-command build + run (auto-detects the JDK)
├── .gitignore
├── data/
│   ├── resources.csv          ambulances, fire/police units, hospitals
│   └── zones.csv              city sectors for point location
├── docs/
│   └── REPORT.md              full academic report
└── src/emergency/
    ├── Main.java              driver: demo / test / menu
    ├── geometry/              Point, Rectangle
    ├── structures/            RangeTree2D (+ fractional cascading), PointLocation
    ├── model/                 ResourceType, Severity, IncidentType,
    │                          EmergencyResource, Incident, Zone, DispatchPlan
    ├── system/                DispatchSystem, CityLoader, SampleCity
    └── test/                  Verification (brute-force correctness suite)
```

---

## 4. Build & run

Requires a **JDK 8 or newer** (the `javac` compiler). A JDK 17 is already
installed on this machine; the scripts locate it automatically.

**Windows**
```
run-web.bat        REM 1-click launch: builds, starts server, opens browser
run.bat web        REM start interactive web frontend (port 8080)
run.bat            REM scripted demo of all six queries
run.bat test       REM correctness suite (vs brute force)
run.bat menu       REM interactive console
```

**Linux / macOS / Git Bash**
```
./run.sh web       # start interactive web frontend (port 8080)
./run.sh           # demo
./run.sh test      # correctness suite
./run.sh menu      # interactive console
```

**Manual**
```
javac -d out $(find src -name "*.java")        # Windows: list the package folders
java -cp out emergency.Main                    # add: test | menu
```

Run from the project root so `data/*.csv` is found; if the files are missing the
program falls back to an identical built-in dataset automatically.

---

## 5. Correctness self-test

`run.bat test` runs nine checks. The spatial structures are validated against a
brute-force `O(n)` reference over hundreds of randomized scenarios (including
duplicate coordinates) — about **6,000 range**, **3,000 nearest-neighbour** and
**2,000 radius** queries — plus edge cases and system behaviours:

```
[PASS] range query == brute force (6000 queries, with duplicates)
[PASS] nearest neighbour == brute force (3000 queries)
[PASS] radius query == brute force circle (2000 queries)
[PASS] empty tree behaves (no points, null nearest)
[PASS] single-point tree (inside / outside / nearest)
[PASS] duplicate coordinates all reported (expected 4)
[PASS] point location routes incidents to the right sector
[PASS] severity allocation removes the right number of ambulances
[PASS] redistribution restores coverage to an emptied sector
RESULT: 9 passed, 0 failed
```

---

## 6. Editing the dataset

Change `data/resources.csv` / `data/zones.csv` and re-run — no recompile needed.

```
resources.csv : id,name,type,x,y,available,icuBeds      type ∈ AMBULANCE|FIRE_TRUCK|POLICE_UNIT|HOSPITAL
zones.csv     : name,minX,minY,maxX,maxY
```

Lines that are blank or start with `#` are ignored. Coordinates are kilometres
on a 30 × 30 km grid.

---

## 7. Complexity summary

| Operation | Time |
|-----------|------|
| Build range tree | `O(n log n)` |
| Rectangular range report | `O(log n + k)`  *(fractional cascading)* |
| Nearest available unit | `O(log n + k)` per expanding window |
| Point location (sector of an incident) | `O(log n)` |
| Naïve scan (what we replace) | `O(n)` |
