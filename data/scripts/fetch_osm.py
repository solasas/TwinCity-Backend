#!/usr/bin/env python3
"""Fetch real OSM data for Hoboken, NJ from the Overpass API and save raw JSON.

Usage: python3 fetch_osm.py
Output: ../raw/{buildings,roads,schools,hospitals}.json
"""
import json
import time
import urllib.parse
import urllib.request
import urllib.error
from pathlib import Path

# Hoboken, NJ bounding box: south, west, north, east
BBOX = "40.7295,-74.0435,40.7600,-74.0245"

MIRRORS = [
    "https://overpass-api.de/api/interpreter",
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]

QUERIES = {
    "buildings": f'[out:json][timeout:180];(way["building"]({BBOX});); out geom;',
    "roads": (
        f'[out:json][timeout:180];'
        f'(way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|'
        f'residential|living_street|pedestrian|service)$"]({BBOX});); out geom;'
    ),
    "schools": f'[out:json][timeout:180];(node["amenity"="school"]({BBOX});way["amenity"="school"]({BBOX});); out geom;',
    "hospitals": f'[out:json][timeout:180];(node["amenity"="hospital"]({BBOX});way["amenity"="hospital"]({BBOX});); out geom;',
}

RAW_DIR = Path(__file__).resolve().parent.parent / "raw"


def fetch(query: str, attempts_per_mirror: int = 2, backoff: float = 5.0) -> dict:
    last_err = None
    body = urllib.parse.urlencode({"data": query}).encode("utf-8")
    for mirror in MIRRORS:
        for attempt in range(1, attempts_per_mirror + 1):
            try:
                print(f"  trying {mirror} (attempt {attempt})...")
                req = urllib.request.Request(mirror, data=body, method="POST")
                with urllib.request.urlopen(req, timeout=190) as resp:
                    raw = resp.read()
                    data = json.loads(raw)
                    if "elements" in data:
                        return data
                    last_err = RuntimeError(f"unexpected response: {raw[:200]!r}")
            except (urllib.error.URLError, urllib.error.HTTPError, json.JSONDecodeError, TimeoutError) as e:
                last_err = e
                print(f"    failed: {e}")
                time.sleep(backoff)
    raise RuntimeError(f"all mirrors failed: {last_err}")


def main():
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    for name, query in QUERIES.items():
        out_path = RAW_DIR / f"{name}.json"
        if out_path.exists():
            print(f"{name}: already fetched at {out_path}, skipping (delete to re-fetch)")
            continue
        print(f"Fetching {name}...")
        data = fetch(query)
        out_path.write_text(json.dumps(data))
        print(f"  saved {len(data['elements'])} elements -> {out_path}")


if __name__ == "__main__":
    main()