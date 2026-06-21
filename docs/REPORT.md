# Project 29 — Intelligent Emergency Resource Allocation and Ambulance Dispatch System

### Using Point Location, Orthogonal Range Search, and Fractional Cascading

**Course:** Data Structures and Algorithms · **Language:** Java (8+)

---

## Abstract

Emergency response time is the single most important factor in survival during
road accidents, cardiac arrests, fires and natural disasters. A modern city
operates hundreds of ambulances, fire engines, police units and hospitals
distributed across the map. Answering operational questions — *which units are
free in this district? which is the nearest ambulance? which hospitals within
5 km have an ICU bed?* — by scanning every resource costs `O(n)` per query and
does not scale.

This project builds an **Intelligent Emergency Resource Allocation and Dispatch
System** that pre-processes resource locations into computational-geometry data
structures so the same questions are answered in **logarithmic time**. The core
index is a **two-dimensional orthogonal range tree augmented with fractional
cascading**, giving `O(log n + k)` rectangular range reporting. **Point
location** by slab decomposition routes each incident to its responsible city
sector in `O(log n)`. On top of these, the system performs severity-based
allocation and dynamic redistribution of ambulances after dispatch.

---

## 1. Problem Statement

Develop a system that answers, efficiently and repeatedly, the following queries
over a set of geographically distributed emergency resources:

1. Find all **available ambulances within a specified geographic region**.
2. Identify the **nearest available ambulance** to an accident location.
3. Retrieve all **hospitals within a 5 km radius having ICU availability**.
4. **Allocate emergency vehicles** based on incident severity.
5. **Dynamically redistribute ambulances** after dispatch.
6. **Generate a coverage map** for emergency-response planning.

Instead of an `O(n)` scan per query, the system uses **orthogonal range trees
and fractional cascading** to answer multidimensional spatial queries
efficiently, reducing dispatch latency for repeated emergency-location searches.

---

## 2. Objectives

* Implement a correct 2-D **orthogonal range tree** from scratch.
* Add **fractional cascading** to reach the optimal `O(log n + k)` query bound.
* Implement **point location** (slab decomposition) for sector routing.
* Layer realistic **emergency-dispatch logic** (severity, ICU, redistribution).
* **Verify** every structure against a brute-force reference.

---

## 3. Background — Data Structures

### 3.1 Orthogonal Range Tree

A range tree answers *axis-aligned rectangle* queries: report all points inside
`[x1,x2] × [y1,y2]`.

* The **primary tree** is a balanced BST on the x-coordinate; the input points
  are stored in its leaves.
* Each node `v` stores an **associated structure**: an array of all points in
  `v`'s subtree, **sorted by y**.
* A query first locates `O(log n)` **canonical nodes** — nodes whose entire
  subtree lies inside the x-range. For each, the points with y in `[y1,y2]` are
  reported from its sorted y-array using binary search.

The number of canonical nodes is `O(log n)`; a binary search at each costs
`O(log n)`, so a plain range tree answers a query in **`O(log² n + k)`** where
`k` is the number of reported points. Construction is `O(n log n)` time and
`O(n log n)` space.

### 3.2 Fractional Cascading

The `log² n` term comes from binary-searching for the **same** y-threshold `y1`
independently at every canonical node. Fractional cascading removes one `log`
factor:

* With every entry of a node's y-array we store **two pointers** — to the
  position of the same y-value in the y-array of the **left** child and of the
  **right** child.
* The query performs **one** binary search for `y1` at the root, then simply
  **follows the pointers** down the tree. Each step from a node to a child is
  `O(1)`, so the y-threshold is maintained for free along the whole traversal.

The query cost drops to the optimal **`O(log n + k)`**. The pointers are
computed in **linear time** while merging the two children's y-arrays, so build
cost remains `O(n log n)`.

> **Why the cascade is valid.** A child's y-array is a *subsequence* of the
> parent's. If `i` is the first index in the parent with `y ≥ y1`, then the
> first child element with `y ≥ parent[i].y` is exactly the first child element
> with `y ≥ y1` (no child element can sit between `y1` and `parent[i].y`).
> Hence the stored pointer lands on the correct position without re-searching.

### 3.3 Point Location (Slab Decomposition)

Given a planar subdivision into regions, point location reports which region
contains a query point. For axis-aligned rectangular regions the classic method
is **slab decomposition**:

* Every vertical rectangle edge induces a vertical line; consecutive lines bound
  vertical **slabs**.
