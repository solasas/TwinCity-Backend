package com.sashank.DigitalTwinBackend.congestion;

import static com.sashank.DigitalTwinBackend.geo.GeoDistance.haversineMeters;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import com.sashank.DigitalTwinBackend.model.Road;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Component;

/**
 * Rule-based congestion estimate: buckets a road by how many currently-tracked vehicles
 * are within {@link #NEARBY_RADIUS_METERS} of it. Fixed thresholds on a count — there is no
 * trained model, no learned weights, no AI/ML involved. Treat the result as a rough,
 * fully-explainable signal ("N vehicles are near this road"), not a traffic-engineering estimate.
 *
 * <p>Distance is measured to the road's geometry vertices, not true point-to-segment distance,
 * so a vehicle sitting mid-way along an unusually long, sparse segment could be missed — a
 * deliberate simplification, fine for a "simple heuristic."
 *
 * <p>The vehicle feed itself is a simulation (Rajahmundry has no public GTFS-realtime source —
 * see {@code SimulatedVehiclePoller}), so unlike the original Hoboken/MBTA mismatch this actually
 * produces geographically-relevant MEDIUM/HIGH readings, since simulated vehicles move along the
 * same real road network these congestion checks run against.
 */
@Component
public class RoadCongestionHeuristic {

    private static final double NEARBY_RADIUS_METERS = 150;
    private static final int MEDIUM_VEHICLE_THRESHOLD = 1;
    private static final int HIGH_VEHICLE_THRESHOLD = 3;

    public CongestionLevel estimate(Road road, List<Vehicle> vehicles) {
        Coordinate[] roadPoints = road.getGeom().getCoordinates();
        long nearbyCount = vehicles.stream().filter(vehicle -> isNearRoad(vehicle, roadPoints)).count();

        if (nearbyCount >= HIGH_VEHICLE_THRESHOLD) {
            return CongestionLevel.HIGH;
        }
        if (nearbyCount >= MEDIUM_VEHICLE_THRESHOLD) {
            return CongestionLevel.MEDIUM;
        }
        return CongestionLevel.LOW;
    }

    private boolean isNearRoad(Vehicle vehicle, Coordinate[] roadPoints) {
        for (Coordinate point : roadPoints) {
            if (haversineMeters(vehicle.lat(), vehicle.lng(), point.y, point.x) <= NEARBY_RADIUS_METERS) {
                return true;
            }
        }
        return false;
    }
}
