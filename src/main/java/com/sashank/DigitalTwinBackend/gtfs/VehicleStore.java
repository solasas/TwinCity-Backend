package com.sashank.DigitalTwinBackend.gtfs;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Holds the latest known position for each vehicle, replaced wholesale on every poll.
 * A simple in-memory store for now; Phase 2.2 moves this to Redis.
 */
@Component
public class VehicleStore {

    private volatile Map<String, Vehicle> vehiclesById = Map.of();

    public void replaceAll(Map<String, Vehicle> latest) {
        vehiclesById = Map.copyOf(latest);
    }

    public List<Vehicle> findAll() {
        return List.copyOf(vehiclesById.values());
    }
}