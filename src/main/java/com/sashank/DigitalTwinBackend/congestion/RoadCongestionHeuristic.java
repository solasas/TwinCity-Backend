package com.sashank.DigitalTwinBackend.congestion;

import com.sashank.DigitalTwinBackend.gtfs.Vehicle;
import com.sashank.DigitalTwinBackend.model.Road;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Component;

/**
 * Rule-based congestion estimate: buckets a road by how many currently-tracked vehicles
 * (Phase 2.1/2.2) are within {@link #NEARBY_RADIUS_METERS} of it. Fixed thresholds on a count —
 * there is no trained model, no learned weights, no AI/ML involved. Treat the result as a rough,
 * fully-explainable signal ("N vehicles are near this road"), not a traffic-engineering estimate.
 *
 * <p>Two deliberate simplifications, both fine for a "simple heuristic" but worth knowing about:
 * <ul>
 *   <li>Distance is measured to the road's geometry vertices, not true point-to-segment distance,
 *       so a vehicle sitting mid-way along an unusually long, sparse segment could be missed.
 *   <li>As of Phase 2.6 the vehicle feed is still the MBTA placeholder from Phase 2.1 (Boston-area
 *       buses), ~300km from these Hoboken roads — in practice this will report LOW for nearly
 *       every road until {@code GTFS_VEHICLE_POSITIONS_URL} is pointed at a Hoboken/NJ-area feed.
 * </ul>
 */
@Component
public class RoadCongestionHeuristic {

    private static final double EARTH_RADIUS_METERS = 6_371_000;
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

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
