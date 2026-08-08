"""Pulls the latest weather conditions for Rajahmundry, Andhra Pradesh, India every ~12 minutes
and writes them into the app's `weather` table (in the `db` Postgres service, not Airflow's own
metadata DB). Not wired into GraphQL yet — that's Phase 2.6.

Originally used the US National Weather Service API (Phase 2.5, Hoboken NJ). When the app moved
to Rajahmundry, NWS's `/points/{lat},{lon}` endpoint returned a hard 404 ("Data Unavailable For
Requested Point") — confirmed live, NWS only covers US coordinates. OpenWeather and Tomorrow.io
were both considered as replacements, but both require a signup + API key; Open-Meteo
(open-meteo.com) needs neither and is free for non-commercial use at this volume (one request
every 12 min), so it was used instead — no functional downside, and it keeps the project's
"no auth/no API keys needed" local-dev posture intact. Everything else about this DAG —
schedule, table, retry policy, upsert-on-conflict — is unchanged from Phase 2.5.
"""

from __future__ import annotations

import os
from datetime import timedelta

import pendulum
import psycopg2
import requests
from airflow.sdk import dag, task

# Rajahmundry city center, as given for this task (close to, but not identical to, the OSM
# import's bounding-box center in data/scripts/fetch_osm.py — both are within the same box).
RAJAHMUNDRY_LAT = 17.0005
RAJAHMUNDRY_LON = 81.8040

# Open-Meteo is model/grid-based, not physical weather stations like NWS — there's no real
# "station ID" to report. The `weather.station_id` column is NOT NULL (see schema.sql), so this
# constant fills that slot, identifying the source and point rather than a physical station.
DATA_SOURCE_ID = "open-meteo:rajahmundry"

OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"
CURRENT_FIELDS = (
    "temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m,"
    "wind_gusts_10m,surface_pressure,visibility,precipitation,weather_code"
)

# WMO weather interpretation codes (the set Open-Meteo's `weather_code` uses) mapped to short
# human-readable text, standing in for NWS's `textDescription` field. Not exhaustive — covers the
# common cases; unmapped codes fall back to "Weather code {n}" rather than silently guessing.
WMO_WEATHER_CODES = {
    0: "Clear sky",
    1: "Mainly clear",
    2: "Partly cloudy",
    3: "Overcast",
    45: "Fog",
    48: "Depositing rime fog",
    51: "Light drizzle",
    53: "Moderate drizzle",
    55: "Dense drizzle",
    61: "Slight rain",
    63: "Moderate rain",
    65: "Heavy rain",
    71: "Slight snow",
    73: "Moderate snow",
    75: "Heavy snow",
    80: "Slight rain showers",
    81: "Moderate rain showers",
    82: "Violent rain showers",
    95: "Thunderstorm",
    96: "Thunderstorm with slight hail",
    99: "Thunderstorm with heavy hail",
}


@dag(
    dag_id="nws_weather_ingest",
    description="Fetch the latest weather conditions for Rajahmundry into the weather table.",
    schedule=timedelta(minutes=12),
    start_date=pendulum.datetime(2024, 1, 1, tz="UTC"),
    catchup=False,
    max_active_runs=1,
    default_args={"retries": 2, "retry_delay": timedelta(minutes=2)},
    tags=["weather", "ingestion"],
)
def nws_weather_ingest():

    @task
    def fetch_observation() -> dict:
        """Fetch Open-Meteo's current conditions for Rajahmundry."""
        response = requests.get(
            OPEN_METEO_URL,
            params={
                "latitude": RAJAHMUNDRY_LAT,
                "longitude": RAJAHMUNDRY_LON,
                "current": CURRENT_FIELDS,
                "timezone": "UTC",
            },
            timeout=15,
        )
        response.raise_for_status()
        return response.json()["current"]

    @task
    def store_observation(current: dict) -> None:
        weather_code = current.get("weather_code")
        short_forecast = WMO_WEATHER_CODES.get(weather_code, f"Weather code {weather_code}")

        with psycopg2.connect(os.environ["DIGITALTWIN_DB_DSN"]) as conn, conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO weather (
                    station_id, observed_at, short_forecast,
                    temperature_c, dewpoint_c, wind_direction_deg, wind_speed_kmh, wind_gust_kmh,
                    barometric_pressure_pa, relative_humidity_pct, visibility_m,
                    precipitation_last_hour_mm
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (station_id, observed_at) DO NOTHING
                """,
                (
                    DATA_SOURCE_ID,
                    current["time"] + "Z",
                    short_forecast,
                    current.get("temperature_2m"),
                    current.get("dew_point_2m"),
                    current.get("wind_direction_10m"),
                    current.get("wind_speed_10m"),
                    current.get("wind_gusts_10m"),
                    # Open-Meteo reports hPa; the column is Pa (matches the unit NWS used).
                    current["surface_pressure"] * 100 if current.get("surface_pressure") is not None else None,
                    current.get("relative_humidity_2m"),
                    current.get("visibility"),
                    # Open-Meteo's `precipitation` covers its reporting interval (typically the
                    # last 15 min here), not literally a trailing hour like NWS's field did —
                    # closest available equivalent, not an exact match.
                    current.get("precipitation"),
                ),
            )

    store_observation(fetch_observation())


nws_weather_ingest()
