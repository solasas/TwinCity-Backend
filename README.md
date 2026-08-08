# Digital Twin of a City — Backend (Iteration 1)

Backend-only. A separate frontend (React, Vite dev server on `http://localhost:5173`) consumes
this API over HTTP. It does not exist in this repo.

## Neighborhood

**Rajahmundry (Rajamahendravaram), Andhra Pradesh, India** — covers the main town, bus stand
area, Government Hospital area, Innespeta, Danavaipeta, and the riverside area near the Godavari.
Bounding box used for the OSM extract: `16.975,81.765,17.020,81.825` (south, west, north, east).
Originally Hoboken, NJ (iteration 1 / Phase 2.1–2.5) — migrated to Rajahmundry without touching
the schema, GraphQL contract, or Phase 2 infrastructure (transit polling, Redis, subscriptions,
Airflow orchestration); only the data source and city-specific constants changed. See "Migration
notes" below for what that involved.

## Stack

- PostgreSQL 16 + PostGIS 3.4, run via Docker Compose.
- Real OpenStreetMap data (buildings, roads, schools, hospitals), fetched from the Overpass API.
- Spring Boot 4.1 + Spring for GraphQL, Spring Data JPA, Hibernate Spatial (JTS geometry types).
- Redis (vehicle store + cached queries), GraphQL subscriptions over WebSocket, Airflow (weather
  ingestion) — added in later phases, see "Current scope" below. Still no auth (local dev demo).

## Prerequisites

- Docker Desktop (or compatible daemon)
- Java 21 (the included `./mvnw` wrapper handles Maven itself)
- Python 3.9+ (only needed to run the one-time data import)

## 1. Run the database

```bash
docker compose up -d
```

This starts a `postgis/postgis:16-3.4` container and automatically runs
`data/sql/schema.sql` on first boot, creating the `buildings`, `roads`, `schools`, and
`hospitals` tables (each with a `GEOMETRY` column + GIST spatial index).

> **Port note:** the container publishes Postgres on host port **5433**, not 5432 — this
> avoids clashing with a locally-installed PostgreSQL server. Connection string:
> `postgresql://digitaltwin:digitaltwin@localhost:5433/digitaltwin`.

## 2. Import real OSM data

Data is fetched from the public Overpass API and imported with two small scripts in
`data/scripts/`. Public Overpass mirrors are sometimes slow/rate-limited — the fetch script
retries across three mirrors and caches each result to `data/raw/*.json`, so re-running it
only fetches what's missing. The raw JSON is checked into the repo, so you can skip step (a)
entirely and go straight to (b).

```bash
cd data
python3 -m venv .venv
.venv/bin/pip install psycopg2-binary

# a) Fetch raw OSM data (skips files that already exist in data/raw/)
.venv/bin/python scripts/fetch_osm.py

# b) Parse + load into PostGIS (idempotent — safe to re-run, upserts on osm_id)
.venv/bin/python scripts/import_osm.py
```

