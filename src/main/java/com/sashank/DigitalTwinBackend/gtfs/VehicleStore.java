package com.sashank.DigitalTwinBackend.gtfs;

import com.sashank.DigitalTwinBackend.realtime.VehicleUpdatesChannel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Holds the latest known position for each vehicle in Redis (Phase 2.2) — this is the
 * source of truth for "latest known state", replaced wholesale on every poll.
 *
 * <p>Writes go to a staging key and get promoted via RENAME so a query never observes a
 * partially-replaced set of vehicles. Vehicles whose position actually changed (new or moved)
 * are published on {@link VehicleUpdatesChannel#NAME} for the {@code vehicleUpdates} subscription.
 */
@Component
public class VehicleStore {

    private static final String KEY = "vehicles";
    private static final String STAGING_KEY = "vehicles:staging";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public VehicleStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void replaceAll(Map<String, Vehicle> latest) {
        Map<String, Vehicle> previous = findAllById();

        if (latest.isEmpty()) {
            redisTemplate.delete(KEY);
            return;
        }
        Map<String, String> serialized = latest.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> writeJson(e.getValue())));
        redisTemplate.delete(STAGING_KEY);
        redisTemplate.opsForHash().putAll(STAGING_KEY, serialized);
        redisTemplate.rename(STAGING_KEY, KEY);

        publishChanges(previous, latest);
    }

    public List<Vehicle> findAll() {
        return findAllById().values().stream().toList();
    }

    private Map<String, Vehicle> findAllById() {
        return redisTemplate.<String, String>opsForHash().entries(KEY).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> readJson(e.getValue())));
    }

    private void publishChanges(Map<String, Vehicle> previous, Map<String, Vehicle> latest) {
        for (Vehicle vehicle : latest.values()) {
            Vehicle before = previous.get(vehicle.id());
            boolean positionChanged = before == null
                    || before.lat() != vehicle.lat()
                    || before.lng() != vehicle.lng();
            if (positionChanged) {
                redisTemplate.convertAndSend(VehicleUpdatesChannel.NAME, writeJson(vehicle));
            }
        }
    }

    private String writeJson(Vehicle vehicle) {
        try {
            return objectMapper.writeValueAsString(vehicle);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize vehicle " + vehicle.id(), e);
        }
    }

    private Vehicle readJson(String json) {
        try {
            return objectMapper.readValue(json, Vehicle.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize cached vehicle: " + json, e);
        }
    }
}