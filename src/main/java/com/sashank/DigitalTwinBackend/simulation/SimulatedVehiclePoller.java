package com.sashank.DigitalTwinBackend.simulation;

import static com.sashank.DigitalTwinBackend.geo.GeoDistance.haversineMeters;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import com.sashank.DigitalTwinBackend.gtfs.VehicleStore;
import com.sashank.DigitalTwinBackend.model.Road;
import com.sashank.DigitalTwinBackend.repository.RoadRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real GTFS-realtime feed: Rajahmundry has no public GTFS or GTFS-realtime
 * source (checked — APSRTC's only discoverable feed is an expired third-party *static* schedule
 * export with no realtime component, and its live tracking is a closed mobile app with no public
 * API). This generates a handful of buses/autos that move back and forth along real imported
 * road geometries, at the same poll cadence and through the exact same
 * {@link VehicleStore#replaceAll} call the real {@code GtfsRealtimePoller} used — so Redis
 * storage, diffing, pub/sub, and the {@code vehicles}/{@code vehicleUpdates} GraphQL surface are
 * completely unchanged. Active by default; see {@code GtfsRealtimePoller} for the real-feed path
 * this replaces (kept working, just disabled, in case a future city has an actual feed).
 */
@Component
@ConditionalOnProperty(name = "vehicle-feed.simulated", havingValue = "true", matchIfMissing = true)
public class SimulatedVehiclePoller {

    private static final Logger log = LoggerFactory.getLogger(SimulatedVehiclePoller.class);

    /** highway=* values excluded as not realistically bus/auto routes (driveways, footpaths). */
    private static final Set<String> EXCLUDED_ROAD_TYPES = Set.of("service", "pedestrian");

    private final VehicleStore vehicleStore;
    private final List<SimulatedVehicle> vehicles;
    private final double tickIntervalSeconds;

    public SimulatedVehiclePoller(VehicleStore vehicleStore, RoadRepository roadRepository,
            @Value("${simulated-vehicles.count:10}") int vehicleCount,
            @Value("${gtfs.realtime.poll-interval-ms:20000}") long pollIntervalMs) {
        this.vehicleStore = vehicleStore;
        this.tickIntervalSeconds = pollIntervalMs / 1000.0;

        List<Road> candidateRoads = roadRepository.findAll().stream()
                .filter(r -> r.isOpen() && r.getGeom() != null
                        && r.getGeom().getCoordinates().length >= 2
                        && !EXCLUDED_ROAD_TYPES.contains(r.getType()))
                .toList();

        if (candidateRoads.isEmpty()) {
            log.warn("No vehicle-capable roads found — simulated vehicle feed will produce no vehicles");
            this.vehicles = List.of();
            return;
        }

        Random random = new Random();
        this.vehicles = IntStream.range(0, vehicleCount)
                .mapToObj(i -> newVehicle(i, candidateRoads, random))
                .toList();
        log.info("Simulated vehicle feed: {} vehicles across {} candidate roads", vehicles.size(),
                candidateRoads.size());
    }

    private SimulatedVehicle newVehicle(int index, List<Road> candidateRoads, Random random) {
        Road road = candidateRoads.get(random.nextInt(candidateRoads.size()));
        boolean isBus = index % 2 == 0;
        String id = (isBus ? "sim-bus-" : "sim-auto-") + (index + 1);
        String label = (isBus ? "Bus " : "Auto ") + (index + 1);
        // Roughly city bus / auto-rickshaw speeds in traffic (~15-40 km/h).
        double speedMps = isBus ? 6 + random.nextDouble() * 4 : 4 + random.nextDouble() * 3;
        double startFraction = random.nextDouble();
        return new SimulatedVehicle(id, label, road.getGeom().getCoordinates(), speedMps, startFraction);
    }

    @Scheduled(
            initialDelayString = "${gtfs.realtime.poll-initial-delay-ms:0}",
            fixedDelayString = "${gtfs.realtime.poll-interval-ms:20000}")
    void tick() {
        if (vehicles.isEmpty()) {
            return;
        }
        Map<String, Vehicle> latest = new HashMap<>();
        String now = Instant.now().toString();
        for (SimulatedVehicle v : vehicles) {
            v.advance(tickIntervalSeconds);
            double[] latLon = v.currentLatLon();
            latest.put(v.id, new Vehicle(v.id, v.label, latLon[0], latLon[1], now));
        }
        vehicleStore.replaceAll(latest);
        log.debug("Simulated {} vehicle positions", latest.size());
    }

    /**
     * Mutable per-vehicle simulation state. Position is parametrized as a single arc-length
     * scalar that increases every tick; folding it through a triangle wave (period = 2x the
     * road's length) makes the vehicle bounce back and forth along the road indefinitely without
     * ever leaving its real geometry — simpler than tracking a segment index + direction flag
     * and reasoning about edge cases at the endpoints.
     */
    private static final class SimulatedVehicle {
        final String id;
        final String label;
        final Coordinate[] points;
        final double[] cumulativeLength;
        final double totalLength;
        final double speedMps;
        double rawDistance;

        SimulatedVehicle(String id, String label, Coordinate[] points, double speedMps, double startFraction) {
            this.id = id;
            this.label = label;
            this.points = points;
            this.cumulativeLength = new double[points.length];
            double acc = 0;
            for (int i = 1; i < points.length; i++) {
                acc += haversineMeters(points[i - 1].y, points[i - 1].x, points[i].y, points[i].x);
                cumulativeLength[i] = acc;
            }
            this.totalLength = acc;
            this.speedMps = speedMps;
            this.rawDistance = totalLength * startFraction;
        }

        void advance(double elapsedSeconds) {
            rawDistance += speedMps * elapsedSeconds;
        }

        double[] currentLatLon() {
            if (totalLength <= 0) {
                return new double[] {points[0].y, points[0].x};
            }
            double cycle = 2 * totalLength;
            double m = rawDistance % cycle;
            double pos = m <= totalLength ? m : cycle - m;

            int seg = 0;
            while (seg < cumulativeLength.length - 2 && cumulativeLength[seg + 1] < pos) {
                seg++;
            }
            double segStart = cumulativeLength[seg];
            double segLen = cumulativeLength[seg + 1] - segStart;
            double t = segLen <= 0 ? 0 : (pos - segStart) / segLen;
            double lat = points[seg].y + t * (points[seg + 1].y - points[seg].y);
            double lon = points[seg].x + t * (points[seg + 1].x - points[seg].x);
            return new double[] {lat, lon};
        }
    }
}
