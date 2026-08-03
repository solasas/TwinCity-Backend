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