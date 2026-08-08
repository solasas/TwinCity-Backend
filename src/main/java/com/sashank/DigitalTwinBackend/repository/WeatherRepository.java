package com.sashank.DigitalTwinBackend.repository;

import com.sashank.DigitalTwinBackend.model.Weather;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRepository extends JpaRepository<Weather, Long> {

    Optional<Weather> findFirstByOrderByObservedAtDesc();
}