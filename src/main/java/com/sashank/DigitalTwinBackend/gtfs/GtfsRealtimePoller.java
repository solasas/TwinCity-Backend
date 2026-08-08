package com.sashank.DigitalTwinBackend.gtfs;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Polls a GTFS-realtime VehiclePositions feed on a fixed interval and refreshes
 * {@link VehicleStore} with the latest snapshot.
 */
@Component
public class GtfsRealtimePoller {

    private static final Logger log = LoggerFactory.getLogger(GtfsRealtimePoller.class);

    private final VehicleStore vehicleStore;
    private final RestClient restClient;
    private final String feedUrl;

    public GtfsRealtimePoller(VehicleStore vehicleStore,
            @Value("${gtfs.realtime.vehicle-positions-url}") String feedUrl) {
        this.vehicleStore = vehicleStore;
        this.feedUrl = feedUrl;
        this.restClient = RestClient.create();
    }

    @Scheduled(
            initialDelayString = "${gtfs.realtime.poll-initial-delay-ms:0}",
            fixedDelayString = "${gtfs.realtime.poll-interval-ms:20000}")
    void poll() {
        byte[] body;
        try {
            body = restClient.get()
                    .uri(feedUrl)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.warn("Failed to fetch GTFS-realtime feed from {}: {}", feedUrl, e.getMessage());
            return;
        }
        if (body == null || body.length == 0) {
            log.warn("GTFS-realtime feed at {} returned an empty response", feedUrl);
            return;
        }

        FeedMessage feed;
        try {
            feed = FeedMessage.parseFrom(body);
        } catch (Exception e) {
            log.warn("Failed to parse GTFS-realtime feed from {}: {}", feedUrl, e.getMessage());
            return;
        }

        Map<String, Vehicle> latest = new HashMap<>();
        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasVehicle()) {
                continue;
            }
            VehiclePosition vp = entity.getVehicle();
            if (!vp.hasPosition()) {
                continue;
            }
            String id = vp.hasVehicle() && vp.getVehicle().hasId() ? vp.getVehicle().getId() : entity.getId();
            String route = vp.hasTrip() && vp.getTrip().hasRouteId() ? vp.getTrip().getRouteId() : null;
            String lastUpdated = (vp.hasTimestamp() ? Instant.ofEpochSecond(vp.getTimestamp()) : Instant.now())
                    .toString();

            latest.put(id, new Vehicle(id, route, vp.getPosition().getLatitude(), vp.getPosition().getLongitude(),
                    lastUpdated));
        }

        vehicleStore.replaceAll(latest);
        log.debug("Refreshed {} vehicle positions from {}", latest.size(), feedUrl);
    }
}