# Digital Twin of a City — Backend (Iteration 1)

Backend-only. A separate frontend (React, Vite dev server on `http://localhost:5173`) consumes
this API over HTTP. It does not exist in this repo.

## Neighborhood

**Hoboken, NJ, USA** — a compact (~3.3 km²), walkable city with a regular street grid, a real
hospital (Hoboken University Medical Center), and multiple schools. Bounding box used for the
OSM extract: `40.7295,-74.0435,40.7600,-74.0245` (south, west, north, east).

## Stack

- PostgreSQL 16 + PostGIS 3.4, run via Docker Compose.
- Real OpenStreetMap data (buildings, roads, schools, hospitals), fetched from the Overpass API.
- Spring Boot 4.1 + Spring for GraphQL, Spring Data JPA, Hibernate Spatial (JTS geometry types).
- No Redis, no subscriptions, no auth — out of scope for this iteration.

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

Expected row counts for Hoboken: ~6,200 buildings, ~1,400 roads, ~20 schools, 1 hospital.

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
  school(id: "3") {
    name
    location { lat lon }
    nearbyHospitals(withinKm: 5) {
      name
      distanceKm
    }
    nearbyRoads(withinMeters: 200) {
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
      "name": "Stevens Cooperative School",
      "location": { "lat": 40.7406745, "lon": -74.0275376 },
      "nearbyHospitals": [
        { "name": "Hoboken University Medical Center", "distanceKm": 0.55 }
      ],
      "nearbyRoads": [
        { "name": "River Street", "open": true },
        { "name": "4th Street", "open": true }
      ]
    }
  }
}
```

## Simulating a road closure

```graphql
mutation {
  simulateRoadClosure(roadId: "585") {
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

## Out of scope (this iteration)

No real-time feeds, no Airflow, no Redis, no GraphQL subscriptions/WebSockets, no auth, no
AI/ML features.