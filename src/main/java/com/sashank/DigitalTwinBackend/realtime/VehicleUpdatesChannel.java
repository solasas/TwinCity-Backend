package com.sashank.DigitalTwinBackend.realtime;

/** Redis pub/sub channel that {@code VehicleStore} publishes changed positions to. */
public final class VehicleUpdatesChannel {

    public static final String NAME = "vehicle-updates";

    private VehicleUpdatesChannel() {
    }
}