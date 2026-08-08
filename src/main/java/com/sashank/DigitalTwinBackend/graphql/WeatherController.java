package com.sashank.DigitalTwinBackend.graphql;

import com.sashank.DigitalTwinBackend.model.Weather;
import com.sashank.DigitalTwinBackend.repository.WeatherRepository;
import java.util.NoSuchElementException;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WeatherController {

    private final WeatherRepository weatherRepository;

    public WeatherController(WeatherRepository weatherRepository) {
        this.weatherRepository = weatherRepository;
    }

    @QueryMapping
    Weather weather() {
        return weatherRepository.findFirstByOrderByObservedAtDesc()
                .orElseThrow(() -> new NoSuchElementException(
                        "No weather data yet — has the Airflow nws_weather_ingest DAG run?"));
    }

    @SchemaMapping(typeName = "WeatherSnapshot", field = "observedAt")
    String observedAt(Weather weather) {
        return weather.getObservedAt().toString();
    }

    @SchemaMapping(typeName = "WeatherSnapshot", field = "fetchedAt")
    String fetchedAt(Weather weather) {
        return weather.getFetchedAt().toString();
    }
}