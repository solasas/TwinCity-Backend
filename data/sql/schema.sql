-- Digital Twin of a City — iteration 1 schema
-- Neighborhood: Hoboken, NJ, USA
-- Loaded automatically by docker-compose (mounted into /docker-entrypoint-initdb.d)

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS buildings (
    id       BIGSERIAL PRIMARY KEY,
    osm_id   BIGINT UNIQUE NOT NULL,
    name     TEXT,
    type     TEXT,              -- OSM building=* tag value (e.g. residential, apartments, retail)
    address  TEXT,
    geom     GEOMETRY(Polygon, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_buildings_geom ON buildings USING GIST (geom);

CREATE TABLE IF NOT EXISTS roads (
    id       BIGSERIAL PRIMARY KEY,
    osm_id   BIGINT UNIQUE NOT NULL,
    name     TEXT,
    type     TEXT,              -- OSM highway=* tag value (e.g. residential, primary, footway)
    open     BOOLEAN NOT NULL DEFAULT TRUE,
    geom     GEOMETRY(LineString, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_roads_geom ON roads USING GIST (geom);

CREATE TABLE IF NOT EXISTS schools (
    id       BIGSERIAL PRIMARY KEY,
    osm_id   BIGINT UNIQUE NOT NULL,
    name     TEXT,
    type     TEXT,              -- OSM amenity/isced level (e.g. school, college)
    address  TEXT,
    geom     GEOMETRY(Point, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_schools_geom ON schools USING GIST (geom);

CREATE TABLE IF NOT EXISTS hospitals (
    id       BIGSERIAL PRIMARY KEY,
    osm_id   BIGINT UNIQUE NOT NULL,
    name     TEXT,
    type     TEXT,              -- OSM amenity value (hospital, clinic, doctors)
    address  TEXT,
    geom     GEOMETRY(Point, 4326) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_hospitals_geom ON hospitals USING GIST (geom);

-- Phase 2.5: populated by the Airflow `nws_weather_ingest` DAG (data/../airflow/dags), polling the
-- NWS API every ~12 min for the station nearest Hoboken. Not yet exposed via GraphQL (Phase 2.6).
CREATE TABLE IF NOT EXISTS weather (
    id BIGSERIAL PRIMARY KEY,
    station_id TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    short_forecast TEXT,              -- e.g. "Fog/Mist", "Mostly Cloudy"
    temperature_c DOUBLE PRECISION,
    dewpoint_c DOUBLE PRECISION,
    wind_direction_deg DOUBLE PRECISION,
    wind_speed_kmh DOUBLE PRECISION,
    wind_gust_kmh DOUBLE PRECISION,
    barometric_pressure_pa DOUBLE PRECISION,
    relative_humidity_pct DOUBLE PRECISION,
    visibility_m DOUBLE PRECISION,
    precipitation_last_hour_mm DOUBLE PRECISION,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (station_id, observed_at)
);
CREATE INDEX IF NOT EXISTS idx_weather_observed_at ON weather (observed_at DESC);