Expected row counts for Rajahmundry: ~13,200 buildings, ~2,500 roads, ~19 schools, ~220
hospitals/clinics (OSM tags many small private clinics as `amenity=hospital` in this area — not
a data-quality bug, just denser real-world tagging than Hoboken's single hospital).

## 3. Run the Spring Boot app

```bash
./mvnw spring-boot:run
```

GraphQL endpoint: **`http://localhost:8080/graphql`** (GraphiQL UI at `/graphiql`).

CORS is restricted to `http://localhost:5173` (Vite's default dev server port) — see
`spring.graphql.cors.*` in `src/main/resources/application.properties`.

> If port 8080 is already in use by something else on your machine, override it:
> `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`. The frontend
> contract assumes 8080, so only do this for local testing.

## SQL queries (verified directly in psql before any Java was written)

```sql
-- Hospitals within N km of a given school
SELECT h.name, ST_Distance(h.geom::geography, s.geom::geography) / 1000.0 AS distance_km
FROM hospitals h, schools s
WHERE s.id = :schoolId
  AND ST_DWithin(h.geom::geography, s.geom::geography, :km * 1000)
ORDER BY distance_km;

-- Nearest school to a given point (KNN, index-accelerated via <->)
SELECT * FROM schools
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
LIMIT 1;

-- Roads within N meters of a given facility
SELECT r.* FROM roads r, hospitals h
WHERE h.id = :hospitalId
  AND ST_DWithin(r.geom::geography, h.geom::geography, :meters);
```

These map directly onto `RoadRepository`, `SchoolRepository`, and `HospitalRepository` in
`src/main/java/.../repository/`.

## GraphQL: compound query example

```graphql
query {
  school(id: "1") {
    name
    location { lat lon }
    nearbyHospitals(withinKm: 1) {
      name
      distanceKm
    }
    nearbyRoads(withinMeters: 300) {
      name
      open
    }
  }
}
```

Expected response shape:

```json
{
  "data": {
    "school": {
      "name": "RAVINDRA BHARATHI SCHOOL",
      "location": { "lat": 17.0072333, "lon": 81.7984452 },
      "nearbyHospitals": [
        { "name": "Dr.vijaya Eye Hospital", "distanceKm": 0.32 }
      ],
      "nearbyRoads": [
        { "name": "Jawaharlal Nehru Road", "open": true },
        { "name": "happy street", "open": true }
      ]
    }
  }
}
```

## Simulating a road closure

```graphql
mutation {
  simulateRoadClosure(roadId: "135") {
    id
    name
    open
  }
}
```

This **toggles** the `open` boolean on the given road. `School.nearbyRoads` only ever
considers roads where `open = true`, so re-running the compound query above after closing a
road that was in the result set will show it disappear from `nearbyRoads`. Call the mutation
again to reopen it.

## Project layout

```
docker-compose.yml          # PostGIS service
data/sql/schema.sql         # DDL, auto-run by docker-compose on first boot
data/raw/*.json             # cached raw Overpass responses (real OSM data, no synthetic coords)
data/scripts/fetch_osm.py   # Overpass API -> data/raw/*.json
data/scripts/import_osm.py  # data/raw/*.json -> PostGIS (upsert by osm_id)
src/main/resources/graphql/schema.graphqls
src/main/java/.../model/        # JPA entities (JTS Polygon/LineString/Point geometry)
src/main/java/.../repository/   # Spring Data JPA + native PostGIS queries
src/main/java/.../graphql/      # @QueryMapping / @SchemaMapping / @MutationMapping controllers
```

## Weather (`weather` query)

Returns the latest row from the `weather` table, populated out-of-band by the Airflow
`nws_weather_ingest` DAG (Phase 2.5, see `airflow/dags/weather_ingest.py`) — this backend never
calls the weather API itself, it just reads whatever Airflow last wrote. If the DAG hasn't run
yet, `weather` returns a GraphQL error rather than fabricating a snapshot.

The DAG originally called the US National Weather Service API. NWS only covers US coordinates —
it returns a hard 404 for Rajahmundry — so the migration swapped it for
[Open-Meteo](https://open-meteo.com/) (free, no API key, worldwide coverage). Same schedule
(~12 min), same table, same retry policy; only the external call and field mapping changed.

```graphql
query { weather { stationId observedAt shortForecast temperatureC windSpeedKmh } }
```

## Vehicle feed (`vehicles` query, `vehicleUpdates` subscription)

**Simulated vehicle feed standing in for a real GTFS source, since Rajahmundry does not
currently publish one.** Checked before building this: no public GTFS-realtime feed exists for
APSRTC or Rajahmundry specifically — the only discoverable GTFS-shaped data for APSRTC is an
*expired, static* (schedule-only, no live positions) feed via a third-party aggregator, and
APSRTC's actual live bus tracking ("APSRTC LIVE TRACK") is a closed mobile app with no
documented public API. This is an honest substitution for a portfolio project, not something to
hide: `SimulatedVehiclePoller` (`src/main/java/.../simulation/`) generates ~10 buses/autos that
move back and forth along **real** imported road geometries (excluding `service`/`pedestrian`
ways — not realistic bus/auto routes), at the same poll cadence a real feed would use.

Nothing downstream knows or cares that the feed is simulated: it writes into `VehicleStore` via
the exact same `replaceAll(...)` call the original `GtfsRealtimePoller` (Phase 2.1) used, so
Redis storage, change-diffing, pub/sub, and the `vehicles`/`vehicleUpdates` GraphQL surface are
byte-for-byte unchanged. `GtfsRealtimePoller` itself is still in the codebase, just disabled
(`vehicle-feed.simulated=false` to re-enable it against a real feed, e.g. for a different city —
`GTFS_VEHICLE_POSITIONS_URL` still defaults to MBTA's public feed from Phase 2.1).

```graphql
query { vehicles { id route lat lng lastUpdated } }
subscription { vehicleUpdates { id route lat lng lastUpdated } }
```

## Congestion heuristic (`congestionLevel` query)

```graphql
query { congestionLevel(roadId: "135") }
```

**This is a fixed-threshold rule, not AI/ML.** `RoadCongestionHeuristic`
(`src/main/java/.../congestion/`) counts how many currently-tracked vehicles are within 150m of
the road's geometry and buckets the count into `LOW` / `MEDIUM` / `HIGH` via fixed thresholds
(`>=3` → `HIGH`, `>=1` → `MEDIUM`, else `LOW`). No model is trained, no weights are learned — it's
a haversine distance check plus two `if`s, deliberately simple. One known limitation: distance is
measured to road vertices, not true point-to-segment distance, so a vehicle mid-way along an
unusually long, sparse segment could be missed.

Unlike the original Hoboken/MBTA mismatch (where the vehicle feed was ~300km from the roads it
was supposedly near), the simulated feed above moves along the *same* real road network this
heuristic checks against, so `MEDIUM`/`HIGH` readings are now actually geographically meaningful
here rather than permanently reading `LOW`.

If this ever becomes an actual trained model (not currently planned), call it that explicitly —
don't relabel this heuristic as "AI" just because it sounds more impressive.

## Migration notes (Hoboken → Rajahmundry)

The neighborhood changed from Hoboken, NJ to Rajahmundry, Andhra Pradesh, India without touching
the schema, GraphQL contract, or Phase 2 infrastructure. What actually changed:

- **Bounding box** (`data/scripts/fetch_osm.py`): swapped to `16.975,81.765,17.020,81.825`.
  Verified via an Overpass count query before adopting it — 13,237 buildings, 2,534 roads, 19
  schools, 222 hospitals/clinics, comparably or more dense than the Hoboken box — so no widening
  toward Kadiam or the railway station was needed.
- **`fetch_osm.py` bug fix, unrelated to the city change**: Overpass mirrors return `406 Not
  Acceptable` for requests with no/generic `User-Agent` (confirmed live — identical request
  succeeds with one, fails without). This silently worked before only because the Hoboken raw
  JSON was already cached in `data/raw/`; a fresh fetch for Rajahmundry exposed it. Fixed by
  adding an explicit `User-Agent` header.
- **Data reload**: `data/raw/*.json` deleted and re-fetched (the script skips fetching if those
  files already exist). `buildings`/`roads`/`schools`/`hospitals` were `TRUNCATE`d before
  re-importing — `import_osm.py` only upserts by `osm_id`, and Hoboken/Rajahmundry `osm_id`s never
  collide, so without truncating first the two cities' data would sit mixed together in the same
  tables. `weather` was also truncated, clearing stale Hoboken observations for a clean cutover.
- **Weather DAG** (`airflow/dags/weather_ingest.py`): NWS only covers US coordinates — confirmed
  a hard 404 for Rajahmundry — so swapped to Open-Meteo (OpenWeather/Tomorrow.io were considered
  but both need an API key; Open-Meteo doesn't). See the "Weather" section above. Coordinates
  point at 17.0005°N, 81.8040°E.
- **Vehicle feed**: replaced with a simulation (`SimulatedVehiclePoller`) — Rajahmundry has no
  real GTFS-realtime feed to point the original poller at. See the "Vehicle feed" section above
  for what was checked and why. The original `GtfsRealtimePoller` is disabled, not deleted.
- **Doc-comments only** (no logic/schema change): `schema.sql` header, `schema.graphqls`
  descriptions, `RoadCongestionHeuristic`'s Javadoc — updated to reference Rajahmundry instead of
  Hoboken, and to stop claiming NWS as the weather source.
- **Frontend**: `VITE_MAP_CENTER_LAT`/`VITE_MAP_CENTER_LNG` (and their hardcoded fallback
  defaults, which were actually stale San Francisco coordinates, not even Hoboken) updated
  separately in the frontend repo — no API-contract or component changes needed there.
- **Known OSM data gap**: no POI in this Overpass extract is tagged `amenity=hospital` with a
  name containing "Government" — Rajahmundry's Government Hospital doesn't appear to be tagged
  that way in current OSM data for this area. Not something a bounding-box or query change can
  fix; it's a gap in OSM's tagging, not in this import.

## Current scope

As of the Rajahmundry migration: PostGIS-backed core schema (iteration 1), vehicle polling —
simulated by default, real GTFS-realtime capability retained but disabled (2.1), Redis-backed
vehicle store + cached compound queries (2.2), GraphQL subscriptions over WebSocket (2.3),
Airflow-orchestrated weather ingestion via Open-Meteo (2.5), and the `weather`/`congestionLevel`
queries (2.6). No auth (local dev demo throughout). No trained/learned models anywhere — every
"smart" behavior in this codebase is either a direct data read, an explicitly-labeled heuristic,
or an explicitly-labeled simulation.