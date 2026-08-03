package com.sashank.DigitalTwinBackend.repository;

/** Native-query projection: a hospital plus its distance from some reference point. */
public interface HospitalDistanceRow {
    Long getId();

    String getName();

    String getType();

    String getAddress();

    Double getLat();

    Double getLon();

    Double getDistanceKm();
}
