package com.sashank.DigitalTwinBackend.gtfs;

/**
 * A live vehicle position, held in memory (see {@link VehicleStore}).
 * {@code lastUpdated} is an ISO-8601 timestamp string, matching the GraphQL schema's
 * {@code Vehicle.lastUpdated: String!} field.
 */
public record Vehicle(String id, String route, double lat, double lng, String lastUpdated) {
}