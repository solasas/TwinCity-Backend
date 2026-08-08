package com.sashank.DigitalTwinBackend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Read-only: rows are written by the Airflow {@code nws_weather_ingest} DAG (Phase 2.5), not this app. */
@Entity
@Table(name = "weather")
public class Weather {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id", nullable = false)
    private String stationId;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "short_forecast")
    private String shortForecast;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(name = "dewpoint_c")
    private Double dewpointC;

    @Column(name = "wind_direction_deg")
    private Double windDirectionDeg;

    @Column(name = "wind_speed_kmh")
    private Double windSpeedKmh;

    @Column(name = "wind_gust_kmh")
    private Double windGustKmh;

    @Column(name = "barometric_pressure_pa")
    private Double barometricPressurePa;

    @Column(name = "relative_humidity_pct")
    private Double relativeHumidityPct;

    @Column(name = "visibility_m")
    private Double visibilityM;

    @Column(name = "precipitation_last_hour_mm")
    private Double precipitationLastHourMm;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;

    public Long getId() {
        return id;
    }

    public String getStationId() {
        return stationId;
    }

    public OffsetDateTime getObservedAt() {
        return observedAt;
    }

    public String getShortForecast() {
        return shortForecast;
    }

    public Double getTemperatureC() {
        return temperatureC;
    }

    public Double getDewpointC() {
        return dewpointC;
    }

    public Double getWindDirectionDeg() {
        return windDirectionDeg;
    }

    public Double getWindSpeedKmh() {
        return windSpeedKmh;
    }

    public Double getWindGustKmh() {
        return windGustKmh;
    }

    public Double getBarometricPressurePa() {
        return barometricPressurePa;
    }

    public Double getRelativeHumidityPct() {
        return relativeHumidityPct;
    }

    public Double getVisibilityM() {
        return visibilityM;
    }

    public Double getPrecipitationLastHourMm() {
        return precipitationLastHourMm;
    }

    public OffsetDateTime getFetchedAt() {
        return fetchedAt;
    }
}