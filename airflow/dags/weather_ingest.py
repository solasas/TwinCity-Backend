"""Phase 2.5: pulls the latest NWS observation for Hoboken, NJ every ~12 minutes and writes it
into the app's `weather` table (in the `db` Postgres service, not Airflow's own metadata DB).
Not wired into GraphQL yet — that's Phase 2.6.
"""

from __future__ import annotations

import os
from datetime import timedelta

import pendulum
import psycopg2
import requests
from airflow.sdk import dag, task

# Center of the Hoboken, NJ bounding box used elsewhere in this repo (data/scripts/fetch_osm.py).
HOBOKEN_LAT = 40.745
HOBOKEN_LON = -74.034

# NWS requires an identifiable User-Agent; it rejects generic/default ones.
NWS_HEADERS = {
    "User-Agent": "DigitalTwinBackend weather DAG (contact: solasa.dev@gmail.com)",
    "Accept": "application/geo+json",
}


@dag(
    dag_id="nws_weather_ingest",
    description="Fetch the latest NWS observation for Hoboken, NJ into the weather table.",
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
        """Resolve Hoboken's nearest NWS station and return its latest observation."""
        points = requests.get(
            f"https://api.weather.gov/points/{HOBOKEN_LAT},{HOBOKEN_LON}",
            headers=NWS_HEADERS,
            timeout=15,
        )
        points.raise_for_status()
        stations_url = points.json()["properties"]["observationStations"]

        stations = requests.get(stations_url, headers=NWS_HEADERS, timeout=15)
        stations.raise_for_status()
        station_id = stations.json()["features"][0]["properties"]["stationIdentifier"]

        observation = requests.get(
            f"https://api.weather.gov/stations/{station_id}/observations/latest",
            headers=NWS_HEADERS,
            timeout=15,
        )
        observation.raise_for_status()

        properties = observation.json()["properties"]
        properties["_station_id"] = station_id
        return properties

    @task
    def store_observation(properties: dict) -> None:
        def scalar(field: str):
            value = properties.get(field)
            return value.get("value") if isinstance(value, dict) else value

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
                    properties["_station_id"],
                    properties["timestamp"],
                    properties.get("textDescription"),
                    scalar("temperature"),
                    scalar("dewpoint"),
                    scalar("windDirection"),
                    scalar("windSpeed"),
                    scalar("windGust"),
                    scalar("barometricPressure"),
                    scalar("relativeHumidity"),
                    scalar("visibility"),
                    scalar("precipitationLastHour"),
                ),
            )

    store_observation(fetch_observation())


nws_weather_ingest()