* Inside one slab the regions appear as **disjoint y-intervals**, stored sorted.
* A query **binary-searches x** to select the slab, then **binary-searches y**
  inside it — `O(log n)` per query, `O(n²)` space in the worst case.

In this system the regions are **response zones** (city sectors), so point
location tells the dispatcher which control room owns an incident.

---

## 4. System Design

```
                         +------------------------+
                         |        Main            |  demo / test / menu
                         +-----------+------------+
                                     |
                         +-----------v------------+
                         |     DispatchSystem      |  operational queries
                         |  (façade / policies)    |
                         +--+------------------+---+
                            |                  |
              +-------------v---+        +-----v-------------+
              |  RangeTree2D    |        |  PointLocation     |
              |  + fractional   |        |  (slab decomp.)    |
              |    cascading    |        +--------------------+
              +-------+---------+
                      |
            +---------v----------+        model: EmergencyResource, Incident,
            | geometry: Point,   |        Zone, Severity, IncidentType,
            | Rectangle          |        ResourceType, DispatchPlan
            +--------------------+        data: CityLoader / SampleCity (CSV)
```

**Package layout**

| Package | Responsibility |
|---------|----------------|
| `emergency.geometry` | `Point`, `Rectangle` primitives (km grid) |
| `emergency.structures` | `RangeTree2D` (+ fractional cascading), `PointLocation` |
| `emergency.model` | domain types: resources, incidents, zones, severity, plan |
| `emergency.system` | `DispatchSystem` façade, `CityLoader`, `SampleCity` |
| `emergency.test` | `Verification` — brute-force correctness suite |
| `emergency` | `Main` driver |

---

## 5. Algorithms (Pseudocode)

### 5.1 Build (range tree + cascade pointers)

```
BUILD(points sorted by x, lo, hi):
    if lo == hi: return Leaf(points[lo])           # y-array = [point]
    mid    = (lo + hi) / 2
    node.splitX = points[mid].x                    # max x in left subtree
    node.left   = BUILD(points, lo, mid)
    node.right  = BUILD(points, mid+1, hi)
    node.ys     = MERGE-BY-Y(left.ys, right.ys)    # also fills leftPtr/rightPtr
    return node
```

`MERGE-BY-Y` merges two y-sorted arrays (linear) and, scanning once from the
top, records for every entry the first index in each child whose y is `≥` it.

### 5.2 Range query with fractional cascading

```
RANGE-QUERY(x1, x2, y1, y2):
    pos = LOWER-BOUND-Y(root.ys, y1)               # ONE binary search
    v   = root
    while v is internal and (x2 < v.splitX or x1 > v.splitX):
        if x2 < v.splitX: pos = v.leftPtr[pos];  v = v.left
        else            : pos = v.rightPtr[pos]; v = v.right
    if v is leaf: report v if inside; return
    split = v
    # left spine follows x1, reporting right children as canonical:
    descend left from split, at each node with x1 ≤ splitX:
        REPORT(node.right, node.rightPtr[pos] ... while y ≤ y2)
        follow leftPtr; else follow rightPtr
    # right spine follows x2, reporting left children symmetrically
```

`REPORT` emits a canonical subtree's y-array from the cascaded position forward
while `y ≤ y2`. No binary search occurs below the root.

### 5.3 Nearest neighbour (expanding window, provably correct)

```
NEAREST(qx, qy, predicate):
    r = 1 km
    repeat: hits = RANGE-QUERY(box(qx,qy,r)) filtered by predicate
            until hits non-empty or box covers the whole map
    d = min Euclidean distance in hits
    # any point closer than d lies inside box(qx,qy,d):
    return argmin Euclidean over RANGE-QUERY(box(qx,qy,d)) filtered
```

### 5.4 Point location

```
LOCATE(px, py):
    s = slab index by binary search of px among slab boundaries
    k = interval by binary search of py among slab s's sorted intervals
    return zone[s][k] if py inside that interval else null
```

### 5.5 Severity-based allocation

```
ALLOCATE(incident):
    zone = LOCATE(incident.x, incident.y)
    repeat severity.ambulancesNeeded times:
        a = NEAREST available AMBULANCE; mark a busy; attach a
    if severity.requiresIcu: attach NEAREST hospital with a free ICU bed
    if type.needsFireTruck : attach NEAREST available FIRE_TRUCK
    if type.needsPolice    : attach NEAREST available POLICE_UNIT
```

### 5.6 Dynamic redistribution

