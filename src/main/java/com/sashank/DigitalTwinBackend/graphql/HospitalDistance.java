package com.sashank.DigitalTwinBackend.graphql;

public record HospitalDistance(
        Long id,
        String name,
        String type,
        String address,
        GeoPoint location,
        double distanceKm) {
}