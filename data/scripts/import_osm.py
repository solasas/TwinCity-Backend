#!/usr/bin/env python3
"""Import raw Overpass JSON (data/raw/*.json) into the PostGIS tables.

Usage: .venv/bin/python import_osm.py
Requires the docker-compose db to be running (see README).
"""
from __future__ import annotations

import json
from pathlib import Path

import psycopg2
from psycopg2.extras import execute_values

RAW_DIR = Path(__file__).resolve().parent.parent / "raw"

DB_DSN = "host=localhost port=5433 dbname=digitaltwin user=digitaltwin password=digitaltwin"

ROAD_HIGHWAY_TAG = "highway"
BUILDING_TAG = "building"


def address_of(tags: dict) -> str | None:
    housenumber = tags.get("addr:housenumber")
    street = tags.get("addr:street")
    city = tags.get("addr:city")
    postcode = tags.get("addr:postcode")
    if not (housenumber or street):
        return None
    parts = [p for p in [
        " ".join(p for p in [housenumber, street] if p),
        city,
        postcode,
    ] if p]
    return ", ".join(parts) if parts else None


def ring_wkt(points: list[dict]) -> str:
    coords = ", ".join(f"{p['lon']} {p['lat']}" for p in points)
    return f"POLYGON(({coords}))"


def line_wkt(points: list[dict]) -> str:
    coords = ", ".join(f"{p['lon']} {p['lat']}" for p in points)
    return f"LINESTRING({coords})"


def point_wkt(lat: float, lon: float) -> str:
    return f"POINT({lon} {lat})"


def polygon_centroid(points: list[dict]) -> tuple[float, float]:
    """Shoelace-formula centroid of a (possibly closed) ring; falls back to the
    vertex average for degenerate rings (e.g. all points collinear)."""
    pts = points[:-1] if points[0] == points[-1] else points
    area = 0.0
    cx = 0.0
    cy = 0.0
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]["lon"], pts[i]["lat"]
        x1, y1 = pts[(i + 1) % n]["lon"], pts[(i + 1) % n]["lat"]
        cross = x0 * y1 - x1 * y0
        area += cross
        cx += (x0 + x1) * cross
        cy += (y0 + y1) * cross
    area /= 2.0
    if abs(area) < 1e-12:
        lon = sum(p["lon"] for p in pts) / n
        lat = sum(p["lat"] for p in pts) / n
        return lat, lon
    cx /= 6 * area
    cy /= 6 * area
    return cy, cx


def load(name: str) -> list[dict]:
    return json.loads((RAW_DIR / f"{name}.json").read_text())["elements"]


def import_buildings(cur):
    rows = []
    for e in load("buildings"):
        tags = e.get("tags", {})
        geometry = e.get("geometry")
        if not geometry or len(geometry) < 4:
            continue
        rows.append((
            e["id"],
            tags.get("name"),
            tags.get(BUILDING_TAG),
            address_of(tags),
            ring_wkt(geometry),
        ))
    execute_values(
        cur,
        """
        INSERT INTO buildings (osm_id, name, type, address, geom)
        VALUES %s
        ON CONFLICT (osm_id) DO UPDATE SET
            name = EXCLUDED.name, type = EXCLUDED.type,
            address = EXCLUDED.address, geom = EXCLUDED.geom
        """,
        rows,
        template="(%s, %s, %s, %s, ST_GeomFromText(%s, 4326))",
    )
    print(f"buildings: inserted/updated {len(rows)}")


def import_roads(cur):
    rows = []
    for e in load("roads"):
        tags = e.get("tags", {})
        geometry = e.get("geometry")
        if not geometry or len(geometry) < 2:
            continue
        rows.append((
            e["id"],
            tags.get("name"),
            tags.get(ROAD_HIGHWAY_TAG),
            line_wkt(geometry),
        ))
    execute_values(
        cur,
        """
        INSERT INTO roads (osm_id, name, type, geom)
        VALUES %s
        ON CONFLICT (osm_id) DO UPDATE SET
            name = EXCLUDED.name, type = EXCLUDED.type, geom = EXCLUDED.geom
        """,
        rows,
        template="(%s, %s, %s, ST_GeomFromText(%s, 4326))",
    )
    print(f"roads: inserted/updated {len(rows)}")


def import_points(cur, name: str, table: str, amenity_tag: str):
    rows = []
    for e in load(name):
        tags = e.get("tags", {})
        if e["type"] == "node":
            lat, lon = e["lat"], e["lon"]
        else:
            geometry = e.get("geometry")
            if not geometry:
                continue
            lat, lon = polygon_centroid(geometry)
        rows.append((
            e["id"],
            tags.get("name"),
            tags.get("amenity", amenity_tag),
            address_of(tags),
            point_wkt(lat, lon),
        ))
    execute_values(
        cur,
        f"""
        INSERT INTO {table} (osm_id, name, type, address, geom)
        VALUES %s
        ON CONFLICT (osm_id) DO UPDATE SET
            name = EXCLUDED.name, type = EXCLUDED.type,
            address = EXCLUDED.address, geom = EXCLUDED.geom
        """,
        rows,
        template="(%s, %s, %s, %s, ST_GeomFromText(%s, 4326))",
    )
    print(f"{table}: inserted/updated {len(rows)}")


def main():
    conn = psycopg2.connect(DB_DSN)
    try:
        with conn:
            with conn.cursor() as cur:
                import_buildings(cur)
                import_roads(cur)
                import_points(cur, "schools", "schools", "school")
                import_points(cur, "hospitals", "hospitals", "hospital")
    finally:
        conn.close()
    print("done.")


if __name__ == "__main__":
    main()