```
REDISTRIBUTE():
    for each zone with no available ambulance:
        donor = nearest available ambulance from a zone that has a surplus (>1)
        relocate donor to the needy zone's centre; rebuild the range tree
```

---

## 6. Complexity Analysis

Let `n` be the number of resources and `k` the size of a query's output.

| Operation | This system | Naïve scan |
|-----------|-------------|------------|
| Build index | `O(n log n)` time, `O(n log n)` space | — |
| Rectangular range report | **`O(log n + k)`** | `O(n)` |
| Nearest available unit | `O(log n + k)` per window, `O(log² n)` typical | `O(n)` |
| Hospitals within radius | `O(log n + k)` | `O(n)` |
| Point location (sector) | `O(log n)` | `O(z)` zones |
| Allocation (constant units) | `O(log² n)` | `O(n)` |
| Relocation (rebuild) | `O(n log n)` | — |

**Derivation of the range bound.** There are `O(log n)` canonical nodes; with
fractional cascading the start position in each is reached in `O(1)` from its
parent, and reporting costs `O(1)` per output point. Summed: `O(log n + k)`.

---

## 7. Implementation Notes

* Coordinates are a **planar kilometre grid** (a local projection of latitude /
  longitude), so Euclidean distance ≈ ground distance in km.
* A circular *"within r km"* query is a bounding-box range query followed by an
  exact Euclidean-distance filter.
* Duplicate x-coordinates are handled by the standard half-open routing rule
  (`x1` inclusive-left, `x2` strict-left), so no coordinate perturbation is
  needed.
* Availability is a mutable flag; dispatch and return-to-service need no rebuild.
  Only **relocation** changes positions and rebuilds the index.
* The city is read from `data/resources.csv` and `data/zones.csv`, with an
  identical built-in dataset as a fallback.

---

## 8. Results

### 8.1 Functional demonstration (`java emergency.Main`)

* **Region query** (South-West sector) lists the two free ambulances there.
* **Nearest** ambulance to accident `(18, 23)` → `A5` at `4.47 km`.
* **ICU within 5 km** of `(7, 11)` → `City-General` at `3.16 km`, 6 ICU beds.
* **CRITICAL road accident** at `(19, 24)`: 2 ambulances + ICU hospital + fire
  engine + police, routed to sector **North-East** by point location.
* **Redistribution**: after two South-East calls empty that sector, `A6` is
  relocated from North-East, restoring coverage (response `13.29 km → 0.00 km`).

### 8.2 Correctness (`java emergency.Main test`)

All nine checks pass, including ~6,000 random range queries, ~3,000
nearest-neighbour queries and ~2,000 radius queries, each agreeing exactly with
an independent brute-force computation, plus edge cases (empty tree, single
point, duplicate coordinates) and system behaviours (point location, allocation,
redistribution).

```
RESULT: 9 passed, 0 failed
ALL CHECKS PASSED
```

---

## 9. Limitations and Future Work

* **Distance metric** is straight-line; a real deployment would use a road
  network (shortest-path / travel-time) instead of Euclidean km.
* **Relocation rebuilds** the whole index (`O(n log n)`); a dynamic/fractional
  range tree or a kd-tree with deletion could update incrementally.
* **Point-location space** is `O(n²)` worst case; a trapezoidal map or a
  persistent search tree would reduce it to `O(n)` space, `O(log n)` query.
* Possible extensions: real-time traffic weighting, multi-incident batching,
  predictive pre-positioning of ambulances, and a map-based GUI.

---

## 10. Conclusion

The project demonstrates how classical computational-geometry data structures —
**orthogonal range trees**, **fractional cascading** and **point location** —
turn an `O(n)`-per-query dispatch problem into a `O(log n + k)` one. The
implementation is correct (validated against brute force), modular, and models a
realistic emergency-response workflow including severity-based allocation and
post-dispatch redistribution.

---

## 11. References

1. M. de Berg, O. Cheong, M. van Kreveld, M. Overmars. *Computational Geometry:
   Algorithms and Applications*, 3rd ed., Springer, 2008 — Ch. 5 (Range Trees),
   Ch. 6 (Point Location).
2. B. Chazelle, L. J. Guibas. *Fractional Cascading: I. A Data Structuring
   Technique*. Algorithmica, 1986.
3. J. L. Bentley. *Decomposable Searching Problems*. Information Processing
   Letters, 1979.
4. T. H. Cormen, C. E. Leiserson, R. L. Rivest, C. Stein. *Introduction to
   Algorithms*, 3rd ed., MIT Press, 2009.
